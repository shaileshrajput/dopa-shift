import Dexie, { type Table } from 'dexie';

/** Local goal record stored in IndexedDB. */
export interface LocalGoal {
  id: string;
  userId: string;
  name: string;
  category: string;
  keywords: string[]; // stored as JSON array
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Local goal checklist item stored in IndexedDB (distinct from DailyTodo). */
export interface LocalGoalChecklistItem {
  id: string;
  goalId: string;
  userId: string;
  text: string; // max 200 chars
  isCompleted: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Local daily to-do record stored in IndexedDB. */
export interface LocalDailyTodo {
  id: string;
  userId: string;
  text: string;
  dueDateTime: string | null;
  isCompleted: boolean;
  dayDate: string; // ISO date YYYY-MM-DD
  createdAt: string;
  updatedAt: string;
}

/** Local habit track record stored in IndexedDB. */
export interface LocalHabitTrack {
  id: string;
  goalId: string;
  userId: string;
  startDate: string;
  currentDay: number;
  isFinished: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Local change log entry for offline sync queue. */
export interface LocalChangeLog {
  id: string;
  entityId: string;
  entityType: string;
  field: string;
  value: string | null;
  timestamp: string;
  deviceId: string;
  userId: string;
}

/** Local reminder stored in IndexedDB. */
export interface LocalReminder {
  id: string;
  userId: string;
  entityType: 'DAILY_TODO' | 'GOAL_CHECKLIST' | 'HABIT_CHECKPOINT';
  entityId: string;
  /** Display label for the linked entity (denormalized for display). */
  entityLabel: string;
  scheduledTime: string; // HH:mm
  scheduledDate: string | null; // ISO date YYYY-MM-DD, null for repeating
  recurrenceType: 'DAILY' | 'SPECIFIC_WEEKDAYS' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM_INTERVAL' | null;
  recurrenceWeekdays: number[]; // ISO day-of-week (1=Mon..7=Sun)
  recurrenceIntervalDays: number | null;
  conditionType: 'ENTITY_INCOMPLETE' | null;
  escalationIntervalMinutes: number;
  maxEscalations: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Local sync state tracking last pull timestamp. */
export interface LocalSyncState {
  key: string;
  lastSyncTimestamp: string;
}

/**
 * DopaShift Dexie database.
 *
 * Indexed fields follow the design document specification:
 * - goals: id (PK), userId, name
 * - dailyTodos: id (PK), userId, dayDate, [userId+dayDate] (compound)
 * - habitTracks: id (PK), goalId, userId
 * - changeLog: id (PK), [userId+timestamp] (compound), entityId
 * - syncState: key (PK)
 */
export class DopaShiftDB extends Dexie {
  goals!: Table<LocalGoal, string>;
  goalChecklistItems!: Table<LocalGoalChecklistItem, string>;
  dailyTodos!: Table<LocalDailyTodo, string>;
  habitTracks!: Table<LocalHabitTrack, string>;
  reminders!: Table<LocalReminder, string>;
  changeLog!: Table<LocalChangeLog, string>;
  syncState!: Table<LocalSyncState, string>;

  constructor() {
    super('dopashift');

    this.version(1).stores({
      goals: 'id, userId, name',
      goalChecklistItems: 'id, goalId, userId',
      dailyTodos: 'id, userId, dayDate, [userId+dayDate]',
      habitTracks: 'id, goalId, userId',
      changeLog: 'id, [userId+timestamp], entityId',
      syncState: 'key',
    });

    this.version(2).stores({
      goals: 'id, userId, name',
      goalChecklistItems: 'id, goalId, userId',
      dailyTodos: 'id, userId, dayDate, [userId+dayDate]',
      habitTracks: 'id, goalId, userId',
      reminders: 'id, userId, entityId, entityType',
      changeLog: 'id, [userId+timestamp], entityId',
      syncState: 'key',
    });
  }
}

/** Singleton database instance. */
export const db = new DopaShiftDB();
