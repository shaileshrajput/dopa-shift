/**
 * Typed API endpoint functions for the DopaShift REST API.
 *
 * Organized by resource: goals, todos, habits, sync, analytics, profile.
 * All functions use the authenticated ApiClient singleton.
 */

import { apiClient, type RequestOptions } from './apiClient';
import type { Goal, DailyTodo, HabitTrack, HabitCheckpoint, ChangeLogEntry } from '../../domain/entities';

// ─── Response Types ──────────────────────────────────────────────────────────

export interface PaginatedResponse<T> {
  data: T[];
  page: number;
  size: number;
  total: number;
}

export interface SyncPushResponse {
  serverTimestamps: Record<string, string>;
  resolved?: ChangeLogEntry[];
  conflicts?: ChangeLogEntry[];
}

export interface SyncPullResponse {
  events: ChangeLogEntry[];
  serverTimestamp: string;
}

export interface EfficiencyScore {
  id: string;
  userId: string;
  date: string;
  productiveSeconds: number;
  totalTrackedSeconds: number;
  scorePercent: number | null;
  computedAt: string;
}

export interface DashboardSummary {
  pendingTodos: number;
  activeGoals: number;
  activeHabits: number;
  todayScore: EfficiencyScore | null;
}

export interface ActivityFeedItem {
  id: string;
  type: string;
  entityId: string;
  description: string;
  timestamp: string;
}

export interface UserProfile {
  id: string;
  displayName: string;
  email: string;
  preferredLocale: string;
  accentColor: string | null;
  profilePhotoUrl: string | null;
  timezone: string;
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
}

// ─── Goals API ───────────────────────────────────────────────────────────────

export const goalsApi = {
  list(options?: RequestOptions): Promise<Goal[]> {
    return apiClient.get<Goal[]>('/goals', options);
  },

  getById(id: string, options?: RequestOptions): Promise<Goal> {
    return apiClient.get<Goal>(`/goals/${id}`, options);
  },

  create(data: { name: string; category: string; keywords: string[] }, options?: RequestOptions): Promise<Goal> {
    return apiClient.post<Goal>('/goals', data, options);
  },

  update(id: string, data: Partial<{ name: string; category: string; keywords: string[]; isActive: boolean }>, options?: RequestOptions): Promise<Goal> {
    return apiClient.put<Goal>(`/goals/${id}`, data, options);
  },

  delete(id: string, action: 'DELETE_ALL' | 'REASSIGN' = 'DELETE_ALL', options?: RequestOptions): Promise<void> {
    return apiClient.delete(`/goals/${id}?action=${action}`, options);
  },
};

// ─── Daily Todos API ─────────────────────────────────────────────────────────

export const todosApi = {
  listByDate(date: string, options?: RequestOptions): Promise<DailyTodo[]> {
    return apiClient.get<DailyTodo[]>(`/todos?date=${date}`, options);
  },

  getPending(options?: RequestOptions): Promise<DailyTodo[]> {
    return apiClient.get<DailyTodo[]>('/todos/pending', options);
  },

  create(data: { text: string; dayDate: string; dueDateTime?: string | null }, options?: RequestOptions): Promise<DailyTodo> {
    return apiClient.post<DailyTodo>('/todos', data, options);
  },

  update(id: string, data: Partial<{ text: string; isCompleted: boolean; dueDateTime: string | null }>, options?: RequestOptions): Promise<DailyTodo> {
    return apiClient.put<DailyTodo>(`/todos/${id}`, data, options);
  },

  delete(id: string, options?: RequestOptions): Promise<void> {
    return apiClient.delete(`/todos/${id}`, options);
  },
};

// ─── Habits API ──────────────────────────────────────────────────────────────

export const habitsApi = {
  listByGoal(goalId: string, options?: RequestOptions): Promise<HabitTrack[]> {
    return apiClient.get<HabitTrack[]>(`/habits?goalId=${goalId}`, options);
  },

  getById(id: string, options?: RequestOptions): Promise<HabitTrack & { checkpoints: HabitCheckpoint[] }> {
    return apiClient.get<HabitTrack & { checkpoints: HabitCheckpoint[] }>(`/habits/${id}`, options);
  },

  getCurrent(options?: RequestOptions): Promise<HabitTrack | null> {
    return apiClient.get<HabitTrack | null>('/habits/current', options);
  },

  activate(data: { goalId: string }, options?: RequestOptions): Promise<HabitTrack> {
    return apiClient.post<HabitTrack>('/habits', data, options);
  },

  updateCheckpoint(
    habitId: string,
    dayNumber: number,
    data: { status: 'COMPLETED' | 'MISSED' },
    options?: RequestOptions,
  ): Promise<HabitCheckpoint> {
    return apiClient.put<HabitCheckpoint>(`/habits/${habitId}/checkpoints/${dayNumber}`, data, options);
  },
};

// ─── Sync API ────────────────────────────────────────────────────────────────

export const syncApi = {
  push(events: ChangeLogEntry[], options?: RequestOptions): Promise<SyncPushResponse> {
    return apiClient.post<SyncPushResponse>('/sync/push', { events }, options);
  },

  pull(since?: string, options?: RequestOptions): Promise<SyncPullResponse> {
    const query = since ? `?since=${encodeURIComponent(since)}` : '';
    return apiClient.get<SyncPullResponse>(`/sync/pull${query}`, options);
  },

  getConflicts(since?: string, options?: RequestOptions): Promise<ChangeLogEntry[]> {
    const query = since ? `?since=${encodeURIComponent(since)}` : '';
    return apiClient.get<ChangeLogEntry[]>(`/sync/conflicts${query}`, options);
  },
};

// ─── Analytics API ───────────────────────────────────────────────────────────

export const analyticsApi = {
  getEfficiency(from: string, to: string, options?: RequestOptions): Promise<EfficiencyScore[]> {
    return apiClient.get<EfficiencyScore[]>(`/analytics/efficiency?from=${from}&to=${to}`, options);
  },

  submitEfficiency(data: { date: string; productiveSeconds: number; totalTrackedSeconds: number }, options?: RequestOptions): Promise<EfficiencyScore> {
    return apiClient.post<EfficiencyScore>('/analytics/efficiency', data, options);
  },

  getDashboardSummary(options?: RequestOptions): Promise<DashboardSummary> {
    return apiClient.get<DashboardSummary>('/dashboard/summary', options);
  },

  getActivityFeed(page = 1, size = 20, options?: RequestOptions): Promise<PaginatedResponse<ActivityFeedItem>> {
    return apiClient.get<PaginatedResponse<ActivityFeedItem>>(`/dashboard/activity?page=${page}&size=${size}`, options);
  },
};

// ─── Profile API ─────────────────────────────────────────────────────────────

export const profileApi = {
  get(options?: RequestOptions): Promise<UserProfile> {
    return apiClient.get<UserProfile>('/profile', options);
  },

  update(data: Partial<UserProfile>, options?: RequestOptions): Promise<UserProfile> {
    return apiClient.put<UserProfile>('/profile', data, options);
  },

  changePassword(data: { currentPassword: string; newPassword: string }, options?: RequestOptions): Promise<void> {
    return apiClient.post('/profile/password', data, options);
  },

  uploadPhoto(file: File, options?: RequestOptions): Promise<{ url: string }> {
    // Photo upload uses multipart, so we bypass the JSON apiClient for this endpoint
    return apiClient.post<{ url: string }>('/profile/photo', { fileName: file.name, contentType: file.type }, options);
  },

  deletePhoto(options?: RequestOptions): Promise<void> {
    return apiClient.delete('/profile/photo', options);
  },
};
