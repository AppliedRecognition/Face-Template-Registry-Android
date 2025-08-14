package com.appliedrec.verid3.facetemplateregistry

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appliedrec.verid3.common.use
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaceTemplateMultiRegistryTest {

    // region Initialisation

    @Test
    fun test_createRegistryWithIncompatibleFaces_fails(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 1, 1)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 1, 1, 1)
        try {
            FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased)
            fail()
        } catch (_: FaceTemplateRegistryException.IncompatibleFaceTemplates) {
            // All good
        }
    }

    @Test
    fun test_createRegistryWithIncompatibleFacesTogglingCheckOnOff(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 1, 1)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 1, 1, 1)
        FaceTemplateMultiRegistry(
            reg1.typeErased,
            reg2.typeErased,
            ensureFaceTemplateCompatibility = false
        )
        try {
            FaceTemplateMultiRegistry(
                reg1.typeErased,
                reg2.typeErased,
                ensureFaceTemplateCompatibility = true
            )
            fail()
        } catch (_: FaceTemplateRegistryException.IncompatibleFaceTemplates) {
            // All good
        }
    }

    @Test
    fun test_getFaceTemplatesOnClosedRegistry_fails(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 1, 1)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 1, 1)
        val multiRegistry = FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased)
        multiRegistry.close()
        try {
            multiRegistry.getFaceTemplates()
            fail()
        } catch (_: IllegalStateException) {
            // All good
        }
    }

    // endregion

    // region Registration

    @Test
    fun test_registerFace(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 0, 1)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 0, 1, 1)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val registeredFaceTemplates =
                multiRegistry.registerFace(createFakeFace(0f), createFakeImage(), "User 1")
            assertEquals(multiRegistry.registries.size, registeredFaceTemplates.size)
            val allFaceTemplates = multiRegistry.getFaceTemplates()
            assertEquals(multiRegistry.registries.size, allFaceTemplates.size)
        }
    }

    @Test
    fun test_registerSimilarFaceAsDifferentIdentifier_fail(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 1, 1)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 1, 1)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            try {
                multiRegistry.registerFace(createFakeFace(0.1f), createFakeImage(), "User 1")
                fail()
            } catch (e: FaceTemplateRegistryException.SimilarFaceAlreadyRegistered) {
                assertEquals("User 0", e.registeredIdentifier)
            }
        }
    }

    @Test
    fun test_registerFaceEnsuringDelegateCalled(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 0, 1)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 0, 1)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val delegate = TestDelegate()
            multiRegistry.delegate = delegate
            val registeredFaceTemplates = multiRegistry.registerFace(
                createFakeFace(0f),
                createFakeImage(),
                "User 1"
            )
            val registeredInDelegate = delegate.completable.await()
            assertEquals(registeredFaceTemplates.size, registeredInDelegate.size)
            val allFaceTemplates = multiRegistry.getFaceTemplates()
            assertEquals(multiRegistry.registries.size, allFaceTemplates.size)
            assertEquals(allFaceTemplates.size, registeredInDelegate.size)
        }
    }

    // endregion

    // region Identification

    @Test
    fun test_identifyFaceInEmptySet_returnsEmptyResult(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 0, 1)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 0, 1)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val idResults = multiRegistry.identifyFace(createFakeFace(0f), createFakeImage())
            assertEquals(0, idResults.size)
        }
    }

    @Test
    fun test_identifyFace(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 10, 2)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 10, 2)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val idResults = multiRegistry.identifyFace(createFakeFace(5.1f), createFakeImage())
            assertEquals(1, idResults.size)
            assertEquals("User 5", idResults[0].taggedFaceTemplate.identifier)
        }
    }

    @Test
    fun test_identifyFaceInCorruptSet_fails(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 1, 1)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 1, 1, 1)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased, ensureFaceTemplateCompatibility = false).use { multiRegistry ->
            try {
                multiRegistry.identifyFace(createFakeFace(0f), createFakeImage())
                fail()
            } catch (_: FaceTemplateRegistryException.IncompatibleFaceTemplates) {
                // All good
            }
            val results =
                multiRegistry.identifyFace(createFakeFace(0f), createFakeImage(), true, false)
            assertEquals(1, results.size)
        }
    }

    @Test
    fun test_autoEnrolFaceAtIdentification(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 10, 2)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 2, 2)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val delegate = TestDelegate()
            multiRegistry.delegate = delegate
            val results = multiRegistry.identifyFace(createFakeFace(5.3f), createFakeImage())
            assertEquals(1, results.size)
            assertEquals("User 5", results[0].taggedFaceTemplate.identifier)
            assertEquals(1, results[0].autoEnrolledFaceTemplates.size)
            val autoEnrolledTemplates = delegate.completable.await()
            assertEquals(1, autoEnrolledTemplates.size)
            assertEquals("User 5", autoEnrolledTemplates[0].identifier)
        }
    }

    // endregion

    // region Authentication

    @Test
    fun test_authenticateFace(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 5, 2)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 5, 2)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val authResult =
                multiRegistry.authenticateFace(createFakeFace(3.1f), createFakeImage(), "User 3")
            assertTrue(authResult.authenticated)
        }
    }

    @Test
    fun test_authenticateFaceInEmptyRegistry_fail(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 0, 2)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 0, 2)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            try {
                multiRegistry.authenticateFace(createFakeFace(0f), createFakeImage(), "User 1")
                fail()
            } catch (e: FaceTemplateRegistryException.IdentifierNotRegistered) {
                assertEquals("User 1", e.identifier)
            }
        }
    }

    @Test
    fun test_authenticateFaceOfUnregisteredUser(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 5, 2)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 5, 2)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val result =
                multiRegistry.authenticateFace(createFakeFace(10.1f), createFakeImage(), "User 1")
            assertFalse(result.authenticated)
        }
    }

    @Test
    fun test_autoEnrolFaceAtAuthentication(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 10, 2)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 2, 2)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val delegate = TestDelegate()
            multiRegistry.delegate = delegate
            val results =
                multiRegistry.authenticateFace(createFakeFace(5.3f), createFakeImage(), "User 5")
            assertTrue(results.authenticated)
            assertEquals(1, results.autoEnrolledFaceTemplates.size)
            val delegateFaces = delegate.completable.await()
            assertEquals(1, delegateFaces.size)
            assertEquals("User 5", delegateFaces[0].identifier)
        }
    }

    @Test
    fun test_doNotAutoEnrolFaceAtAuthentication(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 10, 2)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 2, 2)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val results = multiRegistry.authenticateFace(
                createFakeFace(5.3f),
                createFakeImage(),
                "User 5",
                false
            )
            assertTrue(results.authenticated)
            assertEquals(0, results.autoEnrolledFaceTemplates.size)
        }
    }

    // endregion

    // region Retrieval

    @Test
    fun test_getFaceTemplatesByIdentifier(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 5, 2)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 5, 2)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val templates = multiRegistry.getFaceTemplatesByIdentifier("User 1")
            assertEquals(4, templates.size)
        }
    }

    @Test
    fun test_getIdentifiers(): Unit = runBlocking {
        val reg1 = createRegistry(MockFaceTemplateVersion.Version1, 5, 2)
        val reg2 = createRegistry(MockFaceTemplateVersion.Version2, 5, 2)
        FaceTemplateMultiRegistry(reg1.typeErased, reg2.typeErased).use { multiRegistry ->
            val identifiers = multiRegistry.getIdentifiers()
            assertEquals(5, identifiers.size)
        }
    }

    // endregion
}

private class TestDelegate : FaceTemplateMultiRegistry.Delegate {

    val completable: CompletableDeferred<List<TaggedFaceTemplate<*, *>>> = CompletableDeferred()

    override fun onFaceTemplatesAdded(faceTemplates: List<TaggedFaceTemplate<*, *>>) {
        completable.complete(faceTemplates)
    }
}