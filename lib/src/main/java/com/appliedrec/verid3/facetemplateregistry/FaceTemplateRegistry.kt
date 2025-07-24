package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.Face
import com.appliedrec.verid3.common.FaceRecognition
import com.appliedrec.verid3.common.FaceTemplate
import com.appliedrec.verid3.common.FaceTemplateVersion
import com.appliedrec.verid3.common.IImage
import com.appliedrec.verid3.common.SuspendingCloseable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

@Suppress("UNCHECKED_CAST")
class FaceTemplateRegistry(
    faceRecognition: Array<FaceRecognition<*, *>>,
    faceTemplates: Array<TaggedFaceTemplate<*, *>>
) : SuspendingCloseable {

    interface Delegate {
        suspend fun onFaceTemplatesAdded(faceTemplates: Array<TaggedFaceTemplate<*, *>>)
        fun authenticationThreshold(faceTemplateVersion: FaceTemplateVersion<*>): Float = 0.5f
        fun identificationThreshold(faceTemplateVersion: FaceTemplateVersion<*>): Float = 0.5f
        fun authEnrolmentThreshold(faceTemplateVersion: FaceTemplateVersion<*>): Float = 0.5f
        val useSingleFaceTemplateVersionForIdentification: Boolean
            get() = true
        val autoEnrolFaceTemplates: Boolean
            get() = true
    }

    private val faceTemplateList = faceTemplates.toMutableList()
    private val job = SupervisorJob()
    private val coroutineContext = job + Dispatchers.Default

    val faceTemplates: Array<TaggedFaceTemplate<*, *>>
        get() = faceTemplateList.toTypedArray()
    var delegate: Delegate? = null
    val faceTemplateVersions: Array<FaceTemplateVersion<*>>
        get() = faceRecognition.keys.toTypedArray()
    val identifiers: Set<String>
        get() = faceTemplateList.map { it.identifier }.toSet()
    val faceRecognition: LinkedHashMap<FaceTemplateVersion<*>, FaceRecognition<*, *>>

    init {
        this.faceRecognition = faceRecognition
            .associateByTo(LinkedHashMap()) { it.version }
    }

    suspend fun registerFace(
        face: Face,
        image: IImage,
        identifier: String
    ): Unit = withContext(coroutineContext) {
        val templatesToAdd = faceRecognition.values.mapNotNull { rec ->
            val template = rec.createFaceRecognitionTemplates(
                arrayOf(face), image).firstOrNull()
            if (template == null) {
                null
            } else {
                TaggedFaceTemplate(template, identifier)
            }
        }
        if (templatesToAdd.isNotEmpty()) {
            faceTemplateList.addAll(templatesToAdd)
            try {
                delegate?.onFaceTemplatesAdded(templatesToAdd.toTypedArray())
            } catch (e: Exception) {
                faceTemplateList.removeAll(templatesToAdd)
            }
        }
    }

    suspend fun identifyFace(
        face: Face,
        image: IImage
    ): IdentificationResult = withContext(coroutineContext) {
        val allIds = identifiers
        val useSingleVersion = delegate?.useSingleFaceTemplateVersionForIdentification ?: true

        suspend fun identify(version: FaceTemplateVersion<*>): LinkedHashMap<String, Float> {
            val rec = faceRecognition[version] as FaceRecognition<FaceTemplateVersion<Any>, Any>
            val template = rec.createFaceRecognitionTemplates(arrayOf(face), image).first()
            val taggedTemplates: Array<TaggedFaceTemplate<FaceTemplateVersion<Any>, Any>> =
                getTemplatesByVersion(version) as Array<TaggedFaceTemplate<FaceTemplateVersion<Any>, Any>>
            val templates = taggedTemplates.map { it.faceTemplate }.toTypedArray()
            val scores = rec.compareFaceRecognitionTemplates(templates, template)
            val threshold = delegate?.identificationThreshold(version) ?: 0.5f
            val results = scores.toList()
                .mapIndexedNotNull { index, score ->
                    if (score >= threshold) {
                        taggedTemplates[index].identifier to score
                    } else {
                        null
                    }
                }
                .groupBy { it.first }
                .mapValues { (_, values) -> values.maxOf { it.second } }
                .toList()
                .sortedByDescending { it.second }
                .toMap(LinkedHashMap())
            if (delegate?.autoEnrolFaceTemplates ?: true) {
                val threshold = delegate?.authEnrolmentThreshold(version) ?: 0.5f
                if ((results.values.firstOrNull() ?: 0f) >= threshold) {
                    autoEnrolFace(face, image, results.keys.first())
                }
            }
            return results
        }

        if (useSingleVersion) {
            for (version in faceTemplateVersions) {
                val versionIds = getIdentifiersByVersion(version)
                if (versionIds.containsAll(allIds)) {
                    val result = identify(version)
                    return@withContext IdentificationResult(version, result)
                }
            }
            throw IllegalArgumentException("No face template version eligible for comparison")
        } else {
            for (version in faceTemplateVersions) {
                val result = identify(version)
                if (result.isNotEmpty()) {
                    return@withContext IdentificationResult(
                        version,
                        result
                    )
                }
            }
            return@withContext IdentificationResult(
                faceRecognition.keys.first(),
                LinkedHashMap()
            )
        }
    }

    suspend fun authenticateFace(
        face: Face,
        image: IImage,
        identifier: String
    ): AuthenticationResult = withContext(coroutineContext) {
        val version = getVersionsForIdentifier(identifier).firstOrNull()
            ?: throw IllegalArgumentException("$identifier not registered")
        val recognition = faceRecognition[version] as FaceRecognition<FaceTemplateVersion<Any>, Any>
        val template = recognition
            .createFaceRecognitionTemplates(arrayOf(face), image).first()
        val templates = getTemplatesForIdentifier(identifier, version)
            .map { it.faceTemplate }
            .toTypedArray() as Array<FaceTemplate<FaceTemplateVersion<Any>, Any>>
        val scores = recognition
            .compareFaceRecognitionTemplates(templates, template)
        val maxScore = scores.max()
        val threshold = delegate?.authenticationThreshold(version) ?: 0.5f
        val authenticated = maxScore >= threshold
        if (maxScore >= (delegate?.authEnrolmentThreshold(version) ?: 0.5f)
            && delegate?.autoEnrolFaceTemplates ?: true) {
            autoEnrolFace(face, image, identifier)
        }
        AuthenticationResult(authenticated, maxScore, version)
    }

    override suspend fun close() {
        faceRecognition.values.forEach { it.close() }
        faceTemplateList.clear()
        job.cancel()
    }

    fun getTemplatesByVersion(faceTemplateVersion: FaceTemplateVersion<*>): Array<TaggedFaceTemplate<*, *>> {
        return faceTemplateList.filter {
            it.faceTemplate.version == faceTemplateVersion
        }.toTypedArray()
    }

    fun getIdentifiersByVersion(faceTemplateVersion: FaceTemplateVersion<*>): Set<String> {
        return faceTemplateList.mapNotNull {
            if (it.faceTemplate.version == faceTemplateVersion) {
                it.identifier
            } else {
                null
            }
        }.toSet()
    }

    fun getVersionsForIdentifier(identifier: String): Array<FaceTemplateVersion<*>> {
        return faceTemplateVersions.filter { version ->
            faceTemplateList.firstOrNull { it.identifier == identifier && it.faceTemplate.version == version } != null
        }.toTypedArray()
    }

    fun getTemplatesForIdentifier(identifier: String, faceTemplateVersion: FaceTemplateVersion<*>): Array<TaggedFaceTemplate<*, *>> {
        return faceTemplateList.filter {
            it.identifier == identifier && it.faceTemplate.version == faceTemplateVersion
        }.toTypedArray()
    }

    private suspend fun autoEnrolFace(face: Face, image: IImage, identifier: String) {
        val idVersions = getVersionsForIdentifier(identifier).toSet()
        val versionsToEnrol = faceTemplateVersions.filter { version ->
            !idVersions.contains(version)
        }
        val faceTemplates = versionsToEnrol.map { version ->
            val template = faceRecognition[version]!!.createFaceRecognitionTemplates(arrayOf(face), image).first()
            TaggedFaceTemplate(template, identifier)
        }
        if (faceTemplates.isNotEmpty()) {
            faceTemplateList.addAll(faceTemplates)
            try {
                delegate?.onFaceTemplatesAdded(faceTemplates.toTypedArray())
            } catch (_: Exception) {
                faceTemplateList.removeAll(faceTemplates)
            }
        }
    }
}