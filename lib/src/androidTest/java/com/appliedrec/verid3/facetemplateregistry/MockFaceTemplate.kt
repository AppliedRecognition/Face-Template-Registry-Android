package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.FaceTemplate

class MockFaceTemplate<V: MockFaceTemplateVersion>(version: V, data: Float)
    : FaceTemplate<V, Float>(version, data) {

    override fun equals(other: Any?): Boolean {
        return other is MockFaceTemplate<V> && other.data == data && other.version == version
    }

    override fun hashCode(): Int {
        return 31 * version.hashCode() + data.hashCode()
    }
}