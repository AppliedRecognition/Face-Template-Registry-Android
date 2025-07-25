# Face template registry

The face template registry manages registration, authentication and identification of faces captured by the [Face capture]() library.

The registry doesn't persist its data across app restarts. It keeps face templates in memory. It's up to the consumer to supply the registry with face templates at initialization.

## Handling different face recognition systems

If you application contains face templates produced by different face recognition systems the face template registry will facilitate migration between the systems and ensure consistency of your face template resources.

The library has two face template registry classes:

1. [FaceTemplateRegistry](./lib/src/main/java/com/appliedrec/verid3/facetemplateregistry/FaceTemplateRegistry.kt) – handles face templates for a single face recognition system.
2. [FaceTemplateMultiRegistry](./lib/src/main/java/com/appliedrec/verid3/facetemplateregistry/FaceTemplateMultiRegistry.kt) – coordinates between multiple registries that use different face recognition systems.

Note that both classes implement the [SuspendingCloseable]() interface from the Ver-ID Common Types library. This means that you can use a syntax similar to try-with-resources but in a suspending context. The following statement will create a face template registry
instance and close it to release resources when it's no longer needed. After the registry is closed, calling its methods will throw an `IllegalStateException`.

```kotlin
FaceTemplateRegistry(faceRecognition, faceTemplates).use { registry ->
    // Call registry methods
}
// Now the registry is closed.
```

The above is equivalent to:

```kotlin
val registry = FaceTemplateRegistry(faceRecognition, faceTemplates)
// Call registry methods
registry.close()
// Now the registry ise closed.
```

## Usage

### Creating an registry instance

#### Single face recognition system

```kotlin
// Face recognition instance
val faceRecognition: FaceRecognitionArcFace
    
// Tagged face templates to populate the registry
val faceTemplates: List<TaggedFaceTemplate<FaceTemplateVersionV24, FloatArray>>

// Create registry instance
val registry = FaceTemplateRegistry(
    faceRecognition, 
    faceTemplates
)
```

#### Multiple face recognition systems

```kotlin
// First face recognition instance
val faceRecognition1: FaceRecognitionArcFace
    
// Tagged face templates to populate the first registry
val faceTemplates1: List<TaggedFaceTemplate<FaceTemplateVersionV24, FloatArray>>

// Create first registry instance
val registry1 = FaceTemplateRegistry(
    faceRecognition1, 
    faceTemplates1
)

// Second face recognition instance
val faceRecognition2: FaceRecognition3D
    
// Tagged face templates to populate the second registry
val faceTemplates2: List<TaggedFaceTemplate<FaceTemplateVersion3D1, FloatArray>>

// Create second registry instance
val registry2 = FaceTemplateRegistry(
    faceRecognition2, 
    faceTemplates2
)

// Create multi registry
val multiRegistry = FaceTemplateMultiRegistry(
    registry1 as FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>,
    registry2 as FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>
)
```

### Face template registration

Extract a face template from the given face and image and register it under a given identifier.

```kotlin
runBlocking {
    // Face recognition instance
    val faceRecognition: FaceRecognitionArcFace
    
    // Tagged face templates to populate the registry
    val faceTemplates: List<TaggedFaceTemplate<FaceTemplateVersionV24, FloatArray>>
    
    // Face to register
    val face: Face
    
    // Image in which the face was detected
    val image: IImage
    
    // Identifier with which to tag the face template
    val identifier: String = "User 1"
    
    try {
        // Create a registry instance
        val registeredFaceTemplates = FaceTemplateRegistry(
            faceRecognition, 
            faceTemplates
        ).use { registry ->
            
            // Register the face
            registry.registerFace(face, image, identifier)
        }
    } catch (e: Exception) {
        // Registration failed
    }
}
```

### Face authentication

Extract a face template from the given face and image and compare it to face templates registered under the given identifier.

```kotlin
runBlocking {
    try {
        val authenticationResult = registry.authenticateFace(
            face, 
            image, 
            identifier
        )
        if (authenticationResult.authenticated) {
            // The face has been authenticated as 
            // the user represented by the identifier
        }
    } catch (e: Exception) {
        // Authentication failed
    }
}
```

### Face identification

Extract a face template the given face and image and compare it agains all registered faces, returning a list of results similar to the face template.

```kotlin
runBlocking {
    try {
        val identificationResults = registry.identifyFace(
            face,
            image
        )
        identificationResults.firstOrNull()?.let { result ->
            val identifiedUser = result.taggedFaceTemplate.identifier
            // Face identified as identifiedUser
        }
    } catch (e: Exception) {
        // Identification failed
    }
}
```

### Using multi registry delegate

When using [FaceTemplateMultiRegistry](./lib/src/main/java/com/appliedrec/verid3/facetemplateregistry/FaceTemplateMultiRegistry.kt), you can optionally register a delegate to receive updates when faces are added either by registration or by auto enrolment. This allows you to propagate the updates to your face template source.

```kotlin
// Your multi registry instance
val mutliRegistry: FaceTemplateMultiRegistry

// Set the delegate
multiRegistry.delegate = object : FaceTemplateMultiRegistry.Delegate {

    // Implement the onFaceTemplatesAdded method
    override fun onFaceTemplatesAdded(faceTemplates: List<TaggedFaceTemplate<*, *>>) {

        // The templates in the faceTemplates list have been added
        sendNewFaceTemplatesToServer(faceTemplates)
    }
}
```