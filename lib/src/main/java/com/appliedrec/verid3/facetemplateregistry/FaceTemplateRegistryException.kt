package com.appliedrec.verid3.facetemplateregistry

import com.appliedrec.verid3.common.FaceTemplate

sealed class FaceTemplateRegistryException(message: String) : Exception(message) {
    data class SimilarFaceAlreadyRegistered(
        val registeredIdentifier: String,
        val faceTemplate: FaceTemplate<*, *>,
        val score: Float
    ) : FaceTemplateRegistryException("Similar face already registered as $registeredIdentifier")
    data class IdentifierNotRegistered(
        val identifier: String
    ) : FaceTemplateRegistryException("Identifier not registered: $identifier")
    object IncompatibleFaceTemplates : FaceTemplateRegistryException("Incompatible face templates") {
        private fun readResolve(): Any = IncompatibleFaceTemplates
    }
    data class FaceDoesNotMatchExisting(
        val faceTemplate: FaceTemplate<*, *>,
        val maxScore: Float
    ) : FaceTemplateRegistryException("Face does not match registered face templates(s)")
}