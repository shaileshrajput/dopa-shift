package com.dopashift.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.dopashift.domain.presentation.MotionPolicy
import com.dopashift.domain.presentation.MotionSpec
import com.dopashift.ui.theme.MotionTokens
import com.dopashift.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Optimistic-update + revert mechanism (DUX-2 AC7, DUX-2 AC8).
 *
 * DUX-2.7 requires the UI to reflect a persisted-state change *optimistically* — before the
 * write/sync completes — and DUX-2.8 requires that, if the write is later rejected by validation
 * or sync-conflict resolution, the UI reverts to the authoritative value and surfaces a
 * **non-blocking** message describing what changed.
 *
 * This file provides that mechanism in two layers so the decision logic is testable without Compose:
 *
 * 1. [optimisticUpdate] / [OptimisticController] — pure(-ish) apply → commit → keep-or-revert core
 *    that carries no Compose dependency and can be exercised under plain JVM/coroutine unit tests.
 * 2. [rememberOptimisticToggle] — a small Compose binding that drives the core, animates the revert
 *    with a [MotionTokens] spec, and honors [rememberReducedMotion] by collapsing that transition.
 *
 * The pure core deliberately does **not** define [DashboardUiState]/`DashboardViewModel` (task 10.1)
 * nor `SectionSkeleton` (task 10.2). It defines only a minimal, clearly-named [OptimisticUiEvent]
 * so it has no hard compile dependency on 10.1's not-yet-final `UiEvent`; 10.1 / 12.x can converge
 * on this type (or map to their own) later.
 */

/**
 * A minimal, non-blocking UI feedback event emitted when an optimistic update is reverted
 * (DUX-2 AC8). Consumers surface this as a snackbar / inline banner — never a blocking dialog.
 *
 * This is intentionally a small local sealed type so this file has no hard dependency on task
 * 10.1's `UiEvent`. When 10.1 finalizes its event type, callers can map [Reverted] onto it (or
 * this type can be promoted to the shared one) without touching the decision logic below.
 */
sealed interface OptimisticUiEvent {
    /**
     * The optimistic change was rejected; the field was restored to its authoritative value.
     *
     * @property message human-readable, localized description of what changed (what was undone)
     */
    data class Reverted(val message: String) : OptimisticUiEvent
}

/**
 * Outcome of an optimistic update after its commit has settled.
 *
 * @param T the type of the field being updated
 */
sealed interface OptimisticResult<out T> {
    /**
     * The commit succeeded; the optimistic value is now the authoritative value.
     *
     * @property value the value that was kept
     */
    data class Kept<T>(val value: T) : OptimisticResult<T>

    /**
     * The commit was rejected; the field must show [authoritative] again and the caller should
     * surface [event] (a non-blocking [OptimisticUiEvent.Reverted]).
     *
     * @property authoritative the value to revert to (the pre-update, server-authoritative value)
     * @property event the non-blocking revert feedback to surface
     * @property cause the throwable that caused the rejection, if the commit failed by throwing
     */
    data class Reverted<T>(
        val authoritative: T,
        val event: OptimisticUiEvent.Reverted,
        val cause: Throwable? = null
    ) : OptimisticResult<T>
}

/**
 * Pure(-ish) apply → commit → keep-or-revert core for a single optimistic field update.
 *
 * The caller is expected to have *already* applied [next] to whatever in-memory state it renders
 * from (that is the optimistic reflection required by DUX-2.7). This function then runs [commit]
 * and decides the authoritative outcome:
 *
 * - If [commit] returns `true`, the update is accepted → [OptimisticResult.Kept] carrying [next].
 * - If [commit] returns `false`, the update is rejected → [OptimisticResult.Reverted] carrying
 *   [current] (the authoritative value to restore) and a [OptimisticUiEvent.Reverted] built from
 *   [revertMessage].
 * - If [commit] throws, the update is treated as rejected the same way, with the throwable attached
 *   as [OptimisticResult.Reverted.cause]. Coroutine cancellation is *not* swallowed — it rethrows.
 *
 * This function performs no state mutation and no UI work, so it is fully unit-testable. Callers
 * (ViewModels / composables) are responsible for applying [next] up-front and, on
 * [OptimisticResult.Reverted], restoring [authoritative] and emitting the [event].
 *
 * @param T the type of the field being updated
 * @param current the authoritative value before the update (what to revert to on rejection)
 * @param next the optimistic value already reflected in the UI
 * @param revertMessage produces the localized, human-readable revert message given ([current], [next])
 * @param commit suspend action performing the write/sync; returns `true` if accepted, `false` if rejected
 * @return [OptimisticResult.Kept] when accepted, otherwise [OptimisticResult.Reverted]
 */
