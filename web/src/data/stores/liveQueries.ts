/**
 * Pre-built Dexie liveQuery factories for common DopaShift data streams.
 *
 * Each function returns a querier suitable for use with `useLiveQuery`.
 * Dexie's liveQuery auto-tracks table reads, so any mutation to the
 * underlying tables triggers an immediate re-emission (typically < 50ms),
 * fulfilling the "updates within 2 seconds without polling" requirement.
 *
 * Validates: Requirements 14.4
 */

import { db } from '../db';
import type { LocalGoal, LocalDailyTodo, LocalHabitTrack } from '../db';

// ─── Goals ───────────────────────────────────────────────────────────────────

/**
 * Returns a querier for all goals belonging to a user.
 * Results update reactively whenever the goals table changes.
 */
export function goalsForUser(userId: string): () => Promise<LocalGoal[]> {
  return () => db.goals.where('userId').equals(userId).toArray();
}

/**
 * Returns a querier for active goals belonging to a user.
 */
export function activeGoalsForUser(userId: string): () => Promise<LocalGoal[]> {
  return () =>
    db.goals
      .where('userId')
      .equals(userId)
      .filter((g) => g.isActive)
      .toArray();
}

// ─── Daily Todos ─────────────────────────────────────────────────────────────

/**
 * Returns a querier for daily todos scoped to a user and a specific date.
 * Uses the compound index [userId+dayDate] for efficient lookup.
 */
export function dailyTodosForUserAndDate(
  userId: string,
  dayDate: string,
): () => Promise<LocalDailyTodo[]> {
  return () =>
    db.dailyTodos
      .where('[userId+dayDate]')
      .equals([userId, dayDate])
      .toArray();
}

/**
 * Returns a querier for incomplete daily todos for a user on a given date.
 */
export function pendingTodosForUserAndDate(
  userId: string,
  dayDate: string,
): () => Promise<LocalDailyTodo[]> {
  return () =>
    db.dailyTodos
      .where('[userId+dayDate]')
      .equals([userId, dayDate])
      .filter((t) => !t.isCompleted)
      .toArray();
}

// ─── Habit Tracks ────────────────────────────────────────────────────────────

/**
 * Returns a querier for active (not finished) habit tracks belonging to a user.
 */
export function activeHabitTracksForUser(
  userId: string,
): () => Promise<LocalHabitTrack[]> {
  return () =>
    db.habitTracks
      .where('userId')
      .equals(userId)
      .filter((t) => !t.isFinished)
      .toArray();
}

/**
 * Returns a querier for all habit tracks linked to a specific goal.
 */
export function habitTracksForGoal(goalId: string): () => Promise<LocalHabitTrack[]> {
  return () => db.habitTracks.where('goalId').equals(goalId).toArray();
}
