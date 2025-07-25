package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.Face
import com.appliedrec.verid3.common.FaceTemplate
import com.appliedrec.verid3.common.FaceTemplateVersion
import com.appliedrec.verid3.common.IImage
import com.appliedrec.verid3.common.SuspendingCloseable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles multiple face template registries.
 *
 * @constructor
 * Constructor
 *
 * @param registry Registry to handle
 * @param otherRegistries Other registries to handle
 * @param checkForIncompatibleFaceTemplates Set to `false` to accept registries with faces that
 * cannot be effectively compared. For example, if one registry has users [user1, user2] and one
 * of the other registries has users [user1, user3] then there is no way to compare all users
 * together because comparisons crossing registry boundaries are not possible.
 */
class FaceTemplateMultiRegistry(
    registry: FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>,
    vararg otherRegistries: FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>,
    checkForIncompatibleFaceTemplates: Boolean = true
) : SuspendingCloseable {
    interface Delegate {
        fun onFaceTemplatesAdded(faceTemplates: List<TaggedFaceTemplate<*, *>>)
    }

    /**
     * Set this delegate to receive callbacks when face templates are added either by registration
     * or by auto enrolment.
     */
    var delegate: Delegate? = null

    /**
     * List of registries handled by the multi registry.
     */
    val registries: List<FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>>

    private val job = SupervisorJob()
    private val coroutineContext = job + Dispatchers.Default
    private val isClosed = AtomicBoolean(false)

    init {
        registries = listOf(registry) + otherRegistries
        if (checkForIncompatibleFaceTemplates) {
            runBlocking(coroutineContext) { checkForIncompatibleFaceTemplates() }
        }
    }

    /**
     * Checks that there is a common face template version among all registries.
     *
     * This check will run on initialization if the constructor parameter
     * `checkForIncompatibleFaceTemplates` is left at its default value of `true`.
     */
    suspend fun checkForIncompatibleFaceTemplates() = withContext(coroutineContext) {
        coroutineScope {
            val deferredIdentifiers = registries.map { registry ->
                async {
                    registry.getIdentifiers()
                }
            }
            val identifiers = deferredIdentifiers.awaitAll()
            val allIdentifiers = identifiers.flatten().toSet()
            val hasCommonTemplate = identifiers.any { ids ->
                ids.containsAll(allIdentifiers)
            }
            if (!hasCommonTemplate) {
                throw IllegalStateException("No common face template version found")
            }
        }
    }

    /**
     * Register face in all registries handled by the multi registry
     *
     * @param face Face to register
     * @param image Image in which the face was detected
     * @param identifier Identifier for the user to whom the face belongs
     * @param forceEnrolment Set to `true` to force enrolment even if a similar face template is
     * already registered as another user.
     */
    suspend fun registerFace(
        face: Face,
        image: IImage,
        identifier: String,
        forceEnrolment: Boolean=false
    ) = withContext(coroutineContext) {
        ensureNotClosed()
        coroutineScope {
            val deferredFaceTemplates = registries.map { registry ->
                async {
                    registry.registerFace(face, image, identifier, forceEnrolment)
                }
            }
            val faceTemplates = deferredFaceTemplates.awaitAll().map {
                TaggedFaceTemplate(it, identifier)
            }
            delegate?.let { delegate ->
                CoroutineScope(Dispatchers.Default).launch {
                    delegate.onFaceTemplatesAdded(faceTemplates)
                }
            }
        }
    }

    /**
     * Identify face
     *
     * @param face Face to identify
     * @param image Image in which the face was detected
     * @param autoEnrol Auto enrol the face in registries where the user does not yet exist. This
     * facilitates migrating from one face template registry to another.
     * @param safe If set to `false` the function will iterate over registries and return the first
     * result with matching face templates. This risks missing evaluation of face templates that
     * may yield a higher score. Setting the parameter to `false` may help if there is no common
     * face template version or if it's desired to use a newer face recognition algorithm before
     * all faces are migrated to it.
     * @return List of [identification results][IdentificationResult] ordered by best match first
     */
    suspend fun identifyFace(
        face: Face,
        image: IImage,
        autoEnrol: Boolean=true,
        safe: Boolean=true
    ): List<IdentificationResult<*, *>> = withContext(coroutineContext) {
        ensureNotClosed()
        suspend fun autoEnrolFromResults(registry: FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>, results: List<IdentificationResult<*, *>>) {
            if (!autoEnrol) {
                return
            }
            results.firstNotNullOfOrNull {
                if (it.score >= registry.configuration.autoEnrolmentThreshold) {
                    it.taggedFaceTemplate.identifier
                } else {
                    null
                }
            }?.let { identifier ->
                autoEnrolFace(face, image, identifier)
            }
        }
        if (safe) {
            val users = registries.associate { it.faceRecognition.version to it.getIdentifiers() }
            val allUsers = users.flatMap { it.value }.toSet()
            for (registry in registries) {
                val versionUsers = users[registry.faceRecognition.version] ?: emptySet()
                if (versionUsers.containsAll(allUsers)) {
                    val results = registry.identifyFace(face, image)
                    autoEnrolFromResults(registry, results)
                    return@withContext results
                }
            }
            throw IllegalStateException("No common face template version found")
        } else {
            for (registry in registries) {
                val results = registry.identifyFace(face, image)
                if (results.isNotEmpty()) {
                    autoEnrolFromResults(registry, results)
                    return@withContext results
                }
            }
            return@withContext emptyList()
        }
    }

    /**
     * Authenticate a face
     *
     * The function will throw an [IllegalStateException] if no face templates are registered for
     * the given identifier in any of the registries.
     *
     * @param face Face to authenticate
     * @param image Image in which the face was detected
     * @param identifier Identifier to authenticate against
     * @param autoEnrol Keep as the default `true` to auto enrol the face in registries where the
     * user does not yet exist.
     * @return [Authentication result][AuthenticationResult] that contains the comparison score
     * and the face templates used for the comparison.
     */
    suspend fun authenticateFace(
        face: Face,
        image: IImage,
        identifier: String,
        autoEnrol: Boolean=true
    ): AuthenticationResult<*, *> = withContext(coroutineContext) {
        ensureNotClosed()
        var finalResult: AuthenticationResult<*, *>? = null
        var error: IllegalStateException? = null
        for (registry in registries) {
            try {
                val result = registry.authenticateFace(face, image, identifier)
                if (finalResult == null || result.score > finalResult.score) {
                    finalResult = result
                }
                if (result.authenticated) {
                    break
                }
            } catch (e: IllegalStateException) {
                error = e
            }
        }
        finalResult?.let { result ->
            if (result.authenticated && autoEnrol) {
                val threshold = registries.first {
                    it.faceRecognition.version == result.challengeFaceTemplate.version
                }.configuration.autoEnrolmentThreshold
                if (result.score >= threshold) {
                    autoEnrolFace(face, image, identifier)
                }
            }
            result
        } ?: error?.let { throw error } ?: throw Exception("Unknown error")
    }

    /**
     * Get face templates in all registries
     *
     * @return List of tagged face templates
     */
    suspend fun getFaceTemplates(): List<TaggedFaceTemplate<*, *>> = withContext(coroutineContext) {
        ensureNotClosed()
        coroutineScope {
            registries.map { registry ->
                async {
                    registry.getFaceTemplates()
                }
            }.awaitAll().flatten()
        }
    }

    /**
     * Get identifiers for face templates in all registries
     *
     * @return Set of identifiers
     */
    suspend fun getIdentifiers(): Set<String> = withContext(coroutineContext) {
        ensureNotClosed()
        coroutineScope {
            registries.map { registry ->
                async {
                    registry.getIdentifiers()
                }
            }.awaitAll().flatten().toSet()
        }
    }

    /**
     * Get face templates tagged as the given identifier
     *
     * @param identifier Identifier for which to get face templates
     * @return List of face templates tagged as the given identifier
     */
    suspend fun getFaceTemplatesByIdentifier(
        identifier: String
    ): List<FaceTemplate<*, *>> = withContext(coroutineContext) {
        ensureNotClosed()
        coroutineScope {
            registries.map { registry ->
                async {
                    registry.getFaceTemplatesByIdentifier(identifier)
                }
            }.awaitAll().flatten()
        }
    }

    /**
     * Delete face templates tagged as the given identifier
     *
     * @param identifier Identifier whose face templates will be deleted
     * @return List of deleted face templates
     */
    suspend fun deleteFaceTemplatesByIdentifier(
        identifier: String
    ): List<FaceTemplate<*, *>> = withContext(coroutineContext) {
        ensureNotClosed()
        coroutineScope {
            registries.map { registry ->
                async {
                    registry.deleteFaceTemplatesByIdentifier(identifier)
                }
            }.awaitAll().flatten()
        }
    }

    /**
     * Close the registry. The registry can no longer be used after calling this function and
     * calls to its methods will throw [IllegalStateException].
     */
    override suspend fun close() {
        if (isClosed.compareAndSet(false, true)) {
            job.cancel()
            registries.forEach { it.close() }
        }
    }

    private suspend fun autoEnrolFace(
        face: Face,
        image: IImage,
        identifier: String
    ) = withContext(coroutineContext) {
        coroutineScope {
            val templates = registries
                .map { registry ->
                    async {
                        if (!registry.getIdentifiers().contains(identifier)) {
                            registry.registerFace(face, image, identifier)
                        } else {
                            null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .map { TaggedFaceTemplate(it, identifier) }
            if (templates.isNotEmpty()) {
                delegate?.let { delegate ->
                    CoroutineScope(Dispatchers.Default).launch {
                        delegate.onFaceTemplatesAdded(templates)
                    }
                }
            }
        }
    }

    private fun ensureNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("FaceTemplateMultiRegistry is closed")
        }
    }
}