import type { Goal } from '@domain/entities/Goal';
import type { GoalRepository } from '@domain/interfaces/GoalRepository';
import { db } from '../db';

/**
 * Dexie/IndexedDB implementation of GoalRepository.
 * Wraps the db.goals table and leverages indexes on userId and name.
 */
export class DexieGoalRepository implements GoalRepository {
  async findById(id: string): Promise<Goal | undefined> {
    const record = await db.goals.get(id);
    if (!record) return undefined;
    return this.toDomain(record);
  }

  async findByUserId(userId: string): Promise<Goal[]> {
    const records = await db.goals.where('userId').equals(userId).toArray();
    return records.map((r) => this.toDomain(r));
  }

  async findByUserIdAndName(
    userId: string,
    name: string,
  ): Promise<Goal | undefined> {
    const records = await db.goals
      .where('userId')
      .equals(userId)
      .filter((g) => g.name === name)
      .toArray();
    return records.length > 0 ? this.toDomain(records[0]) : undefined;
  }

  async save(goal: Goal): Promise<Goal> {
    await db.goals.put({
      id: goal.id,
      userId: goal.userId,
      name: goal.name,
      category: goal.category,
      keywords: goal.keywords,
      isActive: goal.isActive,
      createdAt: goal.createdAt,
      updatedAt: goal.updatedAt,
    });
    return goal;
  }

  async delete(id: string): Promise<void> {
    await db.goals.delete(id);
  }

  private toDomain(record: {
    id: string;
    userId: string;
    name: string;
    category: string;
    keywords: string[];
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
  }): Goal {
    return {
      id: record.id,
      userId: record.userId,
      name: record.name,
      category: record.category,
      keywords: record.keywords,
      isActive: record.isActive,
      createdAt: record.createdAt,
      updatedAt: record.updatedAt,
    };
  }
}
