/** Domain entity tracking the last successful sync timestamp. */
export interface SyncState {
  key: string;
  lastSyncTimestamp: string;
}
