import type { DailyTodo } from '../entities/DailyTodo';

/** Port interface for daily to-do item persistence. */
export interface DailyTodoRepository {
  findById(id: string): Promise<DailyTodo | undefined>;
  findByUserIdAndDate(userId: string, dayDate: string): Promise<DailyTodo[]>;
  countByUserIdAndDate(userId: string, dayDate: string): Promise<number>;
  save(item: DailyTodo): Promise<DailyTodo>;
  delete(id: string): Promise<void>;
}
