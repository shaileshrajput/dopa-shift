/**
 * Web portal sync engine client.
 *
 * Mirrors the Android sync engine behavior:
 * - Appends local changes to the Dexie changeLog table
 * - Pushes queued entries to POST /v1/sync/push
 * - Pulls remote entries from GET /v1/sync/pull?since={lastSync}
 * - Retries with exponential backoff (1s, 2s, 4s, 8s, 16s) up to 5 attempts
 * - Tracks sync state (lastSyncTimestamp) in Dexie syncState table
 * - Exposes sync status as an observable (for UI indicators)
 * - Integrates with Service Worker for background sync
 *
 * Validates: Requirements 4.4, 4.5, 4.8, 4.10
 */

import { db, type LocalChangeLog } from '../db';
import { API_BASE_URL } from '../api';
import { retryWithBackoff } from './retryWithBackoff';
import { syncStatusStore, type SyncStatus } from './SyncStatus';
import type { Observable } from 'rxjs';

// ─── Types ───────────────────────────────────────────────────────────────────

/** Shape of a change log entry as returned by the server on pull. */
export interface RemoteChangeLogEntry {
  id: string;
  entityId: string;
  entityType: string;
  field: string;
  value: string | null;
  timestamp: string;
  deviceId: string;
  userId: string;
}

/** Server response from POST /v1/sync/push. */
interface PushResponse {
  serverTimestamps: Record<string, string>;
  resolved?: RemoteChangeLogEntry[];
  conflicts?: RemoteChangeLogEntry[];
}

/** Server response from GET /v1/sync/pull. */
interface PullResponse {
  events: RemoteChangeLogEntry[];
  serverTimestamp: string;
}

// ─── Constants ───────────────────────────────────────────────────────────────

const SYNC_STATE_KEY = 'lastSync';
const BACKGROUND_SYNC_TAG = 'dopashift-sync';

// ─── SyncEngine ──────────────────────────────────────────────────────────────

/**
 * Main sync orchestrator for the web portal.
 *
 * Usage:
 *   const engine = new SyncEngine(getAccessToken);
 *   engine.start();         // begin periodic sync
 *   engine.stop();          // stop periodic sync
 *   engine.syncNow();       // trigger immediate sync
 *   engine.status$;         // subscribe for UI indicators
 */
export class SyncEngine {
  private intervalId: ReturnType<typeof setInterval> | null = null;
  private abortController: AbortController | null = null;
  private isSyncing = false;
  private readonly getToken: () => Promise<string>;
  private readonly syncIntervalMs: number;

  /**
   * @param getToken - Async function returning a valid OAuth2 bearer token.
   * @param syncIntervalMs - Interval between automatic sync cycles (default: 30000ms / 30s).
   */
  constructor(getToken: () => Promise<string>, syncIntervalMs = 30_000) {
    this.getToken = getToken;
    this.syncIntervalMs = syncIntervalMs;
  }

  /** Observable sync status stream for UI bindings. */
  get status$(): Observable<SyncStatus> {
    return syncStatusStore.status$;
  }

  /** Current sync status snapshot. */
  get currentStatus(): SyncStatus {
    return syncStatusStore.current;
  }

  // ─── Lifecycle ─────────────────────────────────────────────────────────────

  /** Start periodic sync. */
  start(): void {
    if (this.intervalId !== null) return;

    // Listen for online/offline events
    window.addEventListener('online', this.handleOnline);
    window.addEventListener('offline', this.handleOffline);

    if (!navigator.onLine) {
      syncStatusStore.update({ state: 'offline' });
      return;
    }

    this.scheduleSync();
    // Trigger immediate sync on start
    void this.syncNow();
  }

