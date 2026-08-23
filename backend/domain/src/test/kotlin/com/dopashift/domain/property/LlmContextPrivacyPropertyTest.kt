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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Property 9: LLM Context Privacy Boundary
 *
 * For any LLM call made by the system, the request payload SHALL contain only goal name,
 * goal description, and user-supplied keywords — never full task history, telemetry data,
 * or personally identifiable information beyond those fields.
 *
 * **Validates: Requirements 15.7**
 *
 * Tag: Feature: dopa-shift, Property 9: LLM Context Privacy Boundary
 */
class LlmContextPrivacyPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 9: LLM Context Privacy Boundary")
    )

    test("Property 9: LLM Context Privacy Boundary - LLM calls receive only goal name and keywords, never PII or telemetry") {
        checkAll(100, goalProfileArb()) { testData ->
            // Arrange: spy LLM provider that captures what was passed
            val spyLlmService = SpyLlmProviderService()
            val goalRepository = StubGoalRepository(testData.goal)
            val videoValidationService = StubVideoValidationService()
            val videoCacheRepository = EmptyVideoCacheRepository()

            val useCase = VideoRecommendationUseCase(
                goalRepository = goalRepository,
                llmProviderService = spyLlmService,
                videoValidationService = videoValidationService,
                videoCacheRepository = videoCacheRepository
            )

            // Act: execute the use case
            useCase.execute(
                goalId = testData.goal.id,
                userId = testData.goal.userId,
                locale = UserLocale.EN,
                llmConfigured = true
            )

            // Assert: LLM was called
            spyLlmService.capturedCalls.size shouldBe 1
            val capturedCall = spyLlmService.capturedCalls.first()

            // Assert: captured arguments contain ONLY goal name and keywords
            capturedCall.goalName shouldBe testData.goal.name
            capturedCall.keywords shouldBe testData.goal.keywords

            // Assert: captured arguments do NOT contain userId, email, task history, or telemetry
            val allCapturedText = buildString {
                append(capturedCall.goalName)
                append(" ")
                append(capturedCall.keywords.joinToString(" "))
            }

            // The userId should not appear in the LLM call payload
            allCapturedText shouldNotContain testData.goal.userId.toString()

            // Simulated PII fields should not appear in the LLM call
            allCapturedText shouldNotContain testData.email
            allCapturedText shouldNotContain testData.taskHistory

            // Telemetry data should not appear
            allCapturedText shouldNotContain testData.telemetryData
        }
    }

    test("Property 9: LLM Context Privacy Boundary - LLM provider method signature accepts only goalName and keywords") {
        checkAll(100, goalProfileArb()) { testData ->
            // This test verifies at the structural level that the LlmProviderService.requestVideoSuggestions
            // method signature only accepts (goalName: String, keywords: List<String>).
            // A spy implementation captures exactly what the use case passes.
            val spyLlmService = SpyLlmProviderService()
            val goalRepository = StubGoalRepository(testData.goal)
            val videoValidationService = StubVideoValidationService()
            val videoCacheRepository = EmptyVideoCacheRepository()

            val useCase = VideoRecommendationUseCase(
                goalRepository = goalRepository,
                llmProviderService = spyLlmService,
                videoValidationService = videoValidationService,
                videoCacheRepository = videoCacheRepository
            )

            useCase.execute(
                goalId = testData.goal.id,
                userId = testData.goal.userId,
                locale = UserLocale.EN,
                llmConfigured = true
            )

            // The spy only has goalName and keywords fields — no userId, no email, no telemetry
            val call = spyLlmService.capturedCalls.first()
            call.goalName shouldNotBe null
            call.keywords shouldNotBe null

            // Verify the interface contract: only 2 parameters are passed to requestVideoSuggestions
            // This is enforced by the spy capturing exactly goalName + keywords
            spyLlmService.capturedCalls.size shouldBe 1
        }
    }
})

// === Test Data ===

/**
 * Container for generated test data including a GoalProfile
 * along with simulated PII/telemetry that MUST NOT reach the LLM.
 */
private data class GoalProfileTestData(
    val goal: GoalProfile,
    val email: String,
    val taskHistory: String,
    val telemetryData: String
)

/**
 * Generates random GoalProfile entities with associated PII/telemetry
 * fields that should never appear in the LLM call.
 */
