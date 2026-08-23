import type { HabitTrack } from '@domain/entities/HabitTrack';
import type { HabitTrackRepository } from '@domain/interfaces/HabitTrackRepository';
import { db } from '../db';

/**
 * Dexie/IndexedDB implementation of HabitTrackRepository.
 * Uses goalId index for fast goal-scoped queries.
 */
export class DexieHabitTrackRepository implements HabitTrackRepository {
  async findById(id: string): Promise<HabitTrack | undefined> {
    const record = await db.habitTracks.get(id);
    if (!record) return undefined;
    return this.toDomain(record);
  }

  async findActiveByGoalId(goalId: string): Promise<HabitTrack[]> {
    const records = await db.habitTracks
      .where('goalId')
      .equals(goalId)
      .filter((t) => !t.isFinished)
      .toArray();
    return records.map((r) => this.toDomain(r));
  }

  async findActiveByUserId(userId: string): Promise<HabitTrack[]> {
    const records = await db.habitTracks
      .where('userId')
      .equals(userId)
      .filter((t) => !t.isFinished)
      .toArray();
    return records.map((r) => this.toDomain(r));
  }

  async save(track: HabitTrack): Promise<HabitTrack> {
    await db.habitTracks.put({
      id: track.id,
      goalId: track.goalId,
      userId: track.userId,
      startDate: track.startDate,
      currentDay: track.currentDay,
      isFinished: track.isFinished,
      createdAt: track.createdAt,
      updatedAt: track.updatedAt,
    });
    return track;
  }

  private toDomain(record: {
    id: string;
    goalId: string;
    userId: string;
    startDate: string;
    currentDay: number;
    isFinished: boolean;
    createdAt: string;
    updatedAt: string;
  }): HabitTrack {
    return {
      id: record.id,
      goalId: record.goalId,
      userId: record.userId,
      startDate: record.startDate,
      currentDay: record.currentDay,
      isFinished: record.isFinished,
      createdAt: record.createdAt,
      updatedAt: record.updatedAt,
    };
  }
}
