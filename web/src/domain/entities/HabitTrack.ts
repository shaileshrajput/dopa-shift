/** Domain entity representing a 30-day habit track. */
export interface HabitTrack {
  id: string;
  goalId: string;
  userId: string;
  startDate: string;
  currentDay: number; // 1-30
  isFinished: boolean;
  createdAt: string;
  updatedAt: string;
}

export type CheckpointStatus = 'PENDING' | 'COMPLETED' | 'MISSED';

export interface HabitCheckpoint {
  id: string;
  habitTrackId: string;
  dayNumber: number; // 1-30
  description: string;
  status: CheckpointStatus;
  completedAt: string | null;
}
