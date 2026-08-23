/**
 * Dashboard page — aggregates goals, todos, habits, efficiency scores,
 * and activity feed into a single scrollable view with quick-action controls.
 *
 * Validates: Requirements 20.1–20.15
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { db } from '@data/db';
import { useLiveQuery } from '@data/stores';
import type { LocalGoal, LocalDailyTodo, LocalHabitTrack } from '@data/db';
import { analyticsApi, type ActivityFeedItem, type EfficiencyScore } from '@data/api/endpoints';
import './DashboardPage.css';

// ─── Constants ───────────────────────────────────────────────────────────────

const ACTIVITY_PAGE_SIZE = 20;
const STUB_USER_ID = 'current-user'; // Placeholder until auth context is wired

/** Get today's date as ISO string (YYYY-MM-DD). */
function getTodayDate(): string {
  return new Date().toISOString().slice(0, 10);
}

// ─── Efficiency Score Ring ───────────────────────────────────────────────────

interface EfficiencyRingProps {
  score: number | null;
  trend: 'improving' | 'declining' | 'stable' | 'insufficient';
}

function EfficiencyRing({ score, trend }: EfficiencyRingProps) {
  const { t } = useTranslation();
  const radius = 34;
  const circumference = 2 * Math.PI * radius;
  const percent = score ?? 0;
  const offset = circumference - (percent / 100) * circumference;

  return (
    <div className="efficiency-ring" aria-label={t('dashboard.todaysScore')}>
      <div className="efficiency-ring__circle">
        <svg className="efficiency-ring__svg" viewBox="0 0 80 80" aria-hidden="true">
          <circle className="efficiency-ring__track" cx="40" cy="40" r={radius} />
          <circle
            className="efficiency-ring__fill"
            cx="40"
            cy="40"
            r={radius}
            strokeDasharray={circumference}
            strokeDashoffset={score !== null ? offset : circumference}
          />
        </svg>
        <span className="efficiency-ring__value">
          {score !== null ? `${score}%` : t('dashboard.noScoreData')}
        </span>
      </div>
      <span
        className={`efficiency-ring__trend efficiency-ring__trend--${trend === 'insufficient' ? 'stable' : trend}`}
      >
        {trend === 'improving' && t('dashboard.trendImproving')}
        {trend === 'declining' && t('dashboard.trendDeclining')}
        {trend === 'stable' && t('dashboard.trendStable')}
        {trend === 'insufficient' && t('dashboard.notEnoughData')}
        {trend !== 'insufficient' && ` ${t('dashboard.comparedToAvg')}`}
      </span>
    </div>
  );
}

// ─── Summary Card ────────────────────────────────────────────────────────────

interface SummaryCardProps {
  label: string;
  value: string | number;
}

function SummaryCard({ label, value }: SummaryCardProps) {
  return (
    <div className="summary-card">
      <span className="summary-card__label">{label}</span>
      <span className="summary-card__value">{value}</span>
    </div>
  );
}

// ─── Goal Card ───────────────────────────────────────────────────────────────

interface GoalCardProps {
  goal: LocalGoal;
}

function GoalCard({ goal }: GoalCardProps) {
  // For now, goal progress is based on keywords count as a proxy;
  // full Goal_Checklist_Item progress requires checklist data in Dexie
  // which will be wired in task 17.3. Displaying keywords and category.
  return (
    <div className="goal-card">
      <div className="goal-card__header">
        <span className="goal-card__name">{goal.name}</span>
        <span className="goal-card__category">{goal.category}</span>
      </div>
      <div className="goal-card__keywords">
        {goal.keywords.slice(0, 3).map((kw) => (
          <span key={kw} className="goal-card__keyword">
            {kw}
          </span>
        ))}
        {goal.keywords.length > 3 && (
          <span className="goal-card__keyword">+{goal.keywords.length - 3}</span>
        )}
      </div>
    </div>
  );
}

// ─── Todo Item ───────────────────────────────────────────────────────────────

interface TodoItemProps {
  todo: LocalDailyTodo;
  onToggle: (id: string, completed: boolean) => void;
}

