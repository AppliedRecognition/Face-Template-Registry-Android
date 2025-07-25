package com.appliedrec.verid3.facetemplateregistry

import android.graphics.PointF
import android.graphics.RectF
import com.appliedrec.verid3.common.EulerAngle
import com.appliedrec.verid3.common.Face
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