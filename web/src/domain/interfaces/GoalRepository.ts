import type { Goal } from '../entities/Goal';

/** Port interface for goal persistence. */
export interface GoalRepository {
  findById(id: string): Promise<Goal | undefined>;
  findByUserId(userId: string): Promise<Goal[]>;
  findByUserIdAndName(userId: string, name: string): Promise<Goal | undefined>;
  save(goal: Goal): Promise<Goal>;
  delete(id: string): Promise<void>;
}
