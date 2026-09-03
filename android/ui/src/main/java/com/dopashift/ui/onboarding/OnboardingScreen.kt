package com.dopashift.ui.onboarding

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.domain.presentation.MotionPolicy
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.MotionTokens
import com.dopashift.ui.theme.minTouchTarget
import com.dopashift.ui.theme.politeLiveRegion
import com.dopashift.ui.theme.rememberReducedMotion

/**
 * Onboarding screen implementing Requirement 17.4 / DUX-4.12.
 *
 * A guided, minimal-friction flow completable in under 2 minutes:
 * 1. Welcome — app introduction
 * 2. Profile — capture the user's full name (shown later on the Settings profile)
 * 3. Create Goal — first goal / keyword setup
 * 4. Setup Habit — first habit track
 * 5. Permissions — usage-stats and overlay handoff (reuses [PermissionSetupPage])
 * 6. Done — get started
 *
 * The flow never lands the user on a blank dashboard on first launch (Requirement 17.4).
 * It applies the shared design system: [DopaShiftTheme] tokens for color/spacing/shape,
 * [MotionTokens] + [rememberReducedMotion] for step transitions (DUX-2.9), and the
 * accessibility helpers ([minTouchTarget], [politeLiveRegion]) plus content descriptions
 * for non-decorative icons (DUX-5.1, DUX-5.2, DUX-5.8).
 *
 * On completion or skip the [OnboardingViewModel] persists
 * `UserPreferencesRepository.setOnboardingCompleted(true)` and fires [onOnboardingComplete],
 * which the nav graph uses to route into the dashboard.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    permissionViewModel: PermissionSetupViewModel = hiltViewModel(),
    onOnboardingComplete: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState by permissionViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onOnboardingComplete()
        }
    }

    OnboardingScreenContent(
        uiState = uiState,
        permissionState = permissionState,
        onFullNameChanged = viewModel::onFullNameChanged,
        onGoalNameChanged = viewModel::onGoalNameChanged,
        onGoalCategoryChanged = viewModel::onGoalCategoryChanged,
        onGoalCategorySelected = viewModel::onGoalCategorySelected,
        onGoalKeywordDraftChanged = viewModel::onGoalKeywordDraftChanged,
        onAddGoalKeywordDraft = viewModel::onAddGoalKeywordDraft,
        onAddSuggestedKeyword = viewModel::onAddSuggestedKeyword,
        onRemoveGoalKeyword = viewModel::onRemoveGoalKeyword,
        onHabitDescriptionChanged = viewModel::onHabitDescriptionChanged,
        onNextStep = viewModel::nextStep,
        onPreviousStep = viewModel::previousStep,
        onSkipStep = viewModel::skipStep,
        onSkipOnboarding = viewModel::skipOnboarding,
        onLaunchSettings = { intent -> context.startActivity(intent) },
        onRefreshPermissions = permissionViewModel::refreshPermissionStatus,
        usageStatsIntent = permissionViewModel::createUsageStatsSettingsIntent,
        overlayIntent = permissionViewModel::createOverlaySettingsIntent,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingScreenContent(
    uiState: OnboardingUiState,
    permissionState: PermissionSetupUiState,
    onFullNameChanged: (String) -> Unit,
    onGoalNameChanged: (String) -> Unit,
    onGoalCategoryChanged: (String) -> Unit,
    onGoalCategorySelected: (String) -> Unit,
    onGoalKeywordDraftChanged: (String) -> Unit,
    onAddGoalKeywordDraft: () -> Unit,
    onAddSuggestedKeyword: (String) -> Unit,
    onRemoveGoalKeyword: (String) -> Unit,
    onHabitDescriptionChanged: (String) -> Unit,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onSkipStep: () -> Unit,
    onSkipOnboarding: () -> Unit,
    onLaunchSettings: (Intent) -> Unit,
    onRefreshPermissions: () -> Unit,
    usageStatsIntent: () -> Intent,
    overlayIntent: () -> Intent,
) {
    val pagerState = rememberPagerState(
        initialPage = uiState.currentStep,
        pageCount = { OnboardingSteps.TOTAL },
    )

    // Step transitions honor the OS reduced-motion setting (DUX-2.9): decorative scroll motion is
    // collapsed to `motion-instant` (<= 100ms) while the step change still occurs.
    val reducedMotion = rememberReducedMotion()
    val stepMotion = MotionPolicy.effective(MotionTokens.Standard, reducedMotion)
    val stepAnimation = tween<Float>(
        durationMillis = stepMotion.durationMillis,
        easing = MotionTokens.easingFor(stepMotion.easingName),
    )

    // Sync pager with ViewModel state, animating with the motion-token-derived spec.
    LaunchedEffect(uiState.currentStep) {
        if (pagerState.currentPage != uiState.currentStep) {
            pagerState.animateScrollToPage(
                page = uiState.currentStep,
                animationSpec = stepAnimation,
            )
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Skip button at the top — meets the 48dp minimum touch target (DUX-5.1).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = DopaShiftTheme.spacing.gutter,
                        vertical = DopaShiftTheme.spacing.base,
                    ),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onSkipOnboarding,
                    modifier = Modifier.minTouchTarget(),
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false, // Controlled via buttons to enforce step validation
            ) { page ->
                when (page) {
                    OnboardingSteps.WELCOME -> WelcomePage()
                    OnboardingSteps.PROFILE -> ProfilePage(
                        fullName = uiState.fullName,
                        error = uiState.fullNameError,
                        onFullNameChanged = onFullNameChanged,
                    )
                    OnboardingSteps.CREATE_GOAL -> CreateGoalPage(
                        goalName = uiState.goalName,
                        goalCategory = uiState.goalCategory,
                        goalKeywordDraft = uiState.goalKeywordDraft,
                        goalKeywords = uiState.goalKeywords,
                        presetCategories = uiState.presetCategories,
                        suggestedKeywords = uiState.suggestedKeywords,
                        error = uiState.goalError,
                        isLoading = uiState.isCreatingGoal,
                        onGoalNameChanged = onGoalNameChanged,
                        onGoalCategoryChanged = onGoalCategoryChanged,
                        onGoalCategorySelected = onGoalCategorySelected,
                        onGoalKeywordDraftChanged = onGoalKeywordDraftChanged,
                        onAddGoalKeywordDraft = onAddGoalKeywordDraft,
                        onAddSuggestedKeyword = onAddSuggestedKeyword,
                        onRemoveGoalKeyword = onRemoveGoalKeyword,
                    )
                    OnboardingSteps.SETUP_HABIT -> SetupHabitPage(
                        habitDescription = uiState.habitDescription,
                        error = uiState.habitError,
                        isLoading = uiState.isCreatingHabit,
                        onHabitDescriptionChanged = onHabitDescriptionChanged,
                    )
                    OnboardingSteps.PERMISSIONS -> PermissionSetupPage(
                        uiState = permissionState,
                        onLaunchSettings = onLaunchSettings,
                        onRefreshPermissions = onRefreshPermissions,
                        usageStatsIntent = usageStatsIntent,
                        overlayIntent = overlayIntent,
                    )
                    OnboardingSteps.DONE -> DonePage()
                }
            }

            // Step indicator and navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DopaShiftTheme.spacing.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Page indicator dots
                StepIndicator(
                    totalSteps = OnboardingSteps.TOTAL,
                    currentStep = uiState.currentStep,
                )

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back button
                    if (uiState.currentStep > OnboardingSteps.WELCOME) {
                        OutlinedButton(
                            onClick = onPreviousStep,
                            modifier = Modifier.minTouchTarget(),
                        ) {
                            Text(stringResource(R.string.onboarding_back))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Skip step (for goal/habit creation only)
                    if (uiState.currentStep == OnboardingSteps.CREATE_GOAL ||
                        uiState.currentStep == OnboardingSteps.SETUP_HABIT
                    ) {
                        TextButton(
                            onClick = onSkipStep,
                            modifier = Modifier.minTouchTarget(),
                        ) {
                            Text(stringResource(R.string.onboarding_skip_step))
                        }
                    }

                    // Next/Finish button
                    val isLastStep = uiState.currentStep == OnboardingSteps.DONE
                    val isLoading = uiState.isCreatingGoal || uiState.isCreatingHabit

                    Button(
                        onClick = onNextStep,
                        enabled = !isLoading,
                        modifier = Modifier.minTouchTarget(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                if (isLastStep) {
                                    stringResource(R.string.onboarding_get_started)
                                } else {
                                    stringResource(R.string.onboarding_next)
                                }
                            )
                            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.compact))
                            Icon(
                                imageVector = if (isLastStep) Icons.Default.Check
                                else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
            }
        }
    }
}

// === Page Composables ===

@Composable
private fun WelcomePage() {
    OnboardingPageLayout(
        icon = Icons.Default.Rocket,
        iconContentDescription = stringResource(R.string.onboarding_welcome_icon_content_description),
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_welcome_description),
    )
}

@Composable
private fun ProfilePage(
    fullName: String,
    error: String?,
    onFullNameChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DopaShiftTheme.spacing.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = stringResource(R.string.onboarding_profile_icon_content_description),
            modifier = Modifier.size(48.dp),
            tint = DopaShiftTheme.colors.productiveBlue,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        Text(
            text = stringResource(R.string.onboarding_profile_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        Text(
            text = stringResource(R.string.onboarding_profile_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        OutlinedTextField(
            value = fullName,
            onValueChange = onFullNameChanged,
            label = { Text(stringResource(R.string.onboarding_profile_full_name_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_profile_full_name_placeholder)) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        // Error message — announced to screen readers as a polite live region (DUX-5.8).
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            error?.let {
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.politeLiveRegion(),
                )
            }
        }
    }
}

/**
 * The onboarding "Create Your First Goal" step, redesigned to mirror the Dashboard's playful
 * [com.dopashift.ui.quickcreate.GoalCreationSheet] (DQC-2). Rather than three bare text fields, the
 * user picks a suggested category chip (or types their own), which surfaces bundled keyword
 * suggestions they can tap to add, plus a working keyword list they can build and prune — the same
 * fun, low-friction flow the main app uses, so first-run and everyday goal creation feel identical.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateGoalPage(
    goalName: String,
    goalCategory: String,
    goalKeywordDraft: String,
    goalKeywords: List<String>,
    presetCategories: List<String>,
    suggestedKeywords: List<String>,
    error: String?,
    isLoading: Boolean,
    onGoalNameChanged: (String) -> Unit,
    onGoalCategoryChanged: (String) -> Unit,
    onGoalCategorySelected: (String) -> Unit,
    onGoalKeywordDraftChanged: (String) -> Unit,
    onAddGoalKeywordDraft: () -> Unit,
    onAddSuggestedKeyword: (String) -> Unit,
    onRemoveGoalKeyword: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DopaShiftTheme.spacing.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = stringResource(R.string.onboarding_goal_icon_content_description),
            modifier = Modifier.size(48.dp),
            tint = DopaShiftTheme.colors.productiveBlue,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        Text(
            text = stringResource(R.string.onboarding_goal_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        Text(
            text = stringResource(R.string.onboarding_goal_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        // ---- Goal name --------------------------------------------------------------
        OutlinedTextField(
            value = goalName,
            onValueChange = onGoalNameChanged,
            label = { Text(stringResource(R.string.onboarding_goal_name_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_goal_name_placeholder)) },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

        // ---- Category: free text + suggested preset chips ---------------------------
        OutlinedTextField(
            value = goalCategory,
            onValueChange = onGoalCategoryChanged,
            label = { Text(stringResource(R.string.onboarding_goal_category_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_goal_category_placeholder)) },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        if (presetCategories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            Text(
                text = stringResource(R.string.onboarding_goal_category_presets_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
            ) {
                presetCategories.forEach { preset ->
                    FilterChip(
                        selected = goalCategory == preset,
                        onClick = { onGoalCategorySelected(preset) },
                        enabled = !isLoading,
                        label = { Text(preset) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

        // ---- Keywords: input + suggested chips + working list -----------------------
        OutlinedTextField(
            value = goalKeywordDraft,
            onValueChange = onGoalKeywordDraftChanged,
            label = { Text(stringResource(R.string.onboarding_goal_keyword_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_goal_keyword_placeholder)) },
            singleLine = true,
            enabled = !isLoading,
            supportingText = { Text(stringResource(R.string.onboarding_goal_keyword_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAddGoalKeywordDraft() }),
            trailingIcon = {
                IconButton(
                    onClick = onAddGoalKeywordDraft,
                    enabled = !isLoading && goalKeywordDraft.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.onboarding_goal_keyword_add),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Bundled suggestions for the chosen category, individually addable (DQC-2.3).
        val unusedSuggestions = suggestedKeywords.filter { suggestion ->
            goalKeywords.none { it.equals(suggestion, ignoreCase = true) }
        }
        if (unusedSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
            ) {
                unusedSuggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { onAddSuggestedKeyword(suggestion) },
                        enabled = !isLoading,
                        label = { Text(suggestion) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        // The working keyword list — tap the X to remove each one (DQC-2.3).
        if (goalKeywords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
            ) {
                goalKeywords.forEach { keyword ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveGoalKeyword(keyword) },
                        label = { Text(keyword) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.onboarding_goal_keyword_remove_content_description,
                                    keyword,
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        // Error message — announced to screen readers as a polite live region (DUX-5.8).
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            error?.let {
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .politeLiveRegion(),
                )
            }
        }

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
    }
}

@Composable
private fun SetupHabitPage(
    habitDescription: String,
    error: String?,
    isLoading: Boolean,
    onHabitDescriptionChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DopaShiftTheme.spacing.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = stringResource(R.string.onboarding_habit_icon_content_description),
            modifier = Modifier.size(48.dp),
            tint = DopaShiftTheme.colors.momentumTeal,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        Text(
            text = stringResource(R.string.onboarding_habit_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        Text(
            text = stringResource(R.string.onboarding_habit_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        OutlinedTextField(
            value = habitDescription,
            onValueChange = onHabitDescriptionChanged,
            label = { Text(stringResource(R.string.onboarding_habit_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_habit_placeholder)) },
            singleLine = true,
            enabled = !isLoading,
            supportingText = {
                Text(stringResource(R.string.onboarding_habit_char_count, habitDescription.length))
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        // Error message — announced to screen readers as a polite live region (DUX-5.8).
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            error?.let {
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.politeLiveRegion(),
                )
            }
        }
    }
}

@Composable
private fun DonePage() {
    OnboardingPageLayout(
        icon = Icons.Default.TrackChanges,
        iconContentDescription = stringResource(R.string.onboarding_done_icon_content_description),
        title = stringResource(R.string.onboarding_done_title),
        description = stringResource(R.string.onboarding_done_description),
    )
}

// === Reusable Components ===

/**
 * Standard layout for informational onboarding pages (welcome, done).
 *
 * @param iconContentDescription a localized description of the leading icon; the icon is
 * informational (not purely decorative) so it carries a description per DUX-5.2.
 */
@Composable
private fun OnboardingPageLayout(
    icon: ImageVector,
    iconContentDescription: String,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DopaShiftTheme.spacing.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(64.dp),
            tint = DopaShiftTheme.colors.productiveBlue,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.section))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Horizontal dot indicator showing progress through onboarding steps. The whole row exposes a
 * single "Step X of Y" content description for screen readers (DUX-5.2) while the individual dots
 * remain a decorative, non-color-only visual (active dot is larger).
 */
@Composable
private fun StepIndicator(
    totalSteps: Int,
    currentStep: Int,
) {
    val progressDescription = stringResource(
        R.string.onboarding_step_progress_content_description,
        currentStep + 1,
        totalSteps,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = progressDescription },
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val isCompleted = index < currentStep
            Box(
                modifier = Modifier
                    .size(if (isActive) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isActive -> MaterialTheme.colorScheme.primary
                            isCompleted -> DopaShiftTheme.colors.momentumTeal
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
            )
        }
    }
}
