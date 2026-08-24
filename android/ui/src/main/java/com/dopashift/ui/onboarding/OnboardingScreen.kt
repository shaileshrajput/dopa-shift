package com.dopashift.ui.onboarding

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.ui.theme.DopaShiftTheme

/**
 * Onboarding screen implementing Requirement 17.4.
 *
 * A guided, 5-screen flow completable in under 2 minutes:
 * 1. Welcome — app introduction
 * 2. Create Goal — first goal setup
 * 3. Setup Habit — first habit track
 * 4. Permissions — usage stats and overlay explanation
 * 5. Done — get started
 *
 * Supports skipping and forward/back navigation via HorizontalPager.
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
        onGoalNameChanged = viewModel::onGoalNameChanged,
        onGoalCategoryChanged = viewModel::onGoalCategoryChanged,
        onGoalKeywordChanged = viewModel::onGoalKeywordChanged,
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
    onGoalNameChanged: (String) -> Unit,
    onGoalCategoryChanged: (String) -> Unit,
    onGoalKeywordChanged: (String) -> Unit,
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

    // Sync pager with ViewModel state
    LaunchedEffect(uiState.currentStep) {
        if (pagerState.currentPage != uiState.currentStep) {
            pagerState.animateScrollToPage(uiState.currentStep)
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Skip button at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DopaShiftTheme.spacing.gutter, vertical = DopaShiftTheme.spacing.base),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onSkipOnboarding) {
                    Text("Skip")
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
                    OnboardingSteps.CREATE_GOAL -> CreateGoalPage(
                        goalName = uiState.goalName,
                        goalCategory = uiState.goalCategory,
                        goalKeyword = uiState.goalKeyword,
                        error = uiState.goalError,
                        isLoading = uiState.isCreatingGoal,
                        onGoalNameChanged = onGoalNameChanged,
                        onGoalCategoryChanged = onGoalCategoryChanged,
                        onGoalKeywordChanged = onGoalKeywordChanged,
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
                        OutlinedButton(onClick = onPreviousStep) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Skip step (for goal/habit creation only)
                    if (uiState.currentStep == OnboardingSteps.CREATE_GOAL ||
                        uiState.currentStep == OnboardingSteps.SETUP_HABIT
                    ) {
                        TextButton(onClick = onSkipStep) {
                            Text("Skip this step")
                        }
                    }

                    // Next/Finish button
                    val isLastStep = uiState.currentStep == OnboardingSteps.DONE
                    val isLoading = uiState.isCreatingGoal || uiState.isCreatingHabit

                    Button(
                        onClick = onNextStep,
                        enabled = !isLoading,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(if (isLastStep) "Get Started" else "Next")
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
        title = "Welcome to DopaShift",
        description = "Take control of your screen time and build positive habits. " +
            "DopaShift helps you define life goals, intercept distracting apps, " +
            "and track your progress with daily micro-habits.",
    )
}

@Composable
private fun CreateGoalPage(
    goalName: String,
    goalCategory: String,
    goalKeyword: String,
    error: String?,
    isLoading: Boolean,
    onGoalNameChanged: (String) -> Unit,
    onGoalCategoryChanged: (String) -> Unit,
    onGoalKeywordChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DopaShiftTheme.spacing.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = DopaShiftTheme.colors.productiveBlue,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        Text(
            text = "Create Your First Goal",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        Text(
            text = "What do you want to achieve? Set a goal to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        OutlinedTextField(
            value = goalName,
            onValueChange = onGoalNameChanged,
            label = { Text("Goal name") },
            placeholder = { Text("e.g., Career Growth") },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

        OutlinedTextField(
            value = goalCategory,
            onValueChange = onGoalCategoryChanged,
            label = { Text("Category") },
            placeholder = { Text("e.g., Professional") },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

        OutlinedTextField(
            value = goalKeyword,
            onValueChange = onGoalKeywordChanged,
            label = { Text("Keyword") },
            placeholder = { Text("e.g., programming") },
            singleLine = true,
            enabled = !isLoading,
            supportingText = {
                Text("Used for content recommendations")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        // Error message
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
                )
            }
        }
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
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = DopaShiftTheme.colors.momentumTeal,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        Text(
            text = "Set Up Your First Habit",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        Text(
            text = "Define a daily micro-habit to build over 30 days. " +
                "It will appear in the intercept overlay when distracting apps are blocked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        OutlinedTextField(
            value = habitDescription,
            onValueChange = onHabitDescriptionChanged,
            label = { Text("Daily habit") },
            placeholder = { Text("e.g., Read for 10 minutes") },
            singleLine = true,
            enabled = !isLoading,
            supportingText = {
                Text("${habitDescription.length}/200")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        // Error message
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
                )
            }
        }
    }
}

@Composable
private fun DonePage() {
    OnboardingPageLayout(
        icon = Icons.Default.TrackChanges,
        title = "You're All Set!",
        description = "Your goals and habits are ready. DopaShift will track your screen time, " +
            "intercept distracting apps, and help you build better habits every day.\n\n" +
            "Tap \"Get Started\" to begin your journey.",
    )
}

// === Reusable Components ===

/**
 * Standard layout for informational onboarding pages (welcome, permissions, done).
 */
@Composable
private fun OnboardingPageLayout(
    icon: ImageVector,
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
            contentDescription = null,
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
 * Horizontal dot indicator showing progress through onboarding steps.
 */
@Composable
private fun StepIndicator(
    totalSteps: Int,
    currentStep: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
        verticalAlignment = Alignment.CenterVertically,
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
