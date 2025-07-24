package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.FaceTemplate
import com.appliedrec.verid3.common.FaceTemplateVersion

data class TaggedFaceTemplate<V: FaceTemplateVersion<D>, D>(
    val faceTemplate: FaceTemplate<V, D>,
    val identifier: String
)
