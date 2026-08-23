/** Domain entity representing a daily to-do item. */
export interface DailyTodo {
  id: string;
  userId: string;
  text: string;
  dueDateTime: string | null;
  isCompleted: boolean;
  dayDate: string; // ISO date (YYYY-MM-DD)
  createdAt: string;
  updatedAt: string;
}