private fun goalProfileArb(): Arb<GoalProfileTestData> = arbitrary {
    val goalId = Arb.uuid().bind()
    val userId = Arb.uuid().bind()
    val nameLength = Arb.int(1..50).bind()
    val categoryLength = Arb.int(1..30).bind()
    val keywordCount = Arb.int(1..5).bind()

    val validChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf(' ', '-', '_')

    val name = buildString {
        append(('a'..'z').random())
        repeat(nameLength - 1) { append(validChars.random()) }
    }.trimEnd()
        .let { if (it.isBlank()) "Goal" else it }
        .take(100)

    val category = buildString {
        append(('a'..'z').random())
        repeat(categoryLength - 1) { append(validChars.random()) }
    }.trimEnd()
        .let { if (it.isBlank()) "Category" else it }
        .take(50)

    val keywords = (1..keywordCount).map {
        val kwLength = Arb.int(1..20).bind()
        buildString {
            append(('a'..'z').random())
            repeat(kwLength - 1) { append(('a'..'z').random()) }
        }.take(50)
    }

    val email = "user${userId.toString().take(8)}@mail.com"

    val taskHistory = "Task history: completed 5 tasks on 2024-01-15, missed 2 on 2024-01-16, userId=$userId"
    val telemetryData = "Telemetry: foreground_app=com.instagram, duration=3600s, device=$userId, timestamp=2024-01-15T10:00:00Z"

    val goal = GoalProfile(
        id = goalId,
        userId = userId,
        name = name,
        category = category,
        keywords = keywords,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        isActive = true
    )

    GoalProfileTestData(
        goal = goal,
        email = email,
        taskHistory = taskHistory,
        telemetryData = telemetryData
    )
}

// === Spy/Stub Implementations ===

/**
 * Spy implementation of LlmProviderService that captures all arguments
 * passed to requestVideoSuggestions for assertion.
 */
private class SpyLlmProviderService : LlmProviderService {
    data class CapturedVideoCall(val goalName: String, val keywords: List<String>)

    val capturedCalls = mutableListOf<CapturedVideoCall>()

    override suspend fun requestVideoSuggestions(goalName: String, keywords: List<String>): LlmVideoResponse {
        capturedCalls.add(CapturedVideoCall(goalName, keywords))
        // Return a valid response with a YouTube URL so the pipeline proceeds
        return LlmVideoResponse(
            videoUrls = listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
            rawResponse = "Here is a video: https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
    }

    override suspend fun requestGoalSuggestions(
        goalName: String,
        description: String,
        keywords: List<String>
    ): LlmSuggestionResponse {
        throw UnsupportedOperationException("Not used in this test")
    }

    override suspend fun validateApiKey(provider: LlmProviderType, apiKey: String): ValidationResult {
        throw UnsupportedOperationException("Not used in this test")
    }
}

/**
 * Stub GoalRepository that returns a single pre-configured goal.
 */
private class StubGoalRepository(private val goal: GoalProfile) : GoalRepository {
    override suspend fun findById(id: UUID): GoalProfile? =
        if (id == goal.id) goal else null

    override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
        if (userId == goal.userId) listOf(goal) else emptyList()

    override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
        if (userId == goal.userId && name.equals(goal.name, ignoreCase = true)) goal else null

    override suspend fun save(goal: GoalProfile): GoalProfile = goal

    override suspend fun delete(id: UUID) {}

    override suspend fun countActiveByUserId(userId: UUID): Int =
        if (userId == goal.userId) 1 else 0
}

/**
 * Stub VideoValidationService that validates all URLs as valid.
 */
private class StubVideoValidationService : VideoValidationService {
    override suspend fun validateVideoUrl(url: String): VideoValidationResult =
        VideoValidationResult(url = url, isValid = true, title = "Test Video")

    override suspend fun searchByKeywords(keywords: List<String>, locale: UserLocale): List<VideoResult> =
        listOf(VideoResult(url = "https://www.youtube.com/watch?v=test123test", title = "Fallback Video", source = VideoSource.YOUTUBE))
}

/**
 * Empty VideoCacheRepository that always reports cache miss and accepts saves.
 */
private class EmptyVideoCacheRepository : VideoCacheRepository {
    override suspend fun findByKeywordSetHash(keywordSetHash: String): List<VideoResult>? = null
    override suspend fun save(keywordSetHash: String, goalId: UUID, videos: List<VideoResult>, expiresAt: Instant) {}
    override suspend fun evictExpired() {}
}
