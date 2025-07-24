package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.FaceTemplateVersion

data class IdentificationResult(
    val faceTemplateVersion: FaceTemplateVersion<*>,
    val identifications: LinkedHashMap<String, Float>
)
