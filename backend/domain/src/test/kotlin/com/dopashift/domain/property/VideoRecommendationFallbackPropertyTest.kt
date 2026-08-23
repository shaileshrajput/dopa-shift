package com.dopashift.domain.property

import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.model.LlmProviderType
import com.dopashift.domain.model.LlmSuggestionResponse
import com.dopashift.domain.model.LlmVideoResponse
import com.dopashift.domain.model.UserLocale
import com.dopashift.domain.model.ValidationResult
import com.dopashift.domain.model.VideoResult
import com.dopashift.domain.model.VideoSource
import com.dopashift.domain.model.VideoValidationResult
import com.dopashift.domain.port.LlmProviderService
import com.dopashift.domain.port.VideoValidationService
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.VideoCacheRepository
import com.dopashift.domain.usecase.VideoRecommendationUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Property 8: Video Recommendation Fallback Chain
 *
 * For any overlay trigger where the LLM is configured, if the LLM response contains no parseable
 * video links or all links fail validation, the system SHALL fall back to YouTube keyword search
 * rather than displaying an error or blank state.
 *
 * **Validates: Requirements 3.2, 3.3**
 *
 * Tags: Feature: dopa-shift, Property 8: Video Recommendation Fallback Chain
 */
class VideoRecommendationFallbackPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 8: Video Recommendation Fallback Chain")
    )

    // --- Fake Implementations ---

    class FakeGoalRepository : GoalRepository {
        val store = mutableMapOf<UUID, GoalProfile>()

        override suspend fun findById(id: UUID): GoalProfile? = store[id]
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
            store.values.filter { it.userId == userId }

        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
            store.values.find { it.userId == userId && it.name == name }

        override suspend fun save(goal: GoalProfile): GoalProfile {
            store[goal.id] = goal
            return goal
        }

        override suspend fun delete(id: UUID) {
            store.remove(id)
        }

        override suspend fun countActiveByUserId(userId: UUID): Int =
            store.values.count { it.userId == userId && it.isActive }
    }

    class FakeVideoCacheRepository : VideoCacheRepository {
        override suspend fun findByKeywordSetHash(keywordSetHash: String): List<VideoResult>? = null
        override suspend fun save(keywordSetHash: String, goalId: UUID, videos: List<VideoResult>, expiresAt: Instant) {}
        override suspend fun evictExpired() {}
    }

    /**
     * FakeLlmProviderService that returns a configurable raw response string.
     * This allows property tests to inject responses with no YouTube URLs, invalid URLs, or empty text.
     */
    class FakeLlmProviderService(private val rawResponse: String) : LlmProviderService {
        override suspend fun requestVideoSuggestions(goalName: String, keywords: List<String>): LlmVideoResponse {
            return LlmVideoResponse(videoUrls = emptyList(), rawResponse = rawResponse)
        }

        override suspend fun requestGoalSuggestions(
            goalName: String,
            description: String,
            keywords: List<String>
        ): LlmSuggestionResponse {
            return LlmSuggestionResponse(suggestions = emptyList(), rawResponse = "")
        }

        override suspend fun validateApiKey(provider: LlmProviderType, apiKey: String): ValidationResult {
            return ValidationResult(isValid = true)
        }
    }

    /**
     * FakeVideoValidationService that:
     * - Always fails validation for individual URLs (simulates all links being private/deleted/blocked)
     * - Always returns YouTube search results for keyword search (the fallback)
     */
    class FakeVideoValidationService(
        private val allValidationsFail: Boolean = true
    ) : VideoValidationService {

        override suspend fun validateVideoUrl(url: String): VideoValidationResult {
            return if (allValidationsFail) {
                VideoValidationResult(url = url, isValid = false, errorReason = "Video unavailable")
            } else {
                VideoValidationResult(url = url, isValid = true, title = "Valid Video")
            }
        }

        override suspend fun searchByKeywords(keywords: List<String>, locale: UserLocale): List<VideoResult> {
            // YouTube keyword search always returns results (the fallback source)
            return listOf(
                VideoResult(
                    url = "https://www.youtube.com/watch?v=fallback001",
                    title = "YouTube Search Result for: ${keywords.joinToString(", ")}",
                    source = VideoSource.YOUTUBE
                )
            )
        }
    }

    // --- Generators ---

    val arbKeywords: Arb<List<String>> = Arb.list(
        Arb.string(minSize = 1, maxSize = 30).map { it.take(50).ifBlank { "keyword" } },
        range = 1..5
    )

    val arbLocale: Arb<UserLocale> = Arb.element(UserLocale.entries)

    /** Generates LLM responses with NO parseable YouTube URLs */
    val arbNonYoutubeResponse: Arb<String> = Arb.element(
        listOf(
            "Here are some great resources for your goal: check out coursera.com and udemy.com",
            "I recommend the following books: Clean Code, Pragmatic Programmer",
            "",
            "Visit https://www.example.com/video123 and https://notube.io/watch?v=abc",
            "No video links available at this time.",
            "https://vimeo.com/12345 https://dailymotion.com/video/x789",
            "Try searching for 'kotlin tutorials' on your preferred platform.",
            "Here is a link: http://invalid-domain.xyz/not-a-video"
        )
    )

    /** Generates LLM responses with YouTube URLs that will ALL fail validation */
    val arbInvalidYoutubeUrls: Arb<String> = Arb.int(1..3).map { count ->
        val urls = (1..count).joinToString("\n") { i ->
            "https://www.youtube.com/watch?v=INVALID${i}xxxx"
        }
        "Here are some videos I found:\n$urls\nHope these help with your goal!"
    }

    // --- Property Tests ---

    test("Fallback to YouTube search when LLM response contains no parseable YouTube URLs") {
        checkAll(100, Arb.uuid(), Arb.uuid(), arbKeywords, arbLocale, arbNonYoutubeResponse) {
            goalId, userId, keywords, locale, llmResponse ->

            val goalRepo = FakeGoalRepository()
            val goal = GoalProfile(
                id = goalId,
                userId = userId,
                name = "Test Goal ${goalId.toString().take(8)}",
                category = "Learning",
                keywords = keywords,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                isActive = true
            )
            goalRepo.save(goal)

            val llmService = FakeLlmProviderService(rawResponse = llmResponse)
            val videoValidationService = FakeVideoValidationService(allValidationsFail = true)
            val cacheRepo = FakeVideoCacheRepository()

            val useCase = VideoRecommendationUseCase(
                goalRepository = goalRepo,
                llmProviderService = llmService,
                videoValidationService = videoValidationService,
                videoCacheRepository = cacheRepo
            )

            val result = useCase.execute(
                goalId = goalId,
                userId = userId,
                locale = locale,
                llmConfigured = true
            )

            // Assert: result is never empty — system falls back to YouTube search
            result.videos.shouldNotBeEmpty()
            // Assert: source is YOUTUBE (fallback)
            result.source shouldBe VideoSource.YOUTUBE
            // Assert: all returned videos are from YouTube search
            result.videos.forEach { video ->
                video.source shouldBe VideoSource.YOUTUBE
            }
        }
    }

    test("Fallback to YouTube search when all LLM-suggested URLs fail validation") {
        checkAll(100, Arb.uuid(), Arb.uuid(), arbKeywords, arbLocale, arbInvalidYoutubeUrls) {
            goalId, userId, keywords, locale, llmResponse ->

            val goalRepo = FakeGoalRepository()
            val goal = GoalProfile(
                id = goalId,
                userId = userId,
                name = "Test Goal ${goalId.toString().take(8)}",
                category = "Skills",
                keywords = keywords,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                isActive = true
            )
            goalRepo.save(goal)

            val llmService = FakeLlmProviderService(rawResponse = llmResponse)
            // All URL validations fail (simulates private/deleted/region-blocked)
            val videoValidationService = FakeVideoValidationService(allValidationsFail = true)
            val cacheRepo = FakeVideoCacheRepository()

            val useCase = VideoRecommendationUseCase(
                goalRepository = goalRepo,
                llmProviderService = llmService,
                videoValidationService = videoValidationService,
                videoCacheRepository = cacheRepo
            )

            val result = useCase.execute(
                goalId = goalId,
                userId = userId,
                locale = locale,
                llmConfigured = true
            )

            // Assert: result is never empty — system falls back to YouTube search
            result.videos.shouldNotBeEmpty()
            // Assert: source is YOUTUBE (fallback path was triggered)
            result.source shouldBe VideoSource.YOUTUBE
            // Assert: all returned videos have YOUTUBE source
            result.videos.forEach { video ->
                video.source shouldBe VideoSource.YOUTUBE
            }
        }
    }

    test("Fallback to YouTube search when LLM returns empty response") {
        checkAll(100, Arb.uuid(), Arb.uuid(), arbKeywords, arbLocale) {
            goalId, userId, keywords, locale ->

            val goalRepo = FakeGoalRepository()
            val goal = GoalProfile(
                id = goalId,
                userId = userId,
                name = "Test Goal ${goalId.toString().take(8)}",
                category = "Productivity",
                keywords = keywords,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                isActive = true
            )
            goalRepo.save(goal)

            // Empty response from LLM
            val llmService = FakeLlmProviderService(rawResponse = "")
            val videoValidationService = FakeVideoValidationService(allValidationsFail = true)
            val cacheRepo = FakeVideoCacheRepository()

            val useCase = VideoRecommendationUseCase(
                goalRepository = goalRepo,
                llmProviderService = llmService,
                videoValidationService = videoValidationService,
                videoCacheRepository = cacheRepo
            )

            val result = useCase.execute(
                goalId = goalId,
                userId = userId,
                locale = locale,
                llmConfigured = true
            )

            // Assert: result is never empty — system falls back to YouTube search
            result.videos.shouldNotBeEmpty()
            // Assert: source is YOUTUBE
            result.source shouldBe VideoSource.YOUTUBE
            // Assert: fallback produces VideoSource.YOUTUBE results
            result.videos.forEach { video ->
                video.source shouldBe VideoSource.YOUTUBE
            }
        }
    }
})
