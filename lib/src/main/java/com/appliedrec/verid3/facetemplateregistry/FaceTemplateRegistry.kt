package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.Face
import com.appliedrec.verid3.common.FaceRecognition
import com.appliedrec.verid3.common.FaceTemplate
import com.appliedrec.verid3.common.FaceTemplateVersion
import com.appliedrec.verid3.common.IImage
import com.appliedrec.verid3.common.SuspendingCloseable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Face template registry that keeps supplied faces in memory.
 *
 * The registry provides functions to register, identify and authenticate a face.
 *
 * @param V Face template version
 * @param D Face template data type
 * @property faceRecognition Face recognition that produces face templates with the supplied version
 * @property configuration Registry configuration
 * @constructor
 * @param faceTemplates List of initial face templates
 */
class FaceTemplateRegistry<V: FaceTemplateVersion<D>, D>(
    val faceRecognition: FaceRecognition<V, D>,
    faceTemplates: List<TaggedFaceTemplate<V, D>>,
    val configuration: Configuration = Configuration()
) : SuspendingCloseable {

    /**
     * Registry configuration
     *
     * @property authenticationThreshold Threshold for face authentication
     * @property identificationThreshold Threshold for face identification
     * @property autoEnrolmentThreshold Threshold for face auto-enrolment
     */
    data class Configuration(
        val authenticationThreshold: Float = 0.5f,
        val identificationThreshold: Float = 0.5f,
        val autoEnrolmentThreshold: Float = 0.6f
    )

    private val faceTemplateList = faceTemplates.toMutableList()
    private val job = SupervisorJob()
    private val coroutineContext = job + Dispatchers.Default
    private val lock = Mutex()
    private val isClosed = AtomicBoolean(false)

    /**
     * Get all registered face templates
     *
     * @return Face template list
     */
    suspend fun getFaceTemplates(): List<TaggedFaceTemplate<V, D>> {
        ensureNotClosed()
        return lock.withLock {
            faceTemplateList.toList()
        }
    }

    /**
     * Get all registered face identifiers
     *
     * @return Set of identifiers
     */
    suspend fun getIdentifiers(): Set<String> {
        ensureNotClosed()
        return lock.withLock {
            faceTemplateList.map { it.identifier }.toSet()
        }
    }

    /**
     * Register a face template from face and image
     *
     * @param face Face to register a template for
     * @param image Image in which the face was detected
     * @param identifier Identifier for the user to whom the face belongs
     * @param forceEnrolment If `true`, the face will be enrolled even if another identifier with a
     * similar face already exists.
     * @return Registered face template
     */
    suspend fun registerFace(
        face: Face,
        image: IImage,
        identifier: String,
        forceEnrolment: Boolean = false
    ): FaceTemplate<V, D> = withContext(coroutineContext) {
        ensureNotClosed()
        val template = faceRecognition.createFaceRecognitionTemplates(
            listOf(face), image).first()
        val taggedTemplate = TaggedFaceTemplate(template, identifier)
        if (!forceEnrolment) {
            val allTemplates = getFaceTemplates()
            if (allTemplates.isNotEmpty()) {
                val scores = faceRecognition.compareFaceRecognitionTemplates(
                    allTemplates.map { it.faceTemplate },
                    template
                )
                val existingUser = scores
                    .zip(allTemplates)
                    .firstOrNull { (score, faceTemplate) ->
                        score >= configuration.authenticationThreshold
                                && faceTemplate.identifier != identifier
                    }
                    ?.second?.identifier
                if (existingUser != null) {
                    throw FaceTemplateRegistryException.SimilarFaceAlreadyRegistered(existingUser)
                }
            }
        }
        lock.withLock {
            faceTemplateList.add(taggedTemplate)
        }
        taggedTemplate.faceTemplate
    }

    /**
     * Identify a face and image
     *
     * @param face Face to identify
     * @param image Image in which the face was detected
     * @return List of [identification results][IdentificationResult] ordered by best match first
     *
     * Note that the list will only contain one result per identifier. So if more than one face
     * template of one identifier match only the best matching face template will be returned.
     */
    suspend fun identifyFace(
        face: Face,
        image: IImage
    ): List<IdentificationResult<V, D>> = withContext(coroutineContext) {
        ensureNotClosed()
        val faceTemplates = getFaceTemplates()
        val template = faceRecognition.createFaceRecognitionTemplates(listOf(face), image)
            .first()
        if (faceTemplates.isEmpty()) {
            return@withContext emptyList()
        }
        val scores = faceRecognition.compareFaceRecognitionTemplates(
            faceTemplates.map { it.faceTemplate },
            template
        )
        scores.zip(faceTemplates)
            .mapNotNull { (score, faceTemplate) ->
                if (score >= configuration.identificationThreshold) {
                    IdentificationResult(faceTemplate, score)
                } else {
                    null
                }
            }
            .groupBy { it.taggedFaceTemplate.identifier }
            .map { (identifier, results) -> results.maxBy { it.score } }
            .sortedByDescending { it.score }
    }

    /**
     * Authenticate face and image against a specific identifier
     *
     * The function will throw an [FaceTemplateRegistryException.IdentifierNotRegistered] if no face templates are registered for
     * the given identifier.
     *
     * @param face Face to authenticate
     * @param image Image in which the face was detected
     * @param identifier Identifier to authenticate against
     * @return [Authentication result][AuthenticationResult] that contains the comparison score
     * and the face templates used for the comparison.
     */
    suspend fun authenticateFace(
        face: Face,
        image: IImage,
        identifier: String
    ): AuthenticationResult<V, D> = withContext(coroutineContext) {
        ensureNotClosed()
        val templates = getFaceTemplatesByIdentifier(identifier)
        if (templates.isEmpty()) {
            throw FaceTemplateRegistryException.IdentifierNotRegistered(identifier)
        }
        val template = faceRecognition.createFaceRecognitionTemplates(listOf(face), image)
            .first()
        val scores = faceRecognition.compareFaceRecognitionTemplates(
            templates,
            template
        )
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: -1
        val maxScore = scores[maxIndex]
        val matchedTemplate = templates[maxIndex]
        AuthenticationResult(
            maxScore >= configuration.authenticationThreshold,
            template,
            matchedTemplate,
            maxScore
        )
    }

    /**
     * Get face templates tagged as the given identifier
     *
     * @param identifier Identifier for which to get face templates
     * @return List of face templates tagged as the given identifier
     */
    suspend fun getFaceTemplatesByIdentifier(identifier: String): List<FaceTemplate<V, D>> {
        ensureNotClosed()
        return getFaceTemplates().mapNotNull {
            if (it.identifier == identifier) it.faceTemplate else null
        }
    }

    @Suppress("UNCHECKED_CAST")
    val typeErased: FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>
        get() = this as FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>

    /**
     * Close the registry. The registry can no longer be used after calling this function and calls to
     * the registry methods will throw an [IllegalStateException]
     */
    override suspend fun close() {
        if (isClosed.compareAndSet(false, true)) {
            lock.withLock {
                faceTemplateList.clear()
            }
            job.cancel()
        }
    }

    private fun ensureNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("FaceTemplateRegistry is closed")
        }
    }
}