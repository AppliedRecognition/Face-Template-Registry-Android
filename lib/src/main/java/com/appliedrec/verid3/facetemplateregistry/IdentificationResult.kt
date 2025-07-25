package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.FaceTemplateVersion

/**
 * Identification result
 *
 * @param V Face template version
 * @param D Face template data type
 * @property taggedFaceTemplate Face template that was identified
 * @property score Comparison score between the face template and the challenge face template
 */
data class IdentificationResult<V: FaceTemplateVersion<D>, D>(
    val taggedFaceTemplate: TaggedFaceTemplate<V, D>,
    val score: Float
)
