/**
 * Domain entity representing a checklist item scoped to a goal.
 * Distinct from DailyTodoItem — a GoalChecklistItem is always associated
 * with exactly one GoalProfile.
 *
 * Validates: Requirement 1.3
 */
export interface GoalChecklistItem {
  id: string;
  goalId: string;
  userId: string;
  text: string; // max 200 chars
  isCompleted: boolean;
  createdAt: string;
  updatedAt: string;
}
