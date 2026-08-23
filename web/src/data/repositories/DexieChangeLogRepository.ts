import type { ChangeLogEntry } from '@domain/entities/ChangeLog';
import type { ChangeLogRepository } from '@domain/interfaces/ChangeLogRepository';
import { db } from '../db';

/**
 * Dexie/IndexedDB implementation of ChangeLogRepository.
 * Uses compound index [userId+timestamp] for efficient since-timestamp queries,
 * and entityId index for entity-scoped lookups.
 */
export class DexieChangeLogRepository implements ChangeLogRepository {
  async append(entry: ChangeLogEntry): Promise<void> {
    await db.changeLog.put({
      id: entry.id,
      entityId: entry.entityId,
      entityType: entry.entityType,
      field: entry.field,
      value: entry.value,
      timestamp: entry.timestamp,
      deviceId: entry.deviceId,
      userId: entry.userId,
    });
  }

  async findSince(
    userId: string,
    since: string,
  ): Promise<ChangeLogEntry[]> {
    // Use compound index [userId+timestamp]; Dexie compound key range
    const records = await db.changeLog
      .where('[userId+timestamp]')
      .between([userId, since], [userId, '\uffff'], true, true)
      .toArray();
    // Filter out the exact 'since' timestamp (exclusive lower bound)
    return records
      .filter((r) => r.timestamp > since)
      .map((r) => this.toDomain(r));
  }

  async findByEntityId(entityId: string): Promise<ChangeLogEntry[]> {
    const records = await db.changeLog
      .where('entityId')
      .equals(entityId)
      .toArray();
    return records.map((r) => this.toDomain(r));
  }

  private toDomain(record: {
    id: string;
    entityId: string;
    entityType: string;
    field: string;
    value: string | null;
    timestamp: string;
    deviceId: string;
    userId: string;
  }): ChangeLogEntry {
    return {
      id: record.id,
      entityId: record.entityId,
      entityType: record.entityType,
      field: record.field,
      value: record.value,
      timestamp: record.timestamp,
      deviceId: record.deviceId,
      userId: record.userId,
    };
  }
}
