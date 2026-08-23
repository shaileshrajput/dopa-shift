/**
 * Goals & Habits page — full goal management with checklist items,
 * linked habit tracks, create/edit/delete dialogs, and dependency
 * resolution on delete.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.5, 1.6, 1.7, 6.1, 6.3, 6.5
 */

import { useState, useCallback, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { db, type LocalGoal, type LocalGoalChecklistItem, type LocalHabitTrack } from '@data/db';
import { useLiveQuery } from '@data/stores';
import './GoalsPage.css';

// ─── Constants ───────────────────────────────────────────────────────────────

const STUB_USER_ID = 'current-user'; // Placeholder until auth context is wired
const MAX_KEYWORDS = 20;
const MAX_KEYWORD_LENGTH = 50;
const MAX_GOAL_NAME_LENGTH = 100;
const MAX_CATEGORY_LENGTH = 50;
const MAX_CHECKLIST_TEXT_LENGTH = 200;

// ─── Types ───────────────────────────────────────────────────────────────────

type DialogMode = 'create' | 'edit' | 'delete' | null;
type DeleteAction = 'DELETE_ALL' | 'REASSIGN';

interface GoalFormData {
  name: string;
  category: string;
  keywords: string[];
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function computeChecklistProgress(
  items: LocalGoalChecklistItem[],
): { completed: number; total: number; percent: number } {
  const total = items.length;
  const completed = items.filter((i) => i.isCompleted).length;
  const percent = total > 0 ? Math.round((completed / total) * 100) : 0;
  return { completed, total, percent };
}

function getHabitDayStatus(
  dayNumber: number,
  currentDay: number,
  isFinished: boolean,
): 'completed' | 'missed' | 'pending' | 'current' {
  if (dayNumber < currentDay) return 'completed';
  if (dayNumber === currentDay && !isFinished) return 'current';
  if (isFinished && dayNumber >= currentDay) return 'missed';
  return 'pending';
}

// ─── Sub-components ──────────────────────────────────────────────────────────

interface GoalCardProps {
  goal: LocalGoal;
  checklistItems: LocalGoalChecklistItem[];
  onClick: () => void;
}

function GoalCard({ goal, checklistItems, onClick }: GoalCardProps) {
  const { t } = useTranslation();
  const progress = computeChecklistProgress(checklistItems);

  return (
    <button
      type="button"
      className="goal-card-full"
      onClick={onClick}
      aria-label={goal.name}
    >
      <div className="goal-card-full__header">
        <span className="goal-card-full__name">{goal.name}</span>
        <span className="goal-card-full__category">{goal.category}</span>
      </div>
      <div className="goal-card-full__keywords">
        {goal.keywords.slice(0, 4).map((kw) => (
          <span key={kw} className="goal-card-full__keyword">{kw}</span>
        ))}
        {goal.keywords.length > 4 && (
          <span className="goal-card-full__keyword">+{goal.keywords.length - 4}</span>
        )}
      </div>
      {progress.total > 0 && (
        <div className="goal-card-full__progress">
          <div className="goal-card-full__progress-bar">
            <div
              className="goal-card-full__progress-fill"
              style={{ width: `${progress.percent}%` }}
            />
          </div>
          <span className="goal-card-full__progress-text">
            {t('dashboard.goalProgress', {
              completed: progress.completed,
              total: progress.total,
            })}
          </span>
        </div>
      )}
    </button>
  );
}

// ─── Habit Track 30-Day Grid ─────────────────────────────────────────────────

interface HabitGridProps {
  track: LocalHabitTrack;
}

function HabitGrid({ track }: HabitGridProps) {
  const days = Array.from({ length: 30 }, (_, i) => i + 1);

  return (
    <div className="habit-grid" role="img" aria-label="30-day habit progress">
      {days.map((day) => {
        const status = getHabitDayStatus(day, track.currentDay, track.isFinished);
        const isCurrent = day === track.currentDay && !track.isFinished;
        return (
          <div
            key={day}
            className={`habit-grid__day habit-grid__day--${status === 'current' ? 'pending' : status}${isCurrent ? ' habit-grid__day--current' : ''}`}
            title={`Day ${day}: ${status}`}
          >
            {day}
          </div>
        );
      })}
    </div>
  );
}

// ─── Habit Track Card ────────────────────────────────────────────────────────

interface HabitTrackCardProps {
  track: LocalHabitTrack;
}

function HabitTrackCard({ track }: HabitTrackCardProps) {
  const { t } = useTranslation();
  const completedDays = track.currentDay - 1;

  return (
    <div className="habit-track-card">
      <div className="habit-track-card__header">
        <span className="habit-track-card__title">
          {t('habits.dayOf30', { day: track.currentDay })}
        </span>
        <span
          className={`habit-track-card__status habit-track-card__status--${track.isFinished ? 'finished' : 'active'}`}
        >
          {track.isFinished ? t('habits.finished') : t('habits.active')}
        </span>
      </div>
      <HabitGrid track={track} />
      <div className="habit-track-card__meta">
        <span>{t('habits.startDate')}: {track.startDate}</span>
        <span>
          {t('habits.daysCompleted', { completed: completedDays, total: 30 })}
        </span>
      </div>
    </div>
  );
}

// ─── Create/Edit Goal Dialog ─────────────────────────────────────────────────

interface GoalDialogProps {
  mode: 'create' | 'edit';
  initialData?: GoalFormData;
  existingGoalNames: string[];
  editingGoalName?: string;
  onSubmit: (data: GoalFormData) => void;
  onClose: () => void;
}

function GoalDialog({
  mode,
  initialData,
  existingGoalNames,
  editingGoalName,
  onSubmit,
  onClose,
}: GoalDialogProps) {
  const { t } = useTranslation();
  const [name, setName] = useState(initialData?.name ?? '');
  const [category, setCategory] = useState(initialData?.category ?? '');
  const [keywords, setKeywords] = useState<string[]>(initialData?.keywords ?? []);
  const [keywordInput, setKeywordInput] = useState('');
  const [error, setError] = useState('');

  const handleAddKeyword = useCallback(() => {
    const kw = keywordInput.trim();
    if (!kw) return;
    if (keywords.length >= MAX_KEYWORDS) {
      setError(t('goals.maxKeywords'));
      return;
    }
    if (kw.length > MAX_KEYWORD_LENGTH) return;
    if (!keywords.includes(kw)) {
      setKeywords((prev) => [...prev, kw]);
    }
    setKeywordInput('');
    setError('');
  }, [keywordInput, keywords, t]);

  const handleRemoveKeyword = useCallback((kw: string) => {
    setKeywords((prev) => prev.filter((k) => k !== kw));
  }, []);

  const handleSubmit = useCallback(
    (e: FormEvent) => {
      e.preventDefault();
      const trimmedName = name.trim();
      if (!trimmedName) {
        setError(t('errors.goalNameRequired'));
        return;
      }
      if (!category.trim()) {
        setError(t('errors.categoryRequired'));
        return;
      }
      if (keywords.length === 0) {
        setError(t('errors.keywordRequired'));
        return;
      }
      // Check uniqueness (Req 1.7) — exclude current name when editing
      const nameConflict = existingGoalNames.some(
        (n) => n.toLowerCase() === trimmedName.toLowerCase() && n !== editingGoalName,
      );
      if (nameConflict) {
        setError(t('goals.nameTaken'));
        return;
      }
      onSubmit({ name: trimmedName, category: category.trim(), keywords });
    },
    [name, category, keywords, existingGoalNames, editingGoalName, onSubmit, t],
  );

  return (
    <div className="dialog-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <form
        className="dialog"
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
      >
        <h2 className="dialog__title">
          {mode === 'create' ? t('goals.createGoal') : t('goals.updateGoal')}
        </h2>

        {error && <p className="dialog__error" role="alert">{error}</p>}

        <div className="dialog__field">
          <label className="dialog__label" htmlFor="goal-name">
            {t('goals.goalName')}
          </label>
          <input
            id="goal-name"
            className="dialog__input"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('goals.goalNamePlaceholder')}
            maxLength={MAX_GOAL_NAME_LENGTH}
            autoFocus
          />
        </div>

        <div className="dialog__field">
          <label className="dialog__label" htmlFor="goal-category">
            {t('goals.category')}
          </label>
          <input
            id="goal-category"
            className="dialog__input"
            type="text"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            placeholder={t('goals.categoryPlaceholder')}
            maxLength={MAX_CATEGORY_LENGTH}
          />
        </div>

        <div className="dialog__field">
          <label className="dialog__label">{t('goals.keywords')}</label>
          {keywords.length > 0 && (
            <div className="dialog__keywords-list">
              {keywords.map((kw) => (
                <span key={kw} className="dialog__keyword-chip">
                  {kw}
                  <button
                    type="button"
                    className="dialog__keyword-remove"
                    onClick={() => handleRemoveKeyword(kw)}
                    aria-label={`Remove ${kw}`}
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}
          <div className="dialog__keyword-add">
            <input
              className="dialog__input"
              type="text"
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
              placeholder={t('goals.keywordPlaceholder')}
              maxLength={MAX_KEYWORD_LENGTH}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleAddKeyword();
                }
              }}
            />
            <button
              type="button"
              className="dialog__btn dialog__btn--secondary"
              onClick={handleAddKeyword}
              disabled={!keywordInput.trim()}
            >
              {t('goals.addKeyword')}
            </button>
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
          <button type="submit" className="dialog__btn dialog__btn--primary">
            {mode === 'create' ? t('common.create') : t('common.save')}
          </button>
        </div>
      </form>
    </div>
  );
}

// ─── Delete Confirmation Dialog with Dependency Resolution ───────────────────

interface DeleteDialogProps {
  goal: LocalGoal;
  dependentChecklist: LocalGoalChecklistItem[];
  dependentHabits: LocalHabitTrack[];
  otherGoals: LocalGoal[];
  onConfirm: (action: DeleteAction, reassignGoalId?: string) => void;
  onClose: () => void;
}

function DeleteDialog({
  goal,
  dependentChecklist,
  dependentHabits,
  otherGoals,
  onConfirm,
  onClose,
}: DeleteDialogProps) {
  const { t } = useTranslation();
  const [action, setAction] = useState<DeleteAction>('DELETE_ALL');
  const [reassignGoalId, setReassignGoalId] = useState(otherGoals[0]?.id ?? '');
  const hasDependents = dependentChecklist.length > 0 || dependentHabits.length > 0;

  const handleConfirm = useCallback(() => {
    if (action === 'REASSIGN' && !reassignGoalId) return;
    onConfirm(action, action === 'REASSIGN' ? reassignGoalId : undefined);
  }, [action, reassignGoalId, onConfirm]);

  return (
    <div className="dialog-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h2 className="dialog__title">{t('goals.deleteGoal')}</h2>
        <p>{t('goals.confirmDelete')}</p>
        <p><strong>{goal.name}</strong></p>

        {hasDependents && (
          <>
            <div className="dialog__field">
              <label className="dialog__label">{t('goals.dependentItems')}</label>
              <div className="dialog__dependents-list">
                {dependentChecklist.map((item) => (
                  <span key={item.id} className="dialog__dependent-item">
                    {t('goals.checklist')}: {item.text}
                  </span>
                ))}
                {dependentHabits.map((track) => (
                  <span key={track.id} className="dialog__dependent-item">
                    {t('habits.title')}: {t('habits.dayOf30', { day: track.currentDay })}
                  </span>
                ))}
              </div>
            </div>

            <div className="dialog__radio-group">
              <label className="dialog__radio-option">
                <input
                  type="radio"
                  name="delete-action"
                  value="DELETE_ALL"
                  checked={action === 'DELETE_ALL'}
                  onChange={() => setAction('DELETE_ALL')}
                />
                <span className="dialog__radio-label">
                  {t('goals.deleteWithDependents')}
                </span>
              </label>
              <label className="dialog__radio-option">
                <input
                  type="radio"
                  name="delete-action"
                  value="REASSIGN"
                  checked={action === 'REASSIGN'}
                  onChange={() => setAction('REASSIGN')}
                  disabled={otherGoals.length === 0}
                />
                <span className="dialog__radio-label">
                  {t('goals.reassignDependents')}
                </span>
              </label>
              {action === 'REASSIGN' && otherGoals.length > 0 && (
                <select
                  className="dialog__select"
                  value={reassignGoalId}
                  onChange={(e) => setReassignGoalId(e.target.value)}
                  aria-label="Select goal to reassign to"
                >
                  {otherGoals.map((g) => (
                    <option key={g.id} value={g.id}>{g.name}</option>
                  ))}
                </select>
              )}
            </div>
          </>
        )}

        <div className="dialog__actions">
          <button
            type="button"
            className="dialog__btn dialog__btn--secondary"
            onClick={onClose}
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            className="dialog__btn dialog__btn--danger"
            onClick={handleConfirm}
          >
            {t('common.delete')}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Goal Detail View ────────────────────────────────────────────────────────

interface GoalDetailViewProps {
  goal: LocalGoal;
  checklistItems: LocalGoalChecklistItem[];
  habitTracks: LocalHabitTrack[];
  allGoals: LocalGoal[];
  onBack: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

function GoalDetailView({
  goal,
  checklistItems,
  habitTracks,
  allGoals: _allGoals,
  onBack,
  onEdit,
  onDelete,
}: GoalDetailViewProps) {
  const { t } = useTranslation();
  const [newItemText, setNewItemText] = useState('');

  const handleAddChecklist = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      const text = newItemText.trim();
      if (!text) return;

      const now = new Date().toISOString();
      await db.goalChecklistItems.put({
        id: crypto.randomUUID(),
        goalId: goal.id,
        userId: STUB_USER_ID,
        text,
        isCompleted: false,
        createdAt: now,
        updatedAt: now,
      });
      setNewItemText('');
    },
    [newItemText, goal.id],
  );

  const handleToggleChecklist = useCallback(async (item: LocalGoalChecklistItem) => {
    await db.goalChecklistItems.update(item.id, {
      isCompleted: !item.isCompleted,
      updatedAt: new Date().toISOString(),
    });
  }, []);

  const handleDeleteChecklist = useCallback(async (id: string) => {
    await db.goalChecklistItems.delete(id);
  }, []);

  const progress = computeChecklistProgress(checklistItems);

  return (
    <div className="goal-detail">
      <button type="button" className="goal-detail__back-btn" onClick={onBack}>
        ← {t('common.back')}
      </button>

      <div className="goal-detail__header">
        <h2 className="goal-detail__name">{goal.name}</h2>
        <div className="goal-detail__actions">
          <button
            type="button"
            className="goal-detail__edit-btn"
            onClick={onEdit}
          >
            {t('common.edit')}
          </button>
          <button
            type="button"
            className="goal-detail__delete-btn"
            onClick={onDelete}
          >
            {t('common.delete')}
          </button>
        </div>
      </div>

      <div className="goal-detail__meta">
        <span className="goal-detail__meta-item">
          <span className="goal-detail__meta-label">{t('goals.category')}: </span>
          {goal.category}
        </span>
        <span className="goal-detail__meta-item">
          <span className="goal-detail__meta-label">{t('goals.progress')}: </span>
          {progress.total > 0
            ? `${progress.completed}/${progress.total} (${progress.percent}%)`
            : '—'}
        </span>
      </div>

      <div className="goal-detail__keywords">
        {goal.keywords.map((kw) => (
          <span key={kw} className="goal-detail__keyword">{kw}</span>
        ))}
      </div>

      {/* ─── Checklist Section (Req 1.3) ─── */}
      <section>
        <h3 className="goal-detail__section-title">{t('goals.checklist')}</h3>
        <div className="checklist">
          {checklistItems.map((item) => (
            <div key={item.id} className="checklist__item">
              <input
                type="checkbox"
                className="checklist__checkbox"
                checked={item.isCompleted}
                onChange={() => handleToggleChecklist(item)}
                aria-label={item.text}
              />
              <span
                className={`checklist__text${item.isCompleted ? ' checklist__text--completed' : ''}`}
              >
                {item.text}
              </span>
              <button
                type="button"
                className="checklist__delete-btn"
                onClick={() => handleDeleteChecklist(item.id)}
                aria-label={`${t('common.delete')} ${item.text}`}
              >
                ×
              </button>
            </div>
          ))}
          <form className="checklist__add-form" onSubmit={handleAddChecklist}>
            <input
              className="checklist__add-input"
              type="text"
              value={newItemText}
              onChange={(e) => setNewItemText(e.target.value)}
              placeholder={t('goals.checklistPlaceholder')}
              maxLength={MAX_CHECKLIST_TEXT_LENGTH}
            />
            <button
              type="submit"
              className="checklist__add-btn"
              disabled={!newItemText.trim()}
            >
              {t('goals.addChecklistItem')}
            </button>
          </form>
        </div>
      </section>

      {/* ─── Linked Habit Tracks (Req 6.1, 6.3, 6.5) ─── */}
      <section className="habit-tracks-section">
        <h3 className="goal-detail__section-title">{t('habits.title')}</h3>
        {habitTracks.length > 0 ? (
          habitTracks.map((track) => (
            <HabitTrackCard key={track.id} track={track} />
          ))
        ) : (
          <p style={{ color: 'var(--color-on-surface-variant)' }}>
            {t('habits.noHabits')}
          </p>
        )}
      </section>
    </div>
  );
}

// ─── Main Goals Page ─────────────────────────────────────────────────────────

export default function GoalsPage() {
  const { t } = useTranslation();
  const [selectedGoalId, setSelectedGoalId] = useState<string | null>(null);
  const [dialogMode, setDialogMode] = useState<DialogMode>(null);

  // ─── Reactive data subscriptions ───────────────────────────────────────────

  const { data: goals, loading: goalsLoading } = useLiveQuery<LocalGoal[]>(
    () => db.goals.where('userId').equals(STUB_USER_ID).toArray(),
    [STUB_USER_ID],
    [],
  );

  const { data: allChecklistItems } = useLiveQuery<LocalGoalChecklistItem[]>(
    () => db.goalChecklistItems.where('userId').equals(STUB_USER_ID).toArray(),
    [STUB_USER_ID],
    [],
  );

  const { data: allHabitTracks } = useLiveQuery<LocalHabitTrack[]>(
    () => db.habitTracks.where('userId').equals(STUB_USER_ID).toArray(),
    [STUB_USER_ID],
    [],
  );

  const goalsList = goals ?? [];
  const checklistItems = allChecklistItems ?? [];
  const habitTracks = allHabitTracks ?? [];

  const selectedGoal = selectedGoalId
    ? goalsList.find((g) => g.id === selectedGoalId) ?? null
    : null;

  const selectedChecklist = selectedGoal
    ? checklistItems.filter((i) => i.goalId === selectedGoal.id)
    : [];

  const selectedHabitTracks = selectedGoal
    ? habitTracks.filter((t) => t.goalId === selectedGoal.id)
    : [];

  // ─── Handlers ──────────────────────────────────────────────────────────────

  const handleCreateGoal = useCallback(
    async (data: GoalFormData) => {
      const now = new Date().toISOString();
      await db.goals.put({
        id: crypto.randomUUID(),
        userId: STUB_USER_ID,
        name: data.name,
        category: data.category,
        keywords: data.keywords,
        isActive: true,
        createdAt: now,
        updatedAt: now,
      });
      setDialogMode(null);
    },
    [],
  );

  const handleEditGoal = useCallback(
    async (data: GoalFormData) => {
      if (!selectedGoal) return;
      await db.goals.update(selectedGoal.id, {
        name: data.name,
        category: data.category,
        keywords: data.keywords,
        updatedAt: new Date().toISOString(),
      });
      setDialogMode(null);
    },
    [selectedGoal],
  );

  const handleDeleteGoal = useCallback(
    async (action: DeleteAction, reassignGoalId?: string) => {
      if (!selectedGoal) return;

      const dependentChecklist = checklistItems.filter(
        (i) => i.goalId === selectedGoal.id,
      );
      const dependentHabits = habitTracks.filter(
        (h) => h.goalId === selectedGoal.id,
      );

      if (action === 'DELETE_ALL') {
        // Delete all dependents (Req 1.6)
        const checklistIds = dependentChecklist.map((i) => i.id);
        const habitIds = dependentHabits.map((h) => h.id);
        await db.goalChecklistItems.bulkDelete(checklistIds);
        await db.habitTracks.bulkDelete(habitIds);
      } else if (action === 'REASSIGN' && reassignGoalId) {
        // Reassign dependents to another goal (Req 1.5)
        const now = new Date().toISOString();
        for (const item of dependentChecklist) {
          await db.goalChecklistItems.update(item.id, {
            goalId: reassignGoalId,
            updatedAt: now,
          });
        }
        for (const track of dependentHabits) {
          await db.habitTracks.update(track.id, {
            goalId: reassignGoalId,
            updatedAt: now,
          });
        }
      }

      await db.goals.delete(selectedGoal.id);
      setSelectedGoalId(null);
      setDialogMode(null);
    },
    [selectedGoal, checklistItems, habitTracks],
  );

  // ─── Loading state ─────────────────────────────────────────────────────────

  if (goalsLoading) {
    return (
      <main className="goals-page">
        <div className="goals-page__loading" role="status">
          {t('common.loading')}
        </div>
      </main>
    );
  }

  // ─── Detail View ───────────────────────────────────────────────────────────

  if (selectedGoal) {
    return (
      <main className="goals-page">
        <GoalDetailView
          goal={selectedGoal}
          checklistItems={selectedChecklist}
          habitTracks={selectedHabitTracks}
          allGoals={goalsList}
          onBack={() => setSelectedGoalId(null)}
          onEdit={() => setDialogMode('edit')}
          onDelete={() => setDialogMode('delete')}
        />

        {/* Edit dialog */}
        {dialogMode === 'edit' && (
          <GoalDialog
            mode="edit"
            initialData={{
              name: selectedGoal.name,
              category: selectedGoal.category,
              keywords: selectedGoal.keywords,
            }}
            existingGoalNames={goalsList.map((g) => g.name)}
            editingGoalName={selectedGoal.name}
            onSubmit={handleEditGoal}
            onClose={() => setDialogMode(null)}
          />
        )}

        {/* Delete dialog with dependency resolution (Req 1.5, 1.6) */}
        {dialogMode === 'delete' && (
          <DeleteDialog
            goal={selectedGoal}
            dependentChecklist={selectedChecklist}
            dependentHabits={selectedHabitTracks}
            otherGoals={goalsList.filter((g) => g.id !== selectedGoal.id)}
            onConfirm={handleDeleteGoal}
            onClose={() => setDialogMode(null)}
          />
        )}
      </main>
    );
  }

  // ─── Goal List View ────────────────────────────────────────────────────────

  return (
    <main className="goals-page">
      <header className="goals-page__header">
        <h1 className="goals-page__title">{t('goals.title')}</h1>
        <button
          type="button"
          className="goals-page__create-btn"
          onClick={() => setDialogMode('create')}
        >
          {t('goals.createGoal')}
        </button>
      </header>

      {goalsList.length === 0 ? (
        <div className="goals-page__empty" role="status">
          <p className="goals-page__empty-text">{t('goals.noGoals')}</p>
        </div>
      ) : (
        <div className="goals-page__grid">
          {goalsList.map((goal) => (
            <GoalCard
              key={goal.id}
              goal={goal}
              checklistItems={checklistItems.filter((i) => i.goalId === goal.id)}
              onClick={() => setSelectedGoalId(goal.id)}
            />
          ))}
        </div>
      )}

      {/* Create dialog */}
      {dialogMode === 'create' && (
        <GoalDialog
          mode="create"
          existingGoalNames={goalsList.map((g) => g.name)}
          onSubmit={handleCreateGoal}
          onClose={() => setDialogMode(null)}
        />
      )}
    </main>
  );
}
