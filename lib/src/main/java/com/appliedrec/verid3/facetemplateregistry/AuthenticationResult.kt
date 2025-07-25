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
 */
data class AuthenticationResult<V: FaceTemplateVersion<D>, D>(
    val authenticated: Boolean,
    val challengeFaceTemplate: FaceTemplate<V, D>,
    val matchedFaceTemplate: FaceTemplate<V, D>,
    val score: Float
)
