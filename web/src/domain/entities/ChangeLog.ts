/** Domain entity representing a change log entry for sync. */
export interface ChangeLogEntry {
  id: string;
  entityId: string;
  entityType: string;
  field: string;
  value: string | null; // JSON-encoded field value; null = deletion
  timestamp: string;
  deviceId: string;
  userId: string;
}
