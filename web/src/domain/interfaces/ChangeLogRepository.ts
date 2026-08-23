import type { ChangeLogEntry } from '../entities/ChangeLog';

/** Port interface for change log persistence (sync engine). */
export interface ChangeLogRepository {
  append(entry: ChangeLogEntry): Promise<void>;
  findSince(userId: string, since: string): Promise<ChangeLogEntry[]>;
  findByEntityId(entityId: string): Promise<ChangeLogEntry[]>;
}