suspend fun <T> optimisticUpdate(
    current: T,
    next: T,
    revertMessage: (from: T, to: T) -> String,
    commit: suspend (T) -> Boolean
): OptimisticResult<T> {
    val accepted = try {
        commit(next)
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (t: Throwable) {
        return OptimisticResult.Reverted(
            authoritative = current,
            event = OptimisticUiEvent.Reverted(revertMessage(next, current)),
            cause = t
        )
    }
    return if (accepted) {
        OptimisticResult.Kept(next)
    } else {
        OptimisticResult.Reverted(
            authoritative = current,
            event = OptimisticUiEvent.Reverted(revertMessage(next, current))
        )
    }
}

/**
 * Reusable, testable controller wrapping the [optimisticUpdate] core with the up-front optimistic
 * apply and the on-reject restore already wired together.
 *
 * The controller owns a single field's [value] and exposes it through [read]. Calling [update]:
 * 1. immediately sets [value] to the optimistic `next` (DUX-2.7 — reflect before the write),
 * 2. runs `commit`,
 * 3. on rejection, restores the prior authoritative value and invokes [onEvent] with a non-blocking
 *    [OptimisticUiEvent.Reverted] (DUX-2.8).
 *
 * It is framework-free: [read] and mutation go through the [read]/[write] accessors the caller
 * supplies, so a ViewModel can back it with a `StateFlow`, a test can back it with a plain `var`,
 * and a composable can back it with mutable state. This keeps the keep-or-revert decision unit
 * testable independent of Compose.
 *
 * @param T the type of the field being controlled
 * @param read reads the current field value
 * @param write writes a new field value (the optimistic apply and the revert both route through this)
 * @param revertMessage builds the localized revert message from ([attempted], [restored]) values
 * @param onEvent receives the non-blocking [OptimisticUiEvent] to surface (e.g. a snackbar)
 */
class OptimisticController<T>(
    private val read: () -> T,
    private val write: (T) -> Unit,
    private val revertMessage: (attempted: T, restored: T) -> String,
    private val onEvent: (OptimisticUiEvent) -> Unit
) {
    /** The current field value (optimistic while a commit is in flight, authoritative otherwise). */
    val value: T get() = read()

    /**
     * Optimistically applies [next], runs [commit], and keeps or reverts based on its result.
     *
     * The optimistic value is written *before* [commit] runs, so the UI reflects the change
     * immediately (DUX-2.7). If [commit] rejects (returns `false` or throws), the prior value is
     * restored and a [OptimisticUiEvent.Reverted] is emitted via `onEvent` (DUX-2.8).
     *
     * @param next the optimistic value to apply
     * @param commit suspend write/sync returning `true` if accepted, `false` if rejected
     * @return the settled [OptimisticResult]
     */
    suspend fun update(next: T, commit: suspend (T) -> Boolean): OptimisticResult<T> {
        val previous = read()
        // DUX-2.7: reflect the change optimistically before the write completes.
        write(next)
        val result = optimisticUpdate(
            current = previous,
            next = next,
            revertMessage = revertMessage,
            commit = commit
        )
        if (result is OptimisticResult.Reverted) {
            // DUX-2.8: restore the authoritative value and surface a non-blocking message.
            write(result.authoritative)
            onEvent(result.event)
        }
        return result
    }
}

/**
 * State handle returned by [rememberOptimisticToggle] for driving an optimistic boolean field
 * (e.g. a to-do / habit / checklist checkbox) from a composable.
 *
 * @property checked the value to render the control from (optimistic during a commit, authoritative otherwise)
 * @property revertProgress a 0f→1f signal that animates when a revert occurs, for a revert transition;
 *   under reduced motion it snaps rather than tweening (DUX-2 AC9)
 * @property toggle requests an optimistic flip to [target], committing via `commit`
 */
class OptimisticToggleState internal constructor(
    val checked: Boolean,
    val revertProgress: State<Float>,
    private val onToggle: (target: Boolean, commit: suspend (Boolean) -> Boolean) -> Unit
) {
    /**
     * Optimistically sets the control to [target] and commits. On rejection the control snaps back
     * to its prior value with a revert transition and a non-blocking [OptimisticUiEvent.Reverted].
     *
     * @param target the value the user is toggling to
     * @param commit suspend write returning `true` if accepted, `false` if rejected
     */
    fun toggle(target: Boolean, commit: suspend (Boolean) -> Boolean) = onToggle(target, commit)
}

/**
 * Composable binding for an optimistic boolean toggle with a motion-token-driven revert transition.
 *
 * Wires an [OptimisticController] to Compose state and animates a revert using [revertMotion]
 * (defaults to [MotionTokens.Quick]). The revert transition is resolved through
 * [MotionPolicy.effective] with [rememberReducedMotion], so when the OS reduced-motion setting is
 * on, the transition collapses (DUX-2 AC9) while the value change itself still happens.
 *
 * The returned [OptimisticToggleState.revertProgress] runs `0f → 1f` each time a revert occurs; a
 * caller can drive a brief highlight/shake/cross-fade from it. It stays at `0f` (or snaps) when
 * reduced motion is active.
 *
 * @param initial the initial authoritative value
 * @param revertMessage builds the localized revert message from ([attempted], [restored]) values
 * @param onEvent receives the non-blocking [OptimisticUiEvent] to surface (e.g. a snackbar host)
 * @param revertMotion the [MotionSpec] token used for the revert transition (default [MotionTokens.Quick])
 * @return an [OptimisticToggleState] to render the control from and to request toggles
 */
@Composable
fun rememberOptimisticToggle(
    initial: Boolean,
    revertMessage: (attempted: Boolean, restored: Boolean) -> String,
    onEvent: (OptimisticUiEvent) -> Unit,
    revertMotion: MotionSpec = MotionTokens.Quick
): OptimisticToggleState {
    val scope: CoroutineScope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val effectiveMotion = MotionPolicy.effective(revertMotion, reducedMotion)

    var checked by remember { mutableStateOf(initial) }
    val revertProgress = remember { Animatable(0f) }

    val controller = remember {
        OptimisticController(
            read = { checked },
            write = { checked = it },
            revertMessage = revertMessage,
            onEvent = onEvent
        )
    }

    // If the authoritative initial value changes upstream, adopt it when no commit is pending.
    LaunchedEffect(initial) { checked = initial }

    return OptimisticToggleState(
        checked = checked,
        revertProgress = revertProgress.asState()
    ) { target, commit ->
        scope.launch {
            val result = controller.update(target, commit)
            if (result is OptimisticResult.Reverted) {
                // Play the revert transition; collapse it under reduced motion (DUX-2 AC9).
                revertProgress.snapTo(0f)
                if (reducedMotion) {
                    revertProgress.snapTo(1f)
                    revertProgress.snapTo(0f)
                } else {
                    revertProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = effectiveMotion.durationMillis,
                            easing = MotionTokens.easingFor(effectiveMotion.easingName)
                        )
                    )
                    revertProgress.snapTo(0f)
                }
            }
        }
    }
}

/** Small adapter exposing an [Animatable]'s value as a read-only [State] without extra imports. */
private fun Animatable<Float, *>.asState(): State<Float> = object : State<Float> {
    override val value: Float get() = this@asState.value
}
