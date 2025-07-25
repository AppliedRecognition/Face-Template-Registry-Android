package com.appliedrec.verid3.facetemplateregistry

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appliedrec.verid3.common.FaceTemplateVersion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaceTemplateMultiRegistryTest {

    lateinit var multiRegistry: FaceTemplateMultiRegistry

    @Before
    fun setup() {
        val recognition1 = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val registry1 = FaceTemplateRegistry(recognition1, listOf(
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 100f), "User1"),
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 0f), "User2"),
        ))
        val recognition2 = MockFaceRecognition(MockFaceTemplateVersion.Version2)
        val registry2 = FaceTemplateRegistry(recognition2, listOf(
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version2, 100f), "User1"),
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version2, 0f), "User2"),
        ))
        multiRegistry = FaceTemplateMultiRegistry(
            registry1 as FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>,
            registry2 as FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>
        )
    }

    @After
    fun tearDown() = runBlocking {
        multiRegistry.close()
    }

    @Test
    fun testInitializationWithIncompatibleFaceTemplates(): Unit = runBlocking {
        val recognition1 = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val registry1 = FaceTemplateRegistry(recognition1, listOf(
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 100f), "User1"),
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 50f), "User2"),
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 0f), "User3"),
        ))
        val recognition2 = MockFaceRecognition(MockFaceTemplateVersion.Version2)
        val registry2 = FaceTemplateRegistry(recognition2, listOf(
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version2, 100f), "User1"),
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version2, 50f), "User2"),
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version2, 0f), "User4"),
        ))
        try {
            FaceTemplateMultiRegistry(
                registry1 as FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>,
                registry2 as FaceTemplateRegistry<FaceTemplateVersion<Any>, Any>
            )
            Assert.fail("Should fail with incompatible face templates")
        } catch (e: IllegalStateException) {
            // OK
        }
    }

    @Test
    fun registerFaceTemplate(): Unit = runBlocking {
        val newFace = createFakeFace(10f)
        val image = createFakeImage()
        multiRegistry.registerFace(newFace, image, "User3")
        val ids = multiRegistry.getIdentifiers()
        Assert.assertTrue(ids.contains("User3"))
        Assert.assertTrue(multiRegistry.registries.all { registry ->
            val ids = registry.getIdentifiers()
            ids.contains("User3")
        })
    }

    @Test
    fun testAutoEnrolment(): Unit = runBlocking {
        // Add User3 to second registry
        multiRegistry.registries[1].registerFace(
            createFakeFace(80f),
            createFakeImage(),
            "User3"
        )
        val challengeFace = createFakeFace(80.1f)
        val image = createFakeImage()
        val results = multiRegistry.identifyFace(challengeFace, image)
        Assert.assertEquals(1, results.size)
        Assert.assertEquals("User3", results[0].taggedFaceTemplate.identifier)
        // Check that User3 has been automatically enrolled to first registry
        Assert.assertTrue(multiRegistry.registries[0].getIdentifiers().contains("User3"))
    }

    @Test
    fun testIdentification(): Unit = runBlocking {
        multiRegistry.registries[1].registerFace(
            createFakeFace(80f),
            createFakeImage(),
            "User3"
        )
        val challengeFace = createFakeFace(80.1f)
        val image = createFakeImage()
        val results = multiRegistry.identifyFace(challengeFace, image)
        Assert.assertEquals(1, results.size)
        Assert.assertEquals("User3", results[0].taggedFaceTemplate.identifier)
        Assert.assertEquals(
            multiRegistry.registries[1].faceRecognition.version,
            results[0].taggedFaceTemplate.faceTemplate.version
        )
    }

    @Test
    fun testIdentification_throwsIfIncompatibleFaces(): Unit = runBlocking {
        multiRegistry.registries[1].registerFace(
            createFakeFace(80f),
            createFakeImage(),
            "User3"
        )
        multiRegistry.registries[0].registerFace(
            createFakeFace(60f),
            createFakeImage(),
            "User4"
        )
        val challengeFace = createFakeFace(80.1f)
        val image = createFakeImage()
        try {
            val results = multiRegistry.identifyFace(challengeFace, image)
            Assert.fail("Should have thrown an exception")
        } catch (e: IllegalStateException) {
            // All good
        }
    }
}