package com.appliedrec.verid3.facetemplateregistry

import android.graphics.PointF
import android.graphics.RectF
import com.appliedrec.verid3.common.EulerAngle
import com.appliedrec.verid3.common.Face
import com.appliedrec.verid3.common.FaceTemplateVersion
import com.appliedrec.verid3.common.IImage
import com.appliedrec.verid3.common.ImageFormat


fun createFakeFace(templateValue: Float): Face {
    return Face(RectF(templateValue, 0f, 1f, 1f), EulerAngle<Float>(0f, 0f, 0f), 10f, emptyArray(),
        PointF(0f, 0f), PointF(0f, 0f), PointF(0f, 0f), PointF(0f, 0f))
}

fun createFakeImage(): IImage {
    return object : IImage {
        override val data: ByteArray = ByteArray(100)
        override val width = 100
        override val height = 100
        override val bytesPerRow: Int = 400
        override val format: ImageFormat = ImageFormat.ARGB
    }
}

inline fun <reified V: MockFaceTemplateVersion> generateUsers(
    version: V,
    count: Int,
    templatesPerUserCount: Int,
    startingUserId: Int=0
): List<TaggedFaceTemplate<V, Float>> {
    return (startingUserId..<(startingUserId+count)).map { i ->
        val identifier = "User $i"
        (0..<templatesPerUserCount).map { j ->
            val data = i.toFloat() + j.toFloat() / 10000f
            TaggedFaceTemplate(
                MockFaceTemplate(version, data),
                identifier
            )
        }
    }.flatMap { it }
}

inline fun <reified V: MockFaceTemplateVersion> createRegistry(
    version: V,
    userCount: Int,
    templatesPerUserCount: Int,
    startingUserId: Int=0
): FaceTemplateRegistry<V, Float> {
    val templates = generateUsers(version, userCount, templatesPerUserCount, startingUserId)
    val recognition = MockFaceRecognition(version)
    return FaceTemplateRegistry(recognition, templates)
}