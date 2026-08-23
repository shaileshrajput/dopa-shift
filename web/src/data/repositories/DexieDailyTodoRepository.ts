import type { DailyTodo } from '@domain/entities/DailyTodo';
import type { DailyTodoRepository } from '@domain/interfaces/DailyTodoRepository';
import { db } from '../db';

/**
 * Dexie/IndexedDB implementation of DailyTodoRepository.
 * Uses compound index [userId+dayDate] for efficient date-scoped queries.
 */
export class DexieDailyTodoRepository implements DailyTodoRepository {
  async findById(id: string): Promise<DailyTodo | undefined> {
    const record = await db.dailyTodos.get(id);
    if (!record) return undefined;
    return this.toDomain(record);
  }

  async findByUserIdAndDate(
    userId: string,
    dayDate: string,
  ): Promise<DailyTodo[]> {
    const records = await db.dailyTodos
      .where('[userId+dayDate]')
      .equals([userId, dayDate])
      .toArray();
    return records.map((r) => this.toDomain(r));
  }

  async countByUserIdAndDate(
    userId: string,
    dayDate: string,
  ): Promise<number> {
    return db.dailyTodos
      .where('[userId+dayDate]')
      .equals([userId, dayDate])
      .count();
  }

  async save(item: DailyTodo): Promise<DailyTodo> {
    await db.dailyTodos.put({
      id: item.id,
      userId: item.userId,
      text: item.text,
      dueDateTime: item.dueDateTime,
      isCompleted: item.isCompleted,
      dayDate: item.dayDate,
      createdAt: item.createdAt,
      updatedAt: item.updatedAt,
    });
    return item;
  }

  async delete(id: string): Promise<void> {
    await db.dailyTodos.delete(id);
  }

  private toDomain(record: {
    id: string;
    userId: string;
    text: string;
    dueDateTime: string | null;
    isCompleted: boolean;
    dayDate: string;
    createdAt: string;
    updatedAt: string;
  }): DailyTodo {
    return {
      id: record.id,
      userId: record.userId,
      text: record.text,
      dueDateTime: record.dueDateTime,
      isCompleted: record.isCompleted,
      dayDate: record.dayDate,
      createdAt: record.createdAt,
      updatedAt: record.updatedAt,
    };
  }
}
