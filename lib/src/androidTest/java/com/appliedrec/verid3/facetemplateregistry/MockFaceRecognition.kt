package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.Face
import com.appliedrec.verid3.common.FaceRecognition
import com.appliedrec.verid3.common.FaceTemplate
import com.appliedrec.verid3.common.IImage
import kotlin.math.abs

class MockFaceRecognition<V: MockFaceTemplateVersion>(override val version: V) : FaceRecognition<V, Float> {

    override suspend fun createFaceRecognitionTemplates(
        faces: List<Face>,
        image: IImage
    ): List<FaceTemplate<V, Float>> {
        return faces.map { face ->
            MockFaceTemplate(version, face.bounds.left) as FaceTemplate<V, Float>
        }
    }

    override suspend fun compareFaceRecognitionTemplates(
        faceRecognitionTemplates: List<FaceTemplate<V, Float>>,
        template: FaceTemplate<V, Float>
    ): FloatArray {
        val challengeData = template.data
        val templateData = faceRecognitionTemplates.map { it.data }
        return templateData.map { data ->
            val diff = abs(data - challengeData)
            if (diff > 1.0f) {
                0f
            } else {
                1.0f - diff
            }
        }.toFloatArray()
    }
}