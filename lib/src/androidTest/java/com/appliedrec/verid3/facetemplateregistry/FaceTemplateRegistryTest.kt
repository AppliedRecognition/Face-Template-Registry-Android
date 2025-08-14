package com.appliedrec.verid3.facetemplateregistry

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class FaceTemplateRegistryTest {

    @Test
    fun test_getFaceTemplatesOnClosedRegistry_fails(): Unit = runBlocking {
        val registry = createRegistry(MockFaceTemplateVersion.Version1, 1, 1)
        registry.close()
        try {
            registry.getFaceTemplates()
            fail()
        } catch (_: IllegalStateException) {
            // All good
        }
    }

    // region Registration

    @Test
    fun test_registerFaceTemplate(): Unit = runBlocking {
        val rec = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val registry = FaceTemplateRegistry(rec, emptyList())
        val face = createFakeFace(0f)
        val template = registry.registerFace(face, createFakeImage(), "Test")
        assertEquals(template.data, 0f, 0.001f)
        assertEquals(template.version, MockFaceTemplateVersion.Version1)
        val templates = registry.getFaceTemplates()
        assertEquals(templates.size, 1)
    }

    @Test
    fun test_registerSimilarFaceAsDifferentIdentifier_fail(): Unit = runBlocking {
        val rec = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val templates = (0..<10).map { i ->
            TaggedFaceTemplate(
                MockFaceTemplate(MockFaceTemplateVersion.Version1, i.toFloat()),
                "User $i"
            )
        }
        val registry = FaceTemplateRegistry(rec, templates)
        val face = createFakeFace(5.1f)
        try {
            registry.registerFace(face, createFakeImage(), "New user")
            fail()
        } catch (e: FaceTemplateRegistryException.SimilarFaceAlreadyRegistered) {
            assertEquals(e.registeredIdentifier, "User 5")
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    @Test
    fun test_registerDifferentFaceAsSameIdentifier_fail(): Unit = runBlocking {
        val rec = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val templates = (0..<10).map { i ->
            TaggedFaceTemplate(
                MockFaceTemplate(MockFaceTemplateVersion.Version1, i.toFloat()),
                "User $i"
            )
        }
        val registry = FaceTemplateRegistry(rec, templates)
        val face = createFakeFace(11.0f)
        try {
            registry.registerFace(face, createFakeImage(), "User 1")
            fail()
        } catch (e: FaceTemplateRegistryException.FaceDoesNotMatchExisting) {
            assertTrue(e.maxScore < registry.configuration.authenticationThreshold)
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    // endregion

    // region Identification

    @Test
    fun test_identifyFaceInEmptySet_returnsEmptyResult(): Unit = runBlocking {
        val rec = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val registry = FaceTemplateRegistry(rec, emptyList())
        val face = createFakeFace(5.1f)
        val idResults = registry.identifyFace(face, createFakeImage())
        assertEquals(0, idResults.size)
    }

    @Test
    fun test_identifyFace(): Unit = runBlocking {
        val rec = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val templates = (0..<10).map { i ->
            TaggedFaceTemplate(
                MockFaceTemplate(MockFaceTemplateVersion.Version1, i.toFloat()),
                "User $i"
            )
        }
        val registry = FaceTemplateRegistry(rec, templates)
        val face = createFakeFace(5.1f)
        val idResults = registry.identifyFace(face, createFakeImage())
        idResults.firstOrNull()?.taggedFaceTemplate?.identifier?.let { identifier ->
            if (idResults.size > 1) {

            } else {

            }
        }
        assertEquals(1, idResults.size)
        assertEquals("User 5", idResults[0].taggedFaceTemplate.identifier)
    }

    // endregion

    // region Authentication

    @Test
    fun test_authenticateFace(): Unit = runBlocking {
        val rec = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val templates = (0..<10).map { i ->
            TaggedFaceTemplate(
                MockFaceTemplate(MockFaceTemplateVersion.Version1, i.toFloat()),
                "User $i"
            )
        }
        val registry = FaceTemplateRegistry(rec, templates)
        val face = createFakeFace(5.1f)
        val authResult = registry.authenticateFace(face, createFakeImage(), "User 5")
        assertTrue(authResult.authenticated)
    }

    @Test
    fun test_authenticateFaceInEmptyRegistry_fail(): Unit = runBlocking {
        val rec = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val registry = FaceTemplateRegistry(rec, emptyList())
        val face = createFakeFace(5.1f)
        try {
            registry.authenticateFace(face, createFakeImage(), "User 5")
            fail()
        } catch (e: FaceTemplateRegistryException.IdentifierNotRegistered) {
            assertEquals(e.identifier, "User 5")
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    @Test
    fun test_authenticateFaceOfUnregisteredUser(): Unit = runBlocking {
        val rec = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val templates = (0..<10).map { i ->
            TaggedFaceTemplate(
                MockFaceTemplate(MockFaceTemplateVersion.Version1, i.toFloat()),
                "User $i"
            )
        }
        val registry = FaceTemplateRegistry(rec, templates)
        val face = createFakeFace(50.1f)
        val authResult = registry.authenticateFace(face, createFakeImage(), "User 5")
        assertFalse(authResult.authenticated)
    }
}