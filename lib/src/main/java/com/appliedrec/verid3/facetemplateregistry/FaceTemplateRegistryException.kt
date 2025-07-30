package com.appliedrec.verid3.facetemplateregistry

sealed class FaceTemplateRegistryException(message: String) : Exception(message) {
    data class SimilarFaceAlreadyRegistered(val registeredIdentifier: String) : FaceTemplateRegistryException("Similar face already registered as $registeredIdentifier")
    data class IdentifierNotRegistered(val identifier: String) : FaceTemplateRegistryException("Identifier not registered: $identifier")
    object IncompatibleFaceTemplates : FaceTemplateRegistryException("Incompatible face templates") {
        private fun readResolve(): Any = IncompatibleFaceTemplates
    }
}