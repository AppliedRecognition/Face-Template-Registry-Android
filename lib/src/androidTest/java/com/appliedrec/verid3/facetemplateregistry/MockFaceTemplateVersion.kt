package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.FaceTemplateVersion

//class MockFaceTemplateVersion(override val id: Int) : FaceTemplateVersion<Float>

sealed class MockFaceTemplateVersion(override val id: Int) : FaceTemplateVersion<Float> {
    object Version1 : MockFaceTemplateVersion(1)
    object Version2 : MockFaceTemplateVersion(2)
}