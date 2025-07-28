package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.FaceTemplate
import com.appliedrec.verid3.common.FaceTemplateVersion

/**
 * Authentication result
 *
 * @param V Face template version
 * @param D Face template data type
 * @property authenticated `true` if the authentication succeeded
 * @property challengeFaceTemplate Face template used for the authentication
 * @property matchedFaceTemplate Face template with the best matching score
 * @property score Comparison score between the challenge and matched face templates
 * @property autoEnrolledFaceTemplates List of face templates that were auto-enrolled
 */
data class AuthenticationResult<V: FaceTemplateVersion<D>, D>(
    val authenticated: Boolean,
    val challengeFaceTemplate: FaceTemplate<V, D>,
    val matchedFaceTemplate: FaceTemplate<V, D>,
    val score: Float,
    val autoEnrolledFaceTemplates: List<FaceTemplate<*, *>> = emptyList()
)

internal data class MutableAuthenticationResult<V: FaceTemplateVersion<D>, D>(
    var authenticated: Boolean,
    var challengeFaceTemplate: FaceTemplate<V, D>,
    var matchedFaceTemplate: FaceTemplate<V, D>,
    var score: Float,
    var autoEnrolledFaceTemplates: MutableList<FaceTemplate<*, *>> = mutableListOf()
) {
    constructor(result: AuthenticationResult<V, D>) : this(
        result.authenticated,
        result.challengeFaceTemplate,
        result.matchedFaceTemplate,
        result.score,
        result.autoEnrolledFaceTemplates.toMutableList()
    )

    fun toAuthenticationResult(): AuthenticationResult<V, D> {
        return AuthenticationResult(
            authenticated,
            challengeFaceTemplate,
            matchedFaceTemplate,
            score,
            autoEnrolledFaceTemplates.toList()
        )
    }
}