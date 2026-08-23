package com.dopashift.data.remote

import com.dopashift.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for DopaShift backend REST endpoints (/v1/...).
 * All requests are authenticated via OAuth2 Bearer token (handled by AuthInterceptor).
 */
interface DopaShiftApi {

    // ========================
    // Auth
    // ========================

    @POST("v1/auth/token")
    suspend fun exchangeToken(@Body request: TokenRequest): Response<TokenResponse>

    @POST("v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<TokenResponse>

    @POST("v1/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    // ========================
    // Goals
    // ========================

    @POST("v1/goals")
    suspend fun createGoal(@Body request: CreateGoalRequest): GoalResponse

    @GET("v1/goals")
    suspend fun getGoals(): List<GoalResponse>

    @GET("v1/goals/{id}")
    suspend fun getGoal(@Path("id") id: String): GoalResponse

    @PUT("v1/goals/{id}")
    suspend fun updateGoal(@Path("id") id: String, @Body request: UpdateGoalRequest): GoalResponse

    @DELETE("v1/goals/{id}")
    suspend fun deleteGoal(
        @Path("id") id: String,
        @Query("action") action: String,
        @Query("reassignGoalId") reassignGoalId: String? = null
    ): Response<Unit>

    // ========================
    // Goal Checklist
    // ========================

    @POST("v1/goals/{goalId}/checklist")
    suspend fun createChecklistItem(
        @Path("goalId") goalId: String,
        @Body request: CreateChecklistItemRequest
    ): ChecklistItemResponse

    @GET("v1/goals/{goalId}/checklist")
    suspend fun getChecklistItems(@Path("goalId") goalId: String): List<ChecklistItemResponse>

    @PUT("v1/goals/{goalId}/checklist/{itemId}")
    suspend fun updateChecklistItem(
        @Path("goalId") goalId: String,
        @Path("itemId") itemId: String,
        @Body request: UpdateChecklistItemRequest
    ): ChecklistItemResponse

    @DELETE("v1/goals/{goalId}/checklist/{itemId}")
    suspend fun deleteChecklistItem(
        @Path("goalId") goalId: String,
        @Path("itemId") itemId: String
    ): Response<Unit>

    // ========================
    // Daily Todos
    // ========================

    @POST("v1/todos")
    suspend fun createTodo(@Body request: CreateTodoRequest): TodoResponse

    @GET("v1/todos")
    suspend fun getTodos(@Query("date") date: String): List<TodoResponse>

    @GET("v1/todos/pending")
    suspend fun getPendingTodos(): List<TodoResponse>

    @PUT("v1/todos/{id}")
    suspend fun updateTodo(@Path("id") id: String, @Body request: UpdateTodoRequest): TodoResponse

    @DELETE("v1/todos/{id}")
    suspend fun deleteTodo(@Path("id") id: String): Response<Unit>

    // ========================
    // Habits
    // ========================

    @POST("v1/habits")
    suspend fun activateHabitTrack(@Body request: ActivateHabitTrackRequest): HabitTrackResponse

    @GET("v1/habits")
    suspend fun getHabitTracks(@Query("goalId") goalId: String? = null): List<HabitTrackResponse>

    @GET("v1/habits/{id}")
    suspend fun getHabitTrack(@Path("id") id: String): HabitTrackResponse

    @PUT("v1/habits/{id}/checkpoints/{day}")
    suspend fun updateCheckpoint(
        @Path("id") trackId: String,
        @Path("day") day: Int,
        @Body request: UpdateCheckpointRequest
    ): HabitCheckpointResponse

    @GET("v1/habits/current")
    suspend fun getCurrentHabitForOverlay(): HabitTrackResponse?

    // ========================
    // Reminders
    // ========================

    @POST("v1/reminders")
    suspend fun createReminder(@Body request: CreateReminderRequest): ReminderResponse

    @GET("v1/reminders")
    suspend fun getReminders(): List<ReminderResponse>

    @PUT("v1/reminders/{id}")
    suspend fun updateReminder(
        @Path("id") id: String,
        @Body request: UpdateReminderRequest
    ): ReminderResponse

    @DELETE("v1/reminders/{id}")
    suspend fun deleteReminder(@Path("id") id: String): Response<Unit>

    // ========================
    // Interception Rules
    // ========================

    @POST("v1/interception/rules")
    suspend fun createInterceptionRule(@Body request: CreateInterceptionRuleRequest): InterceptionRuleResponse

    @GET("v1/interception/rules")
    suspend fun getInterceptionRules(): List<InterceptionRuleResponse>

    @PUT("v1/interception/rules/{id}")
    suspend fun updateInterceptionRule(
        @Path("id") id: String,
        @Body request: UpdateInterceptionRuleRequest
    ): InterceptionRuleResponse

    @DELETE("v1/interception/rules/{id}")
    suspend fun deleteInterceptionRule(@Path("id") id: String): Response<Unit>

    // ========================
    // Sync
    // ========================

    @POST("v1/sync/push")
    suspend fun pushSync(@Body request: SyncPushRequest): SyncPushResponse

    @GET("v1/sync/pull")
    suspend fun pullSync(@Query("since") since: Long): SyncPullResponse

    @GET("v1/sync/conflicts")
    suspend fun getConflicts(@Query("since") since: Long): List<ConflictResponse>

    // ========================
    // Analytics
    // ========================

    @GET("v1/analytics/efficiency")
    suspend fun getEfficiencyScores(
        @Query("from") from: String,
        @Query("to") to: String
    ): List<EfficiencyScoreResponse>

    @POST("v1/analytics/efficiency")
    suspend fun submitEfficiencyScore(@Body request: SubmitEfficiencyScoreRequest): EfficiencyScoreResponse

    @GET("v1/dashboard/summary")
    suspend fun getDashboardSummary(): DashboardSummaryResponse

    @GET("v1/dashboard/activity")
    suspend fun getActivityFeed(
        @Query("page") page: Int,
        @Query("size") size: Int = 20
    ): ActivityFeedResponse

    // ========================
    // Profile
    // ========================

    @GET("v1/profile")
    suspend fun getProfile(): ProfileResponse

    @PUT("v1/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ProfileResponse

    @Multipart
    @POST("v1/profile/photo")
    suspend fun uploadProfilePhoto(@Part photo: MultipartBody.Part): ProfilePhotoResponse

    @DELETE("v1/profile/photo")
    suspend fun deleteProfilePhoto(): Response<Unit>

    @POST("v1/profile/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>

    // ========================
    // LLM Configuration
    // ========================

    @POST("v1/settings/llm")
    suspend fun configureLlm(@Body request: ConfigureLlmRequest): LlmConfigResponse

    @GET("v1/settings/llm")
    suspend fun getLlmConfig(): LlmConfigResponse

    @PUT("v1/settings/llm")
    suspend fun updateLlmConfig(@Body request: ConfigureLlmRequest): LlmConfigResponse

    @DELETE("v1/settings/llm")
    suspend fun deleteLlmConfig(): Response<Unit>

    @POST("v1/settings/llm/validate")
    suspend fun validateLlmConfig(): LlmValidationResponse

    // ========================
    // Content / Video Recommendation
    // ========================

    @GET("v1/content/video")
    suspend fun getVideoRecommendation(@Query("goalId") goalId: String): VideoRecommendationResponse
}