function TodoItem({ todo, onToggle }: TodoItemProps) {
  return (
    <li className="todo-item">
      <input
        type="checkbox"
        className="todo-item__checkbox"
        checked={todo.isCompleted}
        onChange={() => onToggle(todo.id, !todo.isCompleted)}
        aria-label={todo.text}
      />
      <span
        className={`todo-item__text ${todo.isCompleted ? 'todo-item__text--completed' : ''}`}
      >
        {todo.text}
      </span>
    </li>
  );
}

// ─── Activity Feed Item ──────────────────────────────────────────────────────

function getActivityIcon(type: string): { className: string; label: string } {
  switch (type) {
    case 'TODO_COMPLETED':
      return { className: 'activity-item__icon--todo', label: '✓' };
    case 'GOAL_CREATED':
    case 'GOAL_DELETED':
      return { className: 'activity-item__icon--goal', label: '◎' };
    case 'HABIT_COMPLETED':
      return { className: 'activity-item__icon--habit', label: '★' };
    case 'CHECKLIST_UPDATED':
      return { className: 'activity-item__icon--checklist', label: '☰' };
    default:
      return { className: 'activity-item__icon--goal', label: '•' };
  }
}

function formatRelativeTime(timestamp: string, t: (key: string, opts?: Record<string, unknown>) => string): string {
  const now = Date.now();
  const then = new Date(timestamp).getTime();
  const diffMs = now - then;
  const diffMin = Math.floor(diffMs / 60000);
  const diffHour = Math.floor(diffMs / 3600000);
  const diffDay = Math.floor(diffMs / 86400000);

  if (diffMin < 1) return t('dashboard.justNow');
  if (diffMin < 60) return t('dashboard.minutesAgo', { count: diffMin });
  if (diffHour < 24) return t('dashboard.hoursAgo', { count: diffHour });
  return t('dashboard.daysAgo', { count: diffDay });
}

interface ActivityItemProps {
  item: ActivityFeedItem;
}

function ActivityItem({ item }: ActivityItemProps) {
  const { t } = useTranslation();
  const { className, label } = getActivityIcon(item.type);

  return (
    <li className="activity-item">
      <span className={`activity-item__icon ${className}`} aria-hidden="true">
        {label}
      </span>
      <div className="activity-item__content">
        <span className="activity-item__description">{item.description}</span>
        <span className="activity-item__time">{formatRelativeTime(item.timestamp, t)}</span>
      </div>
    </li>
  );
}

// ─── Main Dashboard Page ─────────────────────────────────────────────────────

