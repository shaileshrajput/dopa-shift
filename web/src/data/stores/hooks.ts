/**
 * Convenience React hooks for reactive data subscriptions.
 *
 * These wrap `useLiveQuery` with the pre-built queriers from liveQueries.ts,
 * giving components a simple, one-call interface for subscribing to local data.
 *
 * All hooks provide automatic updates when the underlying Dexie tables change —
 * no polling, no manual refresh.
 *
 * Validates: Requirements 14.4
 */

import { useEffect, useState } from 'react';
import { useLiveQuery, type LiveQueryState } from './useLiveQuery';
import {
  goalsForUser,
  activeGoalsForUser,
  dailyTodosForUserAndDate,
  pendingTodosForUserAndDate,
  activeHabitTracksForUser,
  habitTracksForGoal,
} from './liveQueries';
import { syncStatusStore, type SyncStatus } from '../sync/SyncStatus';
import type { LocalGoal, LocalDailyTodo, LocalHabitTrack } from '../db';

// ─── Goal Hooks ──────────────────────────────────────────────────────────────

/** Subscribe to all goals for a user. */
export function useGoals(userId: string): LiveQueryState<LocalGoal[]> {
  return useLiveQuery(goalsForUser(userId), [userId], []);
}

/** Subscribe to active goals for a user. */
export function useActiveGoals(userId: string): LiveQueryState<LocalGoal[]> {
  return useLiveQuery(activeGoalsForUser(userId), [userId], []);
}

// ─── Daily Todo Hooks ────────────────────────────────────────────────────────

/** Subscribe to daily todos for a specific user and date. */
export function useDailyTodos(
  userId: string,
  dayDate: string,
): LiveQueryState<LocalDailyTodo[]> {
  return useLiveQuery(dailyTodosForUserAndDate(userId, dayDate), [userId, dayDate], []);
}

/** Subscribe to pending (incomplete) daily todos for a specific user and date. */
export function usePendingTodos(
  userId: string,
  dayDate: string,
): LiveQueryState<LocalDailyTodo[]> {
  return useLiveQuery(pendingTodosForUserAndDate(userId, dayDate), [userId, dayDate], []);
}

// ─── Habit Track Hooks ───────────────────────────────────────────────────────

/** Subscribe to active habit tracks for a user. */
export function useActiveHabitTracks(userId: string): LiveQueryState<LocalHabitTrack[]> {
  return useLiveQuery(activeHabitTracksForUser(userId), [userId], []);
}

/** Subscribe to habit tracks for a specific goal. */
export function useHabitTracksForGoal(goalId: string): LiveQueryState<LocalHabitTrack[]> {
  return useLiveQuery(habitTracksForGoal(goalId), [goalId], []);
}

// ─── Sync Status Hook ────────────────────────────────────────────────────────

/**
 * Subscribe to the sync engine's status observable (RxJS BehaviorSubject).
 *
 * This provides real-time sync state (idle, syncing, error, offline)
 * plus pending count and last sync time for UI indicators.
 */
export function useSyncStatus(): SyncStatus {
  const [status, setStatus] = useState<SyncStatus>(syncStatusStore.current);

  useEffect(() => {
    const subscription = syncStatusStore.status$.subscribe(setStatus);
    return () => subscription.unsubscribe();
  }, []);

  return status;
}
