/**
 * Daily Tasks page — full CRUD for DailyTodoItems with date browsing,
 * completion toggling, inline editing, deletion, and pending/completed counts.
 *
 * Optimistic UI: all writes go directly to Dexie (local IndexedDB) for <200ms
 * perceived latency, matching the offline-first architecture.
 *
 * Validates: Requirements 4.1, 4.2, 4.3
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { db } from '@data/db';
import { useDailyTodos } from '@data/stores';
import type { LocalDailyTodo } from '@data/db';
import './TasksPage.css';

// ─── Constants ───────────────────────────────────────────────────────────────

const STUB_USER_ID = 'current-user'; // Placeholder until auth context is wired
const MAX_TASK_LENGTH = 500;
const MAX_TASKS_PER_DAY = 100;

/** Get today's date as ISO string (YYYY-MM-DD). */
function getTodayDate(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Shift a date string by N days, returning ISO YYYY-MM-DD. */
function shiftDate(isoDate: string, days: number): string {
  const d = new Date(isoDate + 'T00:00:00');
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Format an ISO date for display, locale-aware. */
function formatDateLabel(isoDate: string, locale: string): string {
  const date = new Date(isoDate + 'T00:00:00');
  return date.toLocaleDateString(locale, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });
}

// ─── Task Item Component ─────────────────────────────────────────────────────

interface TaskItemProps {
  todo: LocalDailyTodo;
  onToggle: (id: string, completed: boolean) => void;
  onEdit: (id: string, newText: string) => void;
  onDelete: (id: string) => void;
}

function TaskItem({ todo, onToggle, onEdit, onDelete }: TaskItemProps) {
  const { t } = useTranslation();
  const [editing, setEditing] = useState(false);
  const [editText, setEditText] = useState(todo.text);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (editing && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [editing]);

  const handleSaveEdit = useCallback(() => {
    const trimmed = editText.trim();
    if (trimmed && trimmed.length <= MAX_TASK_LENGTH && trimmed !== todo.text) {
      onEdit(todo.id, trimmed);
    }
    setEditing(false);
  }, [editText, todo.id, todo.text, onEdit]);

  const handleCancelEdit = useCallback(() => {
    setEditText(todo.text);
    setEditing(false);
  }, [todo.text]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter') {
        handleSaveEdit();
      } else if (e.key === 'Escape') {
        handleCancelEdit();
      }
    },
    [handleSaveEdit, handleCancelEdit],
  );

  if (editing) {
    return (
      <li className="task-item">
        <input
          ref={inputRef}
          type="text"
          className="task-item__edit-input"
          value={editText}
          onChange={(e) => setEditText(e.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={handleSaveEdit}
          maxLength={MAX_TASK_LENGTH}
          aria-label={t('tasks.editTask')}
        />
        <div className="task-item__edit-actions">
          <button
            type="button"
            className="task-item__action-btn"
            onClick={handleSaveEdit}
            aria-label={t('common.save')}
          >
            ✓
          </button>
          <button
            type="button"
            className="task-item__action-btn"
            onClick={handleCancelEdit}
            aria-label={t('common.cancel')}
          >
            ✕
          </button>
        </div>
      </li>
    );
  }

  return (
    <li className="task-item">
      <input
        type="checkbox"
        className="task-item__checkbox"
        checked={todo.isCompleted}
        onChange={() => onToggle(todo.id, !todo.isCompleted)}
        aria-label={
          todo.isCompleted
            ? t('tasks.markIncomplete')
            : t('tasks.markComplete')
        }
      />
      <span
        className={`task-item__text ${todo.isCompleted ? 'task-item__text--completed' : ''}`}
      >
        {todo.text}
      </span>
      <div className="task-item__actions">
        <button
          type="button"
          className="task-item__action-btn"
          onClick={() => {
            setEditText(todo.text);
            setEditing(true);
          }}
          aria-label={t('common.edit')}
        >
          ✎
        </button>
        <button
          type="button"
          className="task-item__action-btn task-item__action-btn--delete"
          onClick={() => onDelete(todo.id)}
          aria-label={t('common.delete')}
        >
          ✕
        </button>
      </div>
    </li>
  );
}

// ─── Main Tasks Page ─────────────────────────────────────────────────────────

export default function TasksPage() {
  const { t, i18n } = useTranslation();
  const [selectedDate, setSelectedDate] = useState(getTodayDate);

  // Reactive subscription to todos for the selected date
  const { data: todos } = useDailyTodos(STUB_USER_ID, selectedDate);

  const todayDate = getTodayDate();
  const isToday = selectedDate === todayDate;

  // Derived counts
  const allTodos = todos ?? [];
  const pendingCount = allTodos.filter((t) => !t.isCompleted).length;
  const completedCount = allTodos.filter((t) => t.isCompleted).length;

  // ─── Add Task ──────────────────────────────────────────────────────────────

  const [addText, setAddText] = useState('');
  const [addError, setAddError] = useState('');
  const addInputRef = useRef<HTMLInputElement>(null);

  const handleAddTask = useCallback(async () => {
    const text = addText.trim();
    if (!text) return;

    // Validate text length
    if (text.length > MAX_TASK_LENGTH) {
      setAddError(t('tasks.maxLength'));
      return;
    }

    // Validate daily limit
    if (allTodos.length >= MAX_TASKS_PER_DAY) {
      setAddError(t('tasks.dailyLimit'));
      return;
    }

    setAddError('');

    const now = new Date().toISOString();
    const newTodo: LocalDailyTodo = {
      id: crypto.randomUUID(),
      userId: STUB_USER_ID,
      text,
      dueDateTime: null,
      isCompleted: false,
      dayDate: selectedDate,
      createdAt: now,
      updatedAt: now,
    };

    // Optimistic: write directly to Dexie (<200ms)
    await db.dailyTodos.put(newTodo);
    setAddText('');
    addInputRef.current?.focus();
  }, [addText, allTodos.length, selectedDate, t]);

  // ─── Toggle Completion ─────────────────────────────────────────────────────

  const handleToggle = useCallback(async (id: string, completed: boolean) => {
    await db.dailyTodos.update(id, {
      isCompleted: completed,
      updatedAt: new Date().toISOString(),
    });
  }, []);

  // ─── Edit Task ─────────────────────────────────────────────────────────────

  const handleEdit = useCallback(async (id: string, newText: string) => {
    await db.dailyTodos.update(id, {
      text: newText,
      updatedAt: new Date().toISOString(),
    });
  }, []);

  // ─── Delete Task ───────────────────────────────────────────────────────────

  const handleDelete = useCallback(async (id: string) => {
    await db.dailyTodos.delete(id);
  }, []);

  // ─── Date Navigation ───────────────────────────────────────────────────────

  const handlePrevDay = useCallback(() => {
    setSelectedDate((d) => shiftDate(d, -1));
  }, []);

  const handleNextDay = useCallback(() => {
    setSelectedDate((d) => shiftDate(d, 1));
  }, []);

  const handleGoToToday = useCallback(() => {
    setSelectedDate(getTodayDate());
  }, []);

  // ─── Render ────────────────────────────────────────────────────────────────

  return (
    <main className="tasks-page">
      {/* Header */}
      <header className="tasks-page__header">
        <h1 className="tasks-page__title">{t('tasks.title')}</h1>

        {/* Date Selector */}
        <nav className="tasks-date-selector" aria-label={t('tasks.dueDate')}>
          <button
            type="button"
            className="tasks-date-selector__btn"
            onClick={handlePrevDay}
            aria-label={t('common.back')}
          >
            ‹
          </button>
          <span className="tasks-date-selector__label">
            {formatDateLabel(selectedDate, i18n.language)}
          </span>
          <button
            type="button"
            className="tasks-date-selector__btn"
            onClick={handleNextDay}
            aria-label={t('common.next')}
          >
            ›
          </button>
          {!isToday && (
            <button
              type="button"
              className="tasks-date-selector__today"
              onClick={handleGoToToday}
            >
              {t('tasks.today')}
            </button>
          )}
        </nav>
      </header>

      {/* Counts */}
      <div className="tasks-counts" aria-label={t('tasks.allTasks')}>
        <div className="tasks-counts__item">
          <span className="tasks-counts__label">{t('tasks.pending')}</span>
          <span className="tasks-counts__value">{pendingCount}</span>
        </div>
        <div className="tasks-counts__item">
          <span className="tasks-counts__label">{t('tasks.completed')}</span>
          <span className="tasks-counts__value tasks-counts__value--completed">
            {completedCount}
          </span>
        </div>
      </div>

      {/* Add Task Form */}
      <form
        className="tasks-add-form"
        onSubmit={(e) => {
          e.preventDefault();
          handleAddTask();
        }}
      >
        <input
          ref={addInputRef}
          type="text"
          className={`tasks-add-form__input ${addError ? 'tasks-add-form__input--error' : ''}`}
          placeholder={t('tasks.taskPlaceholder')}
          value={addText}
          onChange={(e) => {
            setAddText(e.target.value);
            if (addError) setAddError('');
          }}
          maxLength={MAX_TASK_LENGTH}
          aria-label={t('tasks.addTask')}
        />
        <button
          type="submit"
          className="tasks-add-form__btn"
          disabled={!addText.trim()}
        >
          {t('tasks.addTask')}
        </button>
      </form>
      {addError && (
        <span className="tasks-add-form__error" role="alert">
          {addError}
        </span>
      )}

      {/* Task List */}
      {allTodos.length > 0 ? (
        <ul className="tasks-list" aria-label={t('tasks.todaysTasks')}>
          {allTodos.map((todo) => (
            <TaskItem
              key={todo.id}
              todo={todo}
              onToggle={handleToggle}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />
          ))}
        </ul>
      ) : (
        <div className="tasks-empty" role="status">
          <p className="tasks-empty__text">{t('tasks.noTasks')}</p>
        </div>
      )}
    </main>
  );
}
