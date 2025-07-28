package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.FaceTemplate
import com.appliedrec.verid3.common.FaceTemplateVersion

/**
 * Tagged face template
 *
 * @param V Face template version
 * @param D Face template data type
 * @property faceTemplate Face template
 * @property identifier Identifier for the user to whom the face template belongs
 */
data class TaggedFaceTemplate<V: FaceTemplateVersion<D>, D>(
    val faceTemplate: FaceTemplate<V, D>,
    val identifier: String
)
