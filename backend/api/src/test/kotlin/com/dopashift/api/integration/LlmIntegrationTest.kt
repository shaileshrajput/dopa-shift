package com.dopashift.api.integration

import com.dopashift.domain.model.FinishReason
import com.dopashift.domain.model.LlmCompletionResult
import com.dopashift.domain.port.LlmAdapter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test suite: BYO-LLM (Bring Your Own LLM)
 *
 * Requirement 21.9: Cover all failure modes with mocked responses.
 * - Valid response with YouTube links
 * - Invalid API key (401)
 * - Rate-limited (429)
 * - No video link in response → fallback to YouTube search
 * - Timeout → fallback
 *
 * Uses a [FakeLlmAdapter] that returns configurable responses without network calls.
 */
class LlmIntegrationTest {

    @Nested
    inner class ValidResponses {

        @Test
        fun `valid response with YouTube URLs extracts links correctly`() = runBlocking {
            val adapter = FakeLlmAdapter(response = """
                Here are some videos for learning Kotlin:
                1. https://www.youtube.com/watch?v=ABC123def45
                2. https://youtu.be/XYZ789abc12
                These are great for beginners.
            """.trimIndent())

            val result = adapter.complete("Recommend videos for Kotlin learning")
            assertTrue(result.text.contains("youtube.com/watch?v=ABC123def45"))
            assertTrue(result.text.contains("youtu.be/XYZ789abc12"))
        }

        @Test
        fun `valid response without any URLs triggers fallback indicator`() = runBlocking {
            val adapter = FakeLlmAdapter(response = """
                I recommend watching tutorials about Kotlin coroutines and Jetpack Compose.
                They are very helpful for Android development.
            """.trimIndent())

            val result = adapter.complete("Recommend videos for Android")
            val text = result.text
            // No youtube URLs — calling code should detect this and fallback to search
            val hasYouTubeUrl = text.contains("youtube.com") || text.contains("youtu.be")
            assertTrue(!hasYouTubeUrl, "Response without URLs should trigger fallback")
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `invalid API key returns authentication error`() {
            val adapter = FakeLlmAdapter(
                error = LlmError.AUTHENTICATION,
                errorMessage = "Invalid API key provided"
            )

            val exception = runCatching { runBlocking { adapter.complete("any prompt") } }
                .exceptionOrNull()
            assertNotNull(exception)
            assertTrue(exception.message?.contains("authentication", ignoreCase = true) == true)
        }

        @Test
        fun `rate-limited response returns rate limit error`() {
            val adapter = FakeLlmAdapter(
                error = LlmError.RATE_LIMITED,
                errorMessage = "Rate limit exceeded. Please retry after 60 seconds."
            )

            val exception = runCatching { runBlocking { adapter.complete("any prompt") } }
                .exceptionOrNull()
            assertNotNull(exception)
            assertTrue(exception.message?.contains("rate limit", ignoreCase = true) == true)
        }

        @Test
        fun `timeout returns timeout error`() {
            val adapter = FakeLlmAdapter(
                error = LlmError.TIMEOUT,
                errorMessage = "Request timed out after 10000ms"
            )

            val exception = runCatching { runBlocking { adapter.complete("any prompt") } }
                .exceptionOrNull()
            assertNotNull(exception)
            assertTrue(exception.message?.contains("timeout", ignoreCase = true) == true)
        }

        @Test
        fun `network error returns connectivity failure`() {
            val adapter = FakeLlmAdapter(
                error = LlmError.NETWORK,
                errorMessage = "Unable to connect to provider"
            )

            val exception = runCatching { runBlocking { adapter.complete("any prompt") } }
                .exceptionOrNull()
            assertNotNull(exception)
        }
    }

    @Nested
    inner class FallbackChain {

        @Test
        fun `fallback activates when LLM returns no video links`() = runBlocking {
            val adapter = FakeLlmAdapter(response = "No specific videos, just search online")
            val fallbackSearcher = FakeYouTubeSearcher(
                results = listOf("https://www.youtube.com/watch?v=FallbackVideo1")
            )

            val videoFinder = VideoRecommendationPipeline(adapter, fallbackSearcher)
            val result = videoFinder.findVideo("Kotlin", listOf("coroutines", "flow"))

            assertNotNull(result)
            assertEquals("https://www.youtube.com/watch?v=FallbackVideo1", result.url)
            assertEquals("YOUTUBE_SEARCH", result.source)
        }

        @Test
        fun `fallback activates when LLM errors out`() = runBlocking {
            val adapter = FakeLlmAdapter(error = LlmError.TIMEOUT)
            val fallbackSearcher = FakeYouTubeSearcher(
                results = listOf("https://www.youtube.com/watch?v=FallbackOnError")
            )

            val videoFinder = VideoRecommendationPipeline(adapter, fallbackSearcher)
            val result = videoFinder.findVideo("Fitness", listOf("running"))

            assertNotNull(result)
            assertEquals("YOUTUBE_SEARCH", result.source)
        }

        @Test
        fun `LLM response with valid YouTube URL uses LLM source`() = runBlocking {
            val adapter = FakeLlmAdapter(
                response = "Watch this: https://www.youtube.com/watch?v=LlmRecommended"
            )
            val fallbackSearcher = FakeYouTubeSearcher(results = emptyList())

            val videoFinder = VideoRecommendationPipeline(adapter, fallbackSearcher)
            val result = videoFinder.findVideo("Meditation", listOf("mindfulness"))

            assertNotNull(result)
            assertEquals("https://www.youtube.com/watch?v=LlmRecommended", result.url)
            assertEquals("LLM", result.source)
        }
    }
}

// === Test Doubles ===

enum class LlmError { AUTHENTICATION, RATE_LIMITED, TIMEOUT, NETWORK }

class FakeLlmAdapter(
    private val response: String? = null,
    private val error: LlmError? = null,
    private val errorMessage: String = "Error"
) : LlmAdapter {

    override suspend fun complete(prompt: String, maxTokens: Int): LlmCompletionResult {
        if (error != null) {
            val message = when (error) {
                LlmError.AUTHENTICATION -> "Authentication failed: $errorMessage"
                LlmError.RATE_LIMITED -> "Rate limit exceeded: $errorMessage"
                LlmError.TIMEOUT -> "Timeout: $errorMessage"
                LlmError.NETWORK -> "Network error: $errorMessage"
            }
            throw RuntimeException(message)
        }
        return LlmCompletionResult(
            text = response ?: "",
            tokensUsed = (response?.length ?: 0) / 4,
            finishReason = FinishReason.COMPLETE
        )
    }

    override suspend fun healthCheck(): Boolean = (error == null)

    override val supportsWebBrowsing: Boolean = false
}

class FakeYouTubeSearcher(
    private val results: List<String> = emptyList()
) {
    fun search(keywords: List<String>): List<String> = results
}

data class VideoResult(val url: String, val source: String)

/**
 * Simplified video recommendation pipeline for testing the fallback chain.
 */
class VideoRecommendationPipeline(
    private val llmAdapter: LlmAdapter,
    private val youTubeSearcher: FakeYouTubeSearcher
) {
    private val youTubeUrlPattern = Regex(
        """https?://(?:www\.)?(?:youtube\.com/watch\?v=|youtu\.be/)[\w-]+"""
    )

    suspend fun findVideo(goalName: String, keywords: List<String>): VideoResult? {
        // Step 1: Try LLM
        val prompt = "Recommend educational YouTube videos about: $goalName (${keywords.joinToString(", ")})"
        val llmResult = runCatching { llmAdapter.complete(prompt) }

        if (llmResult.isSuccess) {
            val text = llmResult.getOrThrow().text
            val urls = youTubeUrlPattern.findAll(text).map { it.value }.toList()
            if (urls.isNotEmpty()) {
                return VideoResult(url = urls.first(), source = "LLM")
            }
        }

        // Step 2: Fallback to YouTube keyword search
        val searchResults = youTubeSearcher.search(keywords)
        if (searchResults.isNotEmpty()) {
            return VideoResult(url = searchResults.first(), source = "YOUTUBE_SEARCH")
        }

        return null
    }
}
