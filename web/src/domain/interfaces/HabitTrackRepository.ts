import type { HabitTrack } from '../entities/HabitTrack';

/** Port interface for habit track persistence. */
export interface HabitTrackRepository {
  findById(id: string): Promise<HabitTrack | undefined>;
  findActiveByGoalId(goalId: string): Promise<HabitTrack[]>;
  findActiveByUserId(userId: string): Promise<HabitTrack[]>;
  save(track: HabitTrack): Promise<HabitTrack>;
}