export default function DashboardPage() {
  const { t } = useTranslation();
  const today = getTodayDate();

  // ─── Local reactive data via liveQuery ─────────────────────────────────────

  const { data: goals } = useLiveQuery<LocalGoal[]>(
    () => db.goals.where('userId').equals(STUB_USER_ID).filter((g) => g.isActive).toArray(),
    [STUB_USER_ID],
    [],
  );

  const { data: todos } = useLiveQuery<LocalDailyTodo[]>(
    () => db.dailyTodos.where('[userId+dayDate]').equals([STUB_USER_ID, today]).toArray(),
    [STUB_USER_ID, today],
    [],
  );

  const { data: habits } = useLiveQuery<LocalHabitTrack[]>(
    () => db.habitTracks.where('userId').equals(STUB_USER_ID).filter((h) => !h.isFinished).toArray(),
    [STUB_USER_ID],
    [],
  );

  // ─── Efficiency score state ────────────────────────────────────────────────

  const [todayScore, setTodayScore] = useState<EfficiencyScore | null>(null);
  const [trend, setTrend] = useState<'improving' | 'declining' | 'stable' | 'insufficient'>('insufficient');

  useEffect(() => {
    let cancelled = false;

    async function loadScore() {
      try {
        // Fetch last 8 days of scores to compute trend (need 7-day avg + today)
        const eightDaysAgo = new Date();
        eightDaysAgo.setDate(eightDaysAgo.getDate() - 8);
        const from = eightDaysAgo.toISOString().slice(0, 10);
        const scores = await analyticsApi.getEfficiency(from, today);

        if (cancelled) return;

        const todayEntry = scores.find((s) => s.date === today) ?? null;
        setTodayScore(todayEntry);

        // Compute trend: compare today/most-recent vs 7-day avg
        const previousScores = scores.filter(
          (s) => s.scorePercent !== null && s.date !== today,
        );

        if (previousScores.length < 2) {
          setTrend('insufficient');
          return;
        }

        const avg =
          previousScores.reduce((sum, s) => sum + (s.scorePercent ?? 0), 0) /
          previousScores.length;

        const current = todayEntry?.scorePercent ?? previousScores[previousScores.length - 1]?.scorePercent ?? 0;

        if (current > avg + 2) {
          setTrend('improving');
        } else if (current < avg - 2) {
          setTrend('declining');
        } else {
          setTrend('stable');
        }
      } catch {
        // Offline or API error — trend stays as insufficient / no score
      }
    }

    loadScore();
    return () => { cancelled = true; };
  }, [today]);

  // ─── Activity Feed (paginated from API) ────────────────────────────────────

  const [activityItems, setActivityItems] = useState<ActivityFeedItem[]>([]);
  const [activityPage, setActivityPage] = useState(1);
  const [activityTotal, setActivityTotal] = useState(0);
  const [activityLoading, setActivityLoading] = useState(false);
  const initialLoadDone = useRef(false);

  const loadActivity = useCallback(async (page: number, append: boolean) => {
    setActivityLoading(true);
    try {
      const response = await analyticsApi.getActivityFeed(page, ACTIVITY_PAGE_SIZE);
      if (append) {
        setActivityItems((prev) => [...prev, ...response.data]);
      } else {
        setActivityItems(response.data);
      }
      setActivityTotal(response.total);
      setActivityPage(page);
    } catch {
      // Silently fail when offline — show whatever we have
    } finally {
      setActivityLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!initialLoadDone.current) {
      initialLoadDone.current = true;
      loadActivity(1, false);
    }
  }, [loadActivity]);

  const handleLoadMore = useCallback(() => {
    loadActivity(activityPage + 1, true);
  }, [activityPage, loadActivity]);

  const hasMoreActivity = activityItems.length < activityTotal;

  // ─── Quick-Add Todo ────────────────────────────────────────────────────────

  const [quickAddText, setQuickAddText] = useState('');

  const handleQuickAdd = useCallback(async () => {
    const text = quickAddText.trim();
    if (!text) return;

    const now = new Date().toISOString();
    const newTodo: LocalDailyTodo = {
      id: crypto.randomUUID(),
      userId: STUB_USER_ID,
      text,
      dueDateTime: null,
      isCompleted: false,
      dayDate: today,
      createdAt: now,
      updatedAt: now,
    };

    // Optimistic: write to local Dexie immediately (satisfies 200ms requirement)
    await db.dailyTodos.put(newTodo);
    setQuickAddText('');
  }, [quickAddText, today]);

  // ─── Toggle Todo Completion ────────────────────────────────────────────────

  const handleToggleTodo = useCallback(async (id: string, completed: boolean) => {
    // Optimistic local update
    await db.dailyTodos.update(id, {
      isCompleted: completed,
      updatedAt: new Date().toISOString(),
    });
  }, []);

  // ─── Derived values ────────────────────────────────────────────────────────

  const activeGoals = goals ?? [];
  const todayTodos = todos ?? [];
  const activeHabits = habits ?? [];
  const pendingCount = todayTodos.filter((t) => !t.isCompleted).length;

  const isEmpty =
    activeGoals.length === 0 && todayTodos.length === 0 && activeHabits.length === 0;

  // ─── Render ────────────────────────────────────────────────────────────────

  // Requirement 20.11: Empty state when user has zero of everything
  if (isEmpty) {
    return (
      <main className="dashboard">
        <div className="dashboard__empty-state" role="status">
          <h1 className="dashboard__empty-state-title">{t('dashboard.emptyStateTitle')}</h1>
          <nav className="dashboard__empty-state-actions" aria-label={t('dashboard.quickActions')}>
            <Link to="/goals" className="dashboard__empty-state-link">
              {t('dashboard.emptyStateGoal')}
            </Link>
            <Link to="/tasks" className="dashboard__empty-state-link">
              {t('dashboard.emptyStateTodo')}
            </Link>
            <Link to="/habits" className="dashboard__empty-state-link">
              {t('dashboard.emptyStateHabit')}
            </Link>
          </nav>
        </div>
      </main>
    );
  }

  return (
    <main className="dashboard">
      {/* ─── Header with Efficiency Score Ring (Req 20.15) ─── */}
      <header className="dashboard__header">
        <h1 className="dashboard__greeting">{t('dashboard.welcome')}</h1>
        <EfficiencyRing score={todayScore?.scorePercent ?? null} trend={trend} />
      </header>

      {/* ─── Summary Cards (Req 20.2) ─── */}
      <section className="dashboard__summary" aria-label={t('dashboard.todaysSummary')}>
        <SummaryCard label={t('dashboard.pendingTasks')} value={pendingCount} />
        <SummaryCard label={t('dashboard.activeGoals')} value={activeGoals.length} />
        <SummaryCard label={t('dashboard.habitsDue')} value={activeHabits.length} />
      </section>

      {/* ─── Goal Grid (Req 20.13) ─── */}
      {activeGoals.length > 0 && (
        <section aria-label={t('dashboard.activeGoals')}>
          <div className="dashboard__goals-header">
            <h2 className="dashboard__section-title">{t('dashboard.activeGoals')}</h2>
          </div>
          <div className="dashboard__goals-grid">
            {activeGoals.map((goal) => (
              <GoalCard key={goal.id} goal={goal} />
            ))}
          </div>
        </section>
      )}

      {/* ─── Today's Focus / Todo Quick-Actions (Req 20.4, 20.5, 20.14) ─── */}
      <section className="dashboard__todos" aria-label={t('dashboard.todaysFocus')}>
        <h2 className="dashboard__section-title">{t('dashboard.todaysFocus')}</h2>

        {/* Quick-add input */}
        <form
          className="todo-quick-add"
          onSubmit={(e) => {
            e.preventDefault();
            handleQuickAdd();
          }}
        >
          <input
            type="text"
            className="todo-quick-add__input"
            placeholder={t('dashboard.addTaskPlaceholder')}
            value={quickAddText}
            onChange={(e) => setQuickAddText(e.target.value)}
            maxLength={500}
            aria-label={t('dashboard.addTaskPlaceholder')}
          />
          <button
            type="submit"
            className="todo-quick-add__btn"
            disabled={!quickAddText.trim()}
          >
            {t('dashboard.addTask')}
          </button>
        </form>

        {/* Todo list */}
        {todayTodos.length > 0 ? (
          <ul className="todo-list" aria-label={t('tasks.todaysTasks')}>
            {todayTodos.map((todo) => (
              <TodoItem key={todo.id} todo={todo} onToggle={handleToggleTodo} />
            ))}
          </ul>
        ) : (
          <p className="dashboard__no-data">{t('dashboard.noTasks')}</p>
        )}
      </section>

      {/* ─── Activity Feed (Req 20.6, 20.10) ─── */}
      <section className="dashboard__activity" aria-label={t('dashboard.recentActivity')}>
        <h2 className="dashboard__section-title">{t('dashboard.recentActivity')}</h2>
        {activityItems.length > 0 ? (
          <>
            <ul className="activity-feed" aria-label={t('dashboard.recentActivity')}>
              {activityItems.map((item) => (
                <ActivityItem key={item.id} item={item} />
              ))}
            </ul>
            {hasMoreActivity && (
              <button
                type="button"
                className="dashboard__load-more"
                onClick={handleLoadMore}
                disabled={activityLoading}
              >
                {activityLoading ? t('common.loading') : t('dashboard.loadMore')}
              </button>
            )}
          </>
        ) : (
          <p className="dashboard__no-data">
            {activityLoading ? t('common.loading') : t('dashboard.noMoreActivity')}
          </p>
        )}
      </section>
    </main>
  );
}
