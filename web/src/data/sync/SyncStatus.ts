import { BehaviorSubject, Observable } from 'rxjs';

/** Possible states of the sync engine. */
export type SyncState =
  | 'idle'
  | 'syncing'
  | 'error'
  | 'offline';

/** Detailed sync status exposed to the UI layer. */
export interface SyncStatus {
  state: SyncState;
  /** Number of change log entries pending push. */
  pendingCount: number;
  /** ISO timestamp of the last successful sync. */
  lastSyncAt: string | null;
  /** Human-readable error message if state is 'error'. */
  errorMessage: string | null;
  /** Current retry attempt number (0 when not retrying). */
  retryAttempt: number;
}

const INITIAL_STATUS: SyncStatus = {
  state: 'idle',
  pendingCount: 0,
  lastSyncAt: null,
  errorMessage: null,
  retryAttempt: 0,
};

/**
 * Observable sync status store.
 * UI components subscribe to status$ for reactive sync indicators.
 */
class SyncStatusStore {
  private readonly _status$ = new BehaviorSubject<SyncStatus>(INITIAL_STATUS);

  /** Observable stream of sync status updates. */
  get status$(): Observable<SyncStatus> {
    return this._status$.asObservable();
  }

  /** Current snapshot of sync status. */
  get current(): SyncStatus {
    return this._status$.getValue();
  }

  /** Update sync status (partial merge). */
  update(patch: Partial<SyncStatus>): void {
    this._status$.next({ ...this._status$.getValue(), ...patch });
  }

  /** Reset to initial idle state. */
  reset(): void {
    this._status$.next(INITIAL_STATUS);
  }
}

/** Singleton sync status store. */
export const syncStatusStore = new SyncStatusStore();