  /** Stop periodic sync and cancel any in-progress operations. */
  stop(): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }

    this.abortController?.abort();
    this.abortController = null;

    window.removeEventListener('online', this.handleOnline);
    window.removeEventListener('offline', this.handleOffline);

    syncStatusStore.reset();
  }

  // ─── Public API ────────────────────────────────────────────────────────────

  /**
   * Append a local change to the Dexie changeLog table.
   * This queues the entry for the next push cycle.
   */
  async appendChange(entry: Omit<LocalChangeLog, 'id' | 'timestamp'>): Promise<void> {
    const changeEntry: LocalChangeLog = {
      id: crypto.randomUUID(),
      timestamp: new Date().toISOString(),
      ...entry,
    };

    await db.changeLog.add(changeEntry);
    const count = await db.changeLog.count();
    syncStatusStore.update({ pendingCount: count });
  }

  /**
   * Trigger an immediate sync cycle (push then pull).
   * Safe to call concurrently — only one sync runs at a time.
   */
  async syncNow(): Promise<void> {
    if (this.isSyncing) return;
    if (!navigator.onLine) {
      syncStatusStore.update({ state: 'offline' });
      return;
    }

    this.isSyncing = true;
    this.abortController = new AbortController();
    syncStatusStore.update({ state: 'syncing', errorMessage: null, retryAttempt: 0 });

    try {
      await this.push();
      await this.pull();

      const lastSyncAt = new Date().toISOString();
      syncStatusStore.update({
        state: 'idle',
        pendingCount: 0,
        lastSyncAt,
        errorMessage: null,
        retryAttempt: 0,
      });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Sync failed';
      syncStatusStore.update({ state: 'error', errorMessage: message });

      // Register background sync if available
      await this.registerBackgroundSync();
    } finally {
      this.isSyncing = false;
      this.abortController = null;
    }
  }

  // ─── Push ──────────────────────────────────────────────────────────────────

  /**
   * Push all queued local changeLog entries to the server.
   * Retries with exponential backoff on failure.
   */
  private async push(): Promise<void> {
    const entries = await db.changeLog.toArray();
    if (entries.length === 0) return;

    const token = await this.getToken();

    const response = await retryWithBackoff<PushResponse>(
      async () => {
        const res = await fetch(`${API_BASE_URL}/sync/push`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({ events: entries }),
          signal: this.abortController?.signal,
        });

        if (!res.ok) {
          throw new Error(`Push failed: ${res.status} ${res.statusText}`);
        }

        return res.json() as Promise<PushResponse>;
      },
      {
        maxAttempts: 5,
        baseDelayMs: 1000,
        signal: this.abortController?.signal,
        onRetry: (attempt) => {
          syncStatusStore.update({ retryAttempt: attempt });
        },
      },
    );

    // On successful push, remove the pushed entries from the local queue
    const pushedIds = entries.map((e) => e.id);
    await db.changeLog.bulkDelete(pushedIds);

    // Apply any conflict resolutions returned by the server
    if (response.resolved && response.resolved.length > 0) {
      await this.applyRemoteEntries(response.resolved);
    }

    const remaining = await db.changeLog.count();
    syncStatusStore.update({ pendingCount: remaining });
  }

  // ─── Pull ──────────────────────────────────────────────────────────────────

  /**
   * Pull remote changes since last sync timestamp and apply locally.
   * Retries with exponential backoff on failure.
   */
  private async pull(): Promise<void> {
    const lastSync = await this.getLastSyncTimestamp();
    const sinceParam = lastSync ? `?since=${encodeURIComponent(lastSync)}` : '';
    const token = await this.getToken();

    const response = await retryWithBackoff<PullResponse>(
      async () => {
        const res = await fetch(`${API_BASE_URL}/sync/pull${sinceParam}`, {
          method: 'GET',
          headers: {
            Authorization: `Bearer ${token}`,
          },
          signal: this.abortController?.signal,
        });

        if (!res.ok) {
          throw new Error(`Pull failed: ${res.status} ${res.statusText}`);
        }

        return res.json() as Promise<PullResponse>;
      },
      {
        maxAttempts: 5,
        baseDelayMs: 1000,
        signal: this.abortController?.signal,
        onRetry: (attempt) => {
          syncStatusStore.update({ retryAttempt: attempt });
        },
      },
    );

    // Apply remote entries to local Dexie tables
    if (response.events.length > 0) {
      await this.applyRemoteEntries(response.events);
    }

    // Update last sync timestamp
    await this.setLastSyncTimestamp(response.serverTimestamp);
  }

  // ─── Field-Level Merge Application ─────────────────────────────────────────

  /**
   * Apply remote change log entries to local Dexie tables using field-level merge.
   *
   * Each entry targets a specific entity+field, so we can apply individual
   * field updates without overwriting unrelated local fields.
   */
  private async applyRemoteEntries(entries: RemoteChangeLogEntry[]): Promise<void> {
    // Group entries by entity for efficient batch processing
    const byEntity = new Map<string, RemoteChangeLogEntry[]>();
    for (const entry of entries) {
      const key = `${entry.entityType}:${entry.entityId}`;
      const group = byEntity.get(key) ?? [];
      group.push(entry);
      byEntity.set(key, group);
    }

    await db.transaction('rw', [db.goals, db.dailyTodos, db.habitTracks], async () => {
      for (const [key, entityEntries] of byEntity) {
        const [entityType, entityId] = key.split(':');

        // Build a patch object from field-level entries
        const patch: Record<string, unknown> = {};
        let isDelete = false;

        for (const entry of entityEntries) {
          if (entry.value === null && entry.field === '__entity__') {
            // Entity-level delete
            isDelete = true;
            break;
          }
          patch[entry.field] = entry.value !== null ? JSON.parse(entry.value) : null;
        }

        if (isDelete) {
          await this.deleteEntity(entityType, entityId);
        } else {
          await this.upsertEntity(entityType, entityId, patch);
        }
      }
    });
  }

  /**
   * Delete an entity from the appropriate Dexie table.
   */
  private async deleteEntity(entityType: string, entityId: string): Promise<void> {
    switch (entityType) {
      case 'goal':
        await db.goals.delete(entityId);
        break;
      case 'daily_todo':
        await db.dailyTodos.delete(entityId);
        break;
      case 'habit_track':
        await db.habitTracks.delete(entityId);
        break;
    }
  }

  /**
   * Upsert an entity in the appropriate Dexie table with field-level merge.
   * If the entity exists, only the provided fields are updated.
   * If it doesn't exist, the patch is used to create a new entry.
   */
  private async upsertEntity(
    entityType: string,
    entityId: string,
    patch: Record<string, unknown>,
  ): Promise<void> {
    switch (entityType) {
      case 'goal': {
        const existing = await db.goals.get(entityId);
        if (existing) {
          await db.goals.put({ ...existing, ...patch });
        } else {
          await db.goals.put({ id: entityId, ...patch } as never);
        }
        break;
      }
      case 'daily_todo': {
        const existing = await db.dailyTodos.get(entityId);
        if (existing) {
          await db.dailyTodos.put({ ...existing, ...patch });
        } else {
          await db.dailyTodos.put({ id: entityId, ...patch } as never);
        }
        break;
      }
      case 'habit_track': {
        const existing = await db.habitTracks.get(entityId);
        if (existing) {
          await db.habitTracks.put({ ...existing, ...patch });
        } else {
          await db.habitTracks.put({ id: entityId, ...patch } as never);
        }
        break;
      }
    }
  }

  // ─── Sync State Persistence ────────────────────────────────────────────────

  private async getLastSyncTimestamp(): Promise<string | null> {
    const state = await db.syncState.get(SYNC_STATE_KEY);
    return state?.lastSyncTimestamp ?? null;
  }

  private async setLastSyncTimestamp(timestamp: string): Promise<void> {
    await db.syncState.put({ key: SYNC_STATE_KEY, lastSyncTimestamp: timestamp });
  }

  // ─── Service Worker Background Sync ────────────────────────────────────────

  /**
   * Register a background sync event via Service Worker API.
   * When the browser regains connectivity, the Service Worker can
   * re-trigger sync without the page being active.
   */
  private async registerBackgroundSync(): Promise<void> {
    if (!('serviceWorker' in navigator)) return;

    try {
      const registration = await navigator.serviceWorker.ready;
      // Background Sync API (requires service worker)
      if ('sync' in registration) {
        await (registration.sync as SyncManager).register(BACKGROUND_SYNC_TAG);
      }
    } catch {
      // Background sync registration is best-effort
    }
  }

  // ─── Event Handlers ────────────────────────────────────────────────────────

  private readonly handleOnline = (): void => {
    syncStatusStore.update({ state: 'idle' });
    void this.syncNow();
  };

  private readonly handleOffline = (): void => {
    syncStatusStore.update({ state: 'offline' });
  };

  // ─── Scheduling ────────────────────────────────────────────────────────────

  private scheduleSync(): void {
    this.intervalId = setInterval(() => {
      void this.syncNow();
    }, this.syncIntervalMs);
  }
}

// ─── Service Worker Sync Handler ─────────────────────────────────────────────

/**
 * Utility to handle the 'sync' event inside a Service Worker.
 * Import this in your service-worker.ts file:
 *
 *   import { handleBackgroundSync } from '../data/sync/SyncEngine';
 *
 *   self.addEventListener('sync', (event) => {
 *     if (event.tag === 'dopashift-sync') {
 *       event.waitUntil(handleBackgroundSync(getToken));
 *     }
 *   });
 */
export async function handleBackgroundSync(
  getToken: () => Promise<string>,
): Promise<void> {
  const engine = new SyncEngine(getToken);
  await engine.syncNow();
}

// ─── SyncManager type declaration ────────────────────────────────────────────

/**
 * Augment ServiceWorkerRegistration with Background Sync API.
 * This API is available in Chromium browsers.
 */
interface SyncManager {
  register(tag: string): Promise<void>;
}

declare global {
  interface ServiceWorkerRegistration {
    readonly sync: SyncManager;
  }
}
