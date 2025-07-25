package com.appliedrec.verid3.facetemplateregistry

import android.graphics.PointF
import android.graphics.RectF
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appliedrec.verid3.common.EulerAngle
import com.appliedrec.verid3.common.Face
import com.appliedrec.verid3.common.IImage
import com.appliedrec.verid3.common.ImageFormat
import kotlinx.coroutines.runBlocking
import org.junit.After

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class FaceTemplateRegistryTest {

    private lateinit var registry: FaceTemplateRegistry<MockFaceTemplateVersion.Version1, Float>
    private lateinit var fakeFaceRecognition: MockFaceRecognition<MockFaceTemplateVersion.Version1>

    @Before
    fun setup() {
        fakeFaceRecognition = MockFaceRecognition(MockFaceTemplateVersion.Version1)
        val templates = listOf(
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 100f), "User1"),
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 0f), "User2"),
        )
        registry = FaceTemplateRegistry(fakeFaceRecognition, templates)
    }

    @After
    fun tearDown() = runBlocking {
        registry.close()
    }

    @Test
    fun returnsInitialTemplates() = runBlocking {
        val templates = registry.getFaceTemplates()
        assertEquals(2, templates.size)
        assertEquals("User1", templates[0].identifier)
        assertEquals("User2", templates[1].identifier)
    }

    @Test
    fun registersNewFace() = runBlocking {
        val newFace = createFakeFace(10f)
        val image = createFakeImage()
        registry.registerFace(newFace, image, "User3")
        val ids = registry.getIdentifiers()
        assertTrue(ids.contains("User3"))
    }

    @Test
    fun registerFaceSimilarToOtherUser_throwsException() = runBlocking {
        val newFace = createFakeFace(100.1f)
        val image = createFakeImage()
        try {
            registry.registerFace(newFace, image, "User3")
            fail("Should have thrown an exception")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun forceRegisterFaceSimilarToOtherUser(): Unit = runBlocking {
        val newFace = createFakeFace(100.1f)
        val image = createFakeImage()
        registry.registerFace(newFace, image, "User3", true)
    }

    @Test
    fun identifyFace_returnsBestMatchingTemplate() = runBlocking {
        val challengeFace = createFakeFace(100.1f)
        val image = createFakeImage()
        val results = registry.identifyFace(challengeFace, image)
        assertEquals(1, results.size)
        val result = results[0]
        assertEquals("User1", result.taggedFaceTemplate.identifier)
        assertTrue("Score should be between 0 and 1", result.score in 0f..1f)
        assertTrue("Score should be closer to 1.0", result.score > 0.5f)
    }

    @Test
    fun identifyFace_filtersOutLowScores() = runBlocking {
        val challengeFace = createFakeFace(50f)
        val image = createFakeImage()
        val results = registry.identifyFace(challengeFace, image)
        assertTrue("No matches should be returned due to high threshold", results.isEmpty())
    }

    @Test
    fun identifyFace_returnsHighestScorePerUser() = runBlocking {
        val duplicateTemplates = listOf(
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 100f), "User1"),
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 100.1f), "User1"),
            TaggedFaceTemplate(MockFaceTemplate(MockFaceTemplateVersion.Version1, 0f), "User2")
        )
        registry = FaceTemplateRegistry(fakeFaceRecognition, duplicateTemplates)
        val challengeFace = createFakeFace(100.2f)
        val image = createFakeImage()
        val results = registry.identifyFace(challengeFace, image)
        assertEquals(1, results.count { it.taggedFaceTemplate.identifier == "User1" })
        val bestScore = results.first { it.taggedFaceTemplate.identifier == "User1" }.score
        assertTrue(bestScore > 0.9f)
    }
}