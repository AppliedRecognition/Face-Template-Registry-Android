package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.FaceTemplateVersion

data class AuthenticationResult(
    val authenticated: Boolean,
    val score: Float,
    val faceTemplateVersion: FaceTemplateVersion<*>
)
