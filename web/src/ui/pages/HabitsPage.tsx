/**
 * Habits page — full habit roadmap management with 30-day track visualization,
 * checkpoint completion, create dialog, and linked goal display.
 *
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7
 */

import { useState, useCallback, useMemo, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { db, type LocalHabitTrack, type LocalHabitCheckpoint, type LocalGoal } from '@data/db';
import { useLiveQuery } from '@data/stores';
import './HabitsPage.css';

// ─── Constants ───────────────────────────────────────────────────────────────

const STUB_USER_ID = 'current-user';
const TOTAL_DAYS = 30;
const MAX_DESCRIPTION_LENGTH = 200;

// ─── Types ───────────────────────────────────────────────────────────────────

type ViewMode = 'list' | 'detail';
type FilterMode = 'active' | 'finished' | 'all';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function getTodayDateISO(): string {
  return new Date().toISOString().split('T')[0];
}

function computeStreak(checkpoints: LocalHabitCheckpoint[], currentDay: number): number {
  let streak = 0;
  for (let day = currentDay - 1; day >= 1; day--) {
    const cp = checkpoints.find((c) => c.dayNumber === day);
    if (cp?.status === 'COMPLETED') {
      streak++;
    } else {
      break;
    }
  }
  return streak;
}

function getCheckpointDisplayStatus(
  dayNumber: number,
  currentDay: number,
  checkpoint: LocalHabitCheckpoint | undefined,
  isFinished: boolean,
): 'completed' | 'missed' | 'pending' | 'current' {
  if (checkpoint?.status === 'COMPLETED') return 'completed';
  if (checkpoint?.status === 'MISSED') return 'missed';
  if (dayNumber === currentDay && !isFinished) return 'current';
  if (dayNumber < currentDay) return 'missed';
  return 'pending';
}

// ─── Create Habit Track Dialog ───────────────────────────────────────────────

interface CreateDialogProps {
  goals: LocalGoal[];
  onSubmit: (goalId: string, descriptions: string[]) => void;
  onClose: () => void;
}

function CreateHabitDialog({ goals, onSubmit, onClose }: CreateDialogProps) {
  const { t } = useTranslation();
  const [selectedGoalId, setSelectedGoalId] = useState(goals[0]?.id ?? '');
  const [descriptions, setDescriptions] = useState<string[]>(
    Array.from({ length: TOTAL_DAYS }, (_, i) => `Day ${i + 1} micro-habit`),
  );
  const [editingDay, setEditingDay] = useState<number | null>(null);
  const [editText, setEditText] = useState('');
  const [error, setError] = useState('');

  const handleStartEdit = useCallback((day: number) => {
    setEditingDay(day);
    setEditText(descriptions[day - 1]);
  }, [descriptions]);

  const handleSaveEdit = useCallback(() => {
    if (editingDay === null) return;
    const trimmed = editText.trim();
    if (!trimmed) return;
    if (trimmed.length > MAX_DESCRIPTION_LENGTH) return;
    setDescriptions((prev) => {
      const next = [...prev];
      next[editingDay - 1] = trimmed;
      return next;
    });
    setEditingDay(null);
    setEditText('');
  }, [editingDay, editText]);

  const handleSubmit = useCallback(
    (e: FormEvent) => {
      e.preventDefault();
      if (!selectedGoalId) {
        setError(t('goals.noActiveGoals'));
        return;
      }
      onSubmit(selectedGoalId, descriptions);
    },
    [selectedGoalId, descriptions, onSubmit, t],
  );

  return (
    <div className="dialog-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <form
        className="dialog habits-dialog"
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
      >
        <h2 className="dialog__title">{t('habits.createHabit')}</h2>

        {error && <p className="dialog__error" role="alert">{error}</p>}

        <div className="dialog__field">
          <label className="dialog__label" htmlFor="habit-goal">
            {t('habits.linkedGoal')}
          </label>
          <select
            id="habit-goal"
            className="dialog__select"
            value={selectedGoalId}
            onChange={(e) => setSelectedGoalId(e.target.value)}
          >
            {goals.map((g) => (
              <option key={g.id} value={g.id}>{g.name}</option>
            ))}
          </select>
        </div>

        <div className="dialog__field">
          <label className="dialog__label">{t('habits.checkpoint')} (30 {t('habits.dayOf30', { day: '' }).replace('/ 30', '').trim()})</label>
          <div className="habits-dialog__checkpoints-list">
            {descriptions.map((desc, i) => (
              <div key={i} className="habits-dialog__checkpoint-row">
                <span className="habits-dialog__checkpoint-day">{i + 1}</span>
                {editingDay === i + 1 ? (
                  <div className="habits-dialog__checkpoint-edit">
                    <input
                      className="dialog__input"
                      type="text"
                      value={editText}
                      onChange={(e) => setEditText(e.target.value)}
                      maxLength={MAX_DESCRIPTION_LENGTH}
                      autoFocus
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleSaveEdit();
                        }
                        if (e.key === 'Escape') setEditingDay(null);
                      }}
                    />
                    <button
                      type="button"
                      className="dialog__btn dialog__btn--secondary"
                      onClick={handleSaveEdit}
                    >
                      {t('common.save')}
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    className="habits-dialog__checkpoint-text"
                    onClick={() => handleStartEdit(i + 1)}
                    title={t('common.edit')}
                  >
                    {desc}
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>

        <div className="dialog__actions">
          <button
            type="button"
            className="dialog__btn dialog__btn--secondary"
            onClick={onClose}
          >
            {t('common.cancel')}
          </button>
          <button
            type="submit"
            className="dialog__btn dialog__btn--primary"
            disabled={!selectedGoalId}
          >
            {t('common.create')}
          </button>
        </div>
      </form>
    </div>
  );
}

// ─── 30-Day Grid with Checkpoints ────────────────────────────────────────────

interface HabitGridProps {
  track: LocalHabitTrack;
  checkpoints: LocalHabitCheckpoint[];
  onDayClick?: (dayNumber: number) => void;
}

function HabitGrid({ track, checkpoints, onDayClick }: HabitGridProps) {
  const days = Array.from({ length: TOTAL_DAYS }, (_, i) => i + 1);

  return (
    <div className="habit-grid" role="img" aria-label="30-day habit progress">
      {days.map((day) => {
        const cp = checkpoints.find((c) => c.dayNumber === day);
        const status = getCheckpointDisplayStatus(day, track.currentDay, cp, track.isFinished);
        const isCurrent = status === 'current';
        const isClickable = isCurrent && onDayClick;

        return (
          <button
            key={day}
            type="button"
            className={`habit-grid__day habit-grid__day--${status === 'current' ? 'pending' : status}${isCurrent ? ' habit-grid__day--current' : ''}`}
            title={`Day ${day}: ${cp?.description ?? ''} (${status})`}
            onClick={isClickable ? () => onDayClick(day) : undefined}
            disabled={!isClickable}
            aria-label={`Day ${day} ${status}`}
          >
            {day}
          </button>
        );
      })}
    </div>
  );
}

// ─── Habit Track Card (List View) ────────────────────────────────────────────

interface HabitTrackCardProps {
  track: LocalHabitTrack;
  checkpoints: LocalHabitCheckpoint[];
  goalName: string;
  onClick: () => void;
}

function HabitTrackCard({ track, checkpoints, goalName, onClick }: HabitTrackCardProps) {
  const { t } = useTranslation();
  const completedCount = checkpoints.filter((c) => c.status === 'COMPLETED').length;
  const streak = computeStreak(checkpoints, track.currentDay);
  const completionPercent = Math.round((completedCount / TOTAL_DAYS) * 100);

  return (
    <button
      type="button"
      className="habit-card"
      onClick={onClick}
      aria-label={`${goalName} - ${t('habits.dayOf30', { day: track.currentDay })}`}
    >
      <div className="habit-card__header">
        <div className="habit-card__title-group">
          <span className="habit-card__goal-name">{goalName}</span>
          <span className="habit-card__day-label">
            {t('habits.dayOf30', { day: track.currentDay })}
          </span>
        </div>
        <span
          className={`habit-card__status habit-card__status--${track.isFinished ? 'finished' : 'active'}`}
        >
          {track.isFinished ? t('habits.finished') : t('habits.active')}
        </span>
      </div>

      <HabitGrid track={track} checkpoints={checkpoints} />

      <div className="habit-card__stats">
        <div className="habit-card__stat">
          <span className="habit-card__stat-value">{completionPercent}%</span>
          <span className="habit-card__stat-label">{t('habits.completionRate')}</span>
        </div>
        <div className="habit-card__stat">
          <span className="habit-card__stat-value">{streak}</span>
          <span className="habit-card__stat-label">{t('habits.streak')}</span>
        </div>
        <div className="habit-card__stat">
          <span className="habit-card__stat-value">{completedCount}/{TOTAL_DAYS}</span>
          <span className="habit-card__stat-label">{t('habits.completed')}</span>
        </div>
      </div>

      {/* Progress bar */}
      <div className="habit-card__progress-bar">
        <div
          className="habit-card__progress-fill"
          style={{ width: `${completionPercent}%` }}
        />
      </div>
    </button>
  );
}

// ─── Habit Track Detail View ─────────────────────────────────────────────────

interface HabitDetailProps {
  track: LocalHabitTrack;
  checkpoints: LocalHabitCheckpoint[];
  goalName: string;
  onBack: () => void;
  onCheckIn: (dayNumber: number) => void;
}

function HabitDetailView({ track, checkpoints, goalName, onBack, onCheckIn }: HabitDetailProps) {
  const { t } = useTranslation();
  const completedCount = checkpoints.filter((c) => c.status === 'COMPLETED').length;
  const missedCount = checkpoints.filter((c) => c.status === 'MISSED').length;
  const streak = computeStreak(checkpoints, track.currentDay);
  const currentCheckpoint = checkpoints.find((c) => c.dayNumber === track.currentDay);
  const isTodayCompleted = currentCheckpoint?.status === 'COMPLETED';

  return (
    <div className="habit-detail">
      <button type="button" className="habit-detail__back-btn" onClick={onBack}>
        ← {t('common.back')}
      </button>

      <div className="habit-detail__header">
        <div>
          <h2 className="habit-detail__title">{goalName}</h2>
          <span className="habit-detail__subtitle">
            {t('habits.dayOf30', { day: track.currentDay })}
          </span>
        </div>
        <span
          className={`habit-card__status habit-card__status--${track.isFinished ? 'finished' : 'active'}`}
        >
          {track.isFinished ? t('habits.finished') : t('habits.active')}
        </span>
      </div>

      {/* Today's habit card */}
      {!track.isFinished && (
        <div className="habit-detail__today-card">
          <h3 className="habit-detail__today-title">{t('habits.todaysHabit')}</h3>
          <p className="habit-detail__today-desc">
            {currentCheckpoint?.description ?? `Day ${track.currentDay} micro-habit`}
          </p>
          <button
            type="button"
            className={`habit-detail__checkin-btn${isTodayCompleted ? ' habit-detail__checkin-btn--done' : ''}`}
            onClick={() => onCheckIn(track.currentDay)}
            disabled={isTodayCompleted}
            aria-label={t('habits.markDone')}
          >
            {isTodayCompleted ? `✓ ${t('habits.habitCompleted')}` : t('habits.markDone')}
          </button>
        </div>
      )}

      {/* Stats row */}
      <div className="habit-detail__stats">
        <div className="habit-detail__stat-card">
          <span className="habit-detail__stat-value">{streak}</span>
          <span className="habit-detail__stat-label">
            {t('habits.streakDays', { count: streak })}
          </span>
        </div>
        <div className="habit-detail__stat-card">
          <span className="habit-detail__stat-value">{completedCount}</span>
          <span className="habit-detail__stat-label">{t('habits.completed')}</span>
        </div>
        <div className="habit-detail__stat-card">
          <span className="habit-detail__stat-value">{missedCount}</span>
          <span className="habit-detail__stat-label">{t('habits.missed')}</span>
        </div>
      </div>

      {/* 30-day grid */}
      <section className="habit-detail__grid-section">
        <h3 className="habit-detail__section-title">{t('habits.checkpoint')} Grid</h3>
        <HabitGrid
          track={track}
          checkpoints={checkpoints}
          onDayClick={!track.isFinished ? onCheckIn : undefined}
        />
      </section>

      {/* Checkpoint timeline */}
      <section className="habit-detail__timeline">
        <h3 className="habit-detail__section-title">Timeline</h3>
        <div className="habit-detail__timeline-list">
          {Array.from({ length: TOTAL_DAYS }, (_, i) => i + 1).map((day) => {
            const cp = checkpoints.find((c) => c.dayNumber === day);
            const status = getCheckpointDisplayStatus(day, track.currentDay, cp, track.isFinished);
            return (
              <div key={day} className={`habit-detail__timeline-item habit-detail__timeline-item--${status}`}>
                <span className="habit-detail__timeline-day">Day {day}</span>
                <span className="habit-detail__timeline-desc">
                  {cp?.description ?? '—'}
                </span>
                <span className={`habit-detail__timeline-status habit-detail__timeline-status--${status}`}>
                  {status === 'completed' && t('habits.completed')}
                  {status === 'missed' && t('habits.missed')}
                  {status === 'pending' && t('habits.pending')}
                  {status === 'current' && t('habits.todaysHabit')}
                </span>
              </div>
            );
          })}
        </div>
      </section>

      {/* Metadata */}
      <div className="habit-detail__meta">
        <span>{t('habits.linkedGoal')}: {goalName}</span>
        <span>{t('habits.startDate')}: {track.startDate}</span>
        <span>
          {t('habits.daysCompleted', { completed: completedCount, total: TOTAL_DAYS })}
        </span>
      </div>
    </div>
  );
}

// ─── Main Habits Page ────────────────────────────────────────────────────────

export default function HabitsPage() {
  const { t } = useTranslation();
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [selectedTrackId, setSelectedTrackId] = useState<string | null>(null);
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [filter, setFilter] = useState<FilterMode>('active');

  // ─── Reactive data subscriptions ───────────────────────────────────────────

  const { data: allTracks, loading: tracksLoading } = useLiveQuery<LocalHabitTrack[]>(
    () => db.habitTracks.where('userId').equals(STUB_USER_ID).toArray(),
    [STUB_USER_ID],
    [],
  );

  const { data: allCheckpoints } = useLiveQuery<LocalHabitCheckpoint[]>(
    () => db.habitCheckpoints.where('userId').equals(STUB_USER_ID).toArray(),
    [STUB_USER_ID],
    [],
  );

  const { data: goals } = useLiveQuery<LocalGoal[]>(
    () => db.goals.where('userId').equals(STUB_USER_ID).filter((g) => g.isActive).toArray(),
    [STUB_USER_ID],
    [],
  );

  const tracks = allTracks ?? [];
  const checkpoints = allCheckpoints ?? [];
  const activeGoals = goals ?? [];

  // ─── Filtered tracks ───────────────────────────────────────────────────────

  const filteredTracks = useMemo(() => {
    switch (filter) {
      case 'active':
        return tracks.filter((t) => !t.isFinished);
      case 'finished':
        return tracks.filter((t) => t.isFinished);
      default:
        return tracks;
    }
  }, [tracks, filter]);

  const selectedTrack = selectedTrackId
    ? tracks.find((t) => t.id === selectedTrackId) ?? null
    : null;

  const selectedCheckpoints = selectedTrack
    ? checkpoints.filter((c) => c.habitTrackId === selectedTrack.id)
    : [];

  // ─── Goal name resolver ────────────────────────────────────────────────────

  const getGoalName = useCallback(
    (goalId: string) => activeGoals.find((g) => g.id === goalId)?.name ?? '—',
    [activeGoals],
  );

  // ─── Handlers ──────────────────────────────────────────────────────────────

  const handleCreateTrack = useCallback(
    async (goalId: string, descriptions: string[]) => {
      const now = new Date().toISOString();
      const today = getTodayDateISO();
      const trackId = crypto.randomUUID();

      // Create the track (Req 6.1)
      await db.habitTracks.put({
        id: trackId,
        goalId,
        userId: STUB_USER_ID,
        startDate: today,
        currentDay: 1,
        isFinished: false,
        createdAt: now,
        updatedAt: now,
      });

      // Create 30 checkpoints (Req 6.1)
      const cpBatch: LocalHabitCheckpoint[] = descriptions.map((desc, i) => ({
        id: crypto.randomUUID(),
        habitTrackId: trackId,
        userId: STUB_USER_ID,
        dayNumber: i + 1,
        description: desc,
        status: 'PENDING' as const,
        completedAt: null,
        createdAt: now,
        updatedAt: now,
      }));

      await db.habitCheckpoints.bulkPut(cpBatch);
      setShowCreateDialog(false);
    },
    [],
  );

  const handleCheckIn = useCallback(
    async (dayNumber: number) => {
      if (!selectedTrack) return;

      const now = new Date().toISOString();
      const cp = checkpoints.find(
        (c) => c.habitTrackId === selectedTrack.id && c.dayNumber === dayNumber,
      );

      if (!cp || cp.status === 'COMPLETED') return;

      // Mark checkpoint as completed (Req 6.3)
      await db.habitCheckpoints.update(cp.id, {
        status: 'COMPLETED',
        completedAt: now,
        updatedAt: now,
      });

      // Advance current day pointer (Req 6.3)
      const nextDay = dayNumber + 1;
      if (nextDay > TOTAL_DAYS) {
        // All 30 days completed or passed — mark as finished (Req 6.6)
        await db.habitTracks.update(selectedTrack.id, {
          currentDay: TOTAL_DAYS,
          isFinished: true,
          updatedAt: now,
        });
      } else {
        await db.habitTracks.update(selectedTrack.id, {
          currentDay: nextDay,
          updatedAt: now,
        });
      }
    },
    [selectedTrack, checkpoints],
  );

  // ─── Loading state ─────────────────────────────────────────────────────────

  if (tracksLoading) {
    return (
      <main className="habits-page">
        <div className="habits-page__loading" role="status">
          {t('common.loading')}
        </div>
      </main>
    );
  }

  // ─── Detail View ───────────────────────────────────────────────────────────

  if (viewMode === 'detail' && selectedTrack) {
    return (
      <main className="habits-page">
        <HabitDetailView
          track={selectedTrack}
          checkpoints={selectedCheckpoints}
          goalName={getGoalName(selectedTrack.goalId)}
          onBack={() => {
            setViewMode('list');
            setSelectedTrackId(null);
          }}
          onCheckIn={handleCheckIn}
        />
      </main>
    );
  }

  // ─── List View ─────────────────────────────────────────────────────────────

  return (
    <main className="habits-page">
      <header className="habits-page__header">
        <h1 className="habits-page__title">{t('habits.title')}</h1>
        <button
          type="button"
          className="habits-page__create-btn"
          onClick={() => setShowCreateDialog(true)}
          disabled={activeGoals.length === 0}
          title={activeGoals.length === 0 ? t('goals.noActiveGoals') : t('habits.createHabit')}
        >
          {t('habits.createHabit')}
        </button>
      </header>

      {/* Filter tabs */}
      <div className="habits-page__filters" role="tablist">
        {(['active', 'finished', 'all'] as FilterMode[]).map((f) => (
          <button
            key={f}
            type="button"
            role="tab"
            aria-selected={filter === f}
            className={`habits-page__filter-tab${filter === f ? ' habits-page__filter-tab--active' : ''}`}
            onClick={() => setFilter(f)}
          >
            {f === 'active' && t('habits.active')}
            {f === 'finished' && t('habits.finished')}
            {f === 'all' && t('tasks.allTasks')}
          </button>
        ))}
      </div>

      {/* Track cards */}
      {filteredTracks.length === 0 ? (
        <div className="habits-page__empty" role="status">
          <p className="habits-page__empty-text">{t('habits.noHabits')}</p>
        </div>
      ) : (
        <div className="habits-page__grid">
          {filteredTracks.map((track) => (
            <HabitTrackCard
              key={track.id}
              track={track}
              checkpoints={checkpoints.filter((c) => c.habitTrackId === track.id)}
              goalName={getGoalName(track.goalId)}
              onClick={() => {
                setSelectedTrackId(track.id);
                setViewMode('detail');
              }}
            />
          ))}
        </div>
      )}

      {/* Create dialog */}
      {showCreateDialog && (
        <CreateHabitDialog
          goals={activeGoals}
          onSubmit={handleCreateTrack}
          onClose={() => setShowCreateDialog(false)}
        />
      )}
    </main>
  );
}
