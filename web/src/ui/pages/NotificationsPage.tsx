/**
 * Notifications & Reminders management page.
 *
 * Provides a full CRUD UI for reminders including:
 * - List of active/inactive reminders
 * - Create/Edit/Delete reminder dialogs
 * - Recurrence type selection (daily, weekdays, weekly, monthly, custom interval)
 * - Conditional reminder toggle (fire only if entity incomplete)
 * - Escalation interval and max count configuration
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4
 */

import { useState, useCallback, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { db, type LocalReminder } from '@data/db';
import { useLiveQuery } from '@data/stores';
import './NotificationsPage.css';

// ─── Constants ───────────────────────────────────────────────────────────────

const STUB_USER_ID = 'current-user'; // Placeholder until auth context is wired

const WEEKDAY_KEYS = [
  'notifications.weekdayMon',
  'notifications.weekdayTue',
  'notifications.weekdayWed',
  'notifications.weekdayThu',
  'notifications.weekdayFri',
  'notifications.weekdaySat',
  'notifications.weekdaySun',
] as const;

const WEEKDAY_ISO_VALUES = [1, 2, 3, 4, 5, 6, 7] as const;

type RecurrenceType = LocalReminder['recurrenceType'];
type EntityType = LocalReminder['entityType'];
type DialogMode = 'create' | 'edit' | 'delete' | null;

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatRecurrence(
  reminder: LocalReminder,
  t: (key: string, opts?: Record<string, unknown>) => string,
): string {
  if (!reminder.recurrenceType) return t('notifications.recurrenceNone');
  switch (reminder.recurrenceType) {
    case 'DAILY':
      return t('notifications.recurrenceDaily');
    case 'SPECIFIC_WEEKDAYS': {
      const dayLabels = reminder.recurrenceWeekdays
        .map((d) => t(WEEKDAY_KEYS[d - 1]))
        .join(', ');
      return dayLabels;
    }
    case 'WEEKLY':
      return t('notifications.recurrenceWeekly');
    case 'MONTHLY':
      return t('notifications.recurrenceMonthly');
    case 'CUSTOM_INTERVAL':
      return `${t('notifications.recurrenceCustom')} (${reminder.recurrenceIntervalDays}d)`;
    default:
      return '';
  }
}

function formatEntityType(
  entityType: EntityType,
  t: (key: string) => string,
): string {
  switch (entityType) {
    case 'DAILY_TODO':
      return t('notifications.entityTypeTodo');
    case 'GOAL_CHECKLIST':
      return t('notifications.entityTypeChecklist');
    case 'HABIT_CHECKPOINT':
      return t('notifications.entityTypeHabit');
  }
}

// ─── Form Data Interface ─────────────────────────────────────────────────────

interface ReminderFormData {
  entityType: EntityType;
  entityId: string;
  entityLabel: string;
  scheduledTime: string;
  scheduledDate: string;
  recurrenceType: RecurrenceType;
  recurrenceWeekdays: number[];
  recurrenceIntervalDays: number | null;
  conditionType: 'ENTITY_INCOMPLETE' | null;
  escalationIntervalMinutes: number;
  maxEscalations: number;
}

const DEFAULT_FORM_DATA: ReminderFormData = {
  entityType: 'DAILY_TODO',
  entityId: '',
  entityLabel: '',
  scheduledTime: '09:00',
  scheduledDate: '',
  recurrenceType: null,
  recurrenceWeekdays: [],
  recurrenceIntervalDays: null,
  conditionType: null,
  escalationIntervalMinutes: 15,
  maxEscalations: 3,
};

// ─── Reminder Card ───────────────────────────────────────────────────────────

interface ReminderCardProps {
  reminder: LocalReminder;
  onEdit: () => void;
  onDelete: () => void;
  onToggleActive: () => void;
}

function ReminderCard({ reminder, onEdit, onDelete, onToggleActive }: ReminderCardProps) {
  const { t } = useTranslation();

  return (
    <div className={`reminder-card${!reminder.isActive ? ' reminder-card--inactive' : ''}`}>
      <div className="reminder-card__header">
        <div className="reminder-card__entity-info">
          <span className="reminder-card__entity-type">
            {formatEntityType(reminder.entityType, t)}
          </span>
          <span className="reminder-card__entity-label">{reminder.entityLabel}</span>
        </div>
        <div className="reminder-card__status">
          <button
            type="button"
            className={`reminder-card__toggle${reminder.isActive ? ' reminder-card__toggle--active' : ''}`}
            onClick={onToggleActive}
            aria-label={reminder.isActive ? t('notifications.active') : t('notifications.inactive')}
            title={reminder.isActive ? t('notifications.active') : t('notifications.inactive')}
          >
            <span className="reminder-card__toggle-dot" />
          </button>
        </div>
      </div>

      <div className="reminder-card__details">
        <span className="reminder-card__time">{reminder.scheduledTime}</span>
        <span className="reminder-card__recurrence">
          {formatRecurrence(reminder, t)}
        </span>
        {reminder.conditionType && (
          <span className="reminder-card__conditional">
            {t('notifications.conditional')}
          </span>
        )}
      </div>

      <div className="reminder-card__escalation">
        <span className="reminder-card__escalation-label">
          {t('notifications.escalation')}:
        </span>
        <span className="reminder-card__escalation-value">
          {reminder.escalationIntervalMinutes}min × {reminder.maxEscalations}
        </span>
      </div>

      <div className="reminder-card__actions">
        <button
          type="button"
          className="reminder-card__edit-btn"
          onClick={onEdit}
        >
          {t('common.edit')}
        </button>
        <button
          type="button"
          className="reminder-card__delete-btn"
          onClick={onDelete}
        >
          {t('common.delete')}
        </button>
      </div>
    </div>
  );
}

// ─── Create/Edit Reminder Dialog ─────────────────────────────────────────────

interface ReminderDialogProps {
  mode: 'create' | 'edit';
  initialData?: ReminderFormData;
  onSubmit: (data: ReminderFormData) => void;
  onClose: () => void;
}

function ReminderDialog({ mode, initialData, onSubmit, onClose }: ReminderDialogProps) {
  const { t } = useTranslation();
  const [formData, setFormData] = useState<ReminderFormData>(
    initialData ?? DEFAULT_FORM_DATA,
  );
  const [error, setError] = useState('');

  const updateField = useCallback(
    <K extends keyof ReminderFormData>(key: K, value: ReminderFormData[K]) => {
      setFormData((prev) => ({ ...prev, [key]: value }));
      setError('');
    },
    [],
  );

  const handleToggleWeekday = useCallback((day: number) => {
    setFormData((prev) => {
      const weekdays = prev.recurrenceWeekdays.includes(day)
        ? prev.recurrenceWeekdays.filter((d) => d !== day)
        : [...prev.recurrenceWeekdays, day].sort((a, b) => a - b);
      return { ...prev, recurrenceWeekdays: weekdays };
    });
  }, []);

  const handleSubmit = useCallback(
    (e: FormEvent) => {
      e.preventDefault();

      if (!formData.scheduledTime) {
        setError(t('common.required'));
        return;
      }
      if (!formData.entityLabel.trim()) {
        setError(t('common.required'));
        return;
      }

      // Validate one-off requires a date
      if (!formData.recurrenceType && !formData.scheduledDate) {
        setError(t('common.required'));
        return;
      }

      // Validate weekdays recurrence has at least one day selected
      if (
        formData.recurrenceType === 'SPECIFIC_WEEKDAYS' &&
        formData.recurrenceWeekdays.length === 0
      ) {
        setError(t('common.required'));
        return;
      }

      // Validate custom interval
      if (formData.recurrenceType === 'CUSTOM_INTERVAL') {
        const interval = formData.recurrenceIntervalDays;
        if (!interval || interval < 1 || interval > 365) {
          setError(t('notifications.intervalDaysPlaceholder'));
          return;
        }
      }

      // Validate escalation
      if (
        formData.escalationIntervalMinutes < 5 ||
        formData.escalationIntervalMinutes > 120
      ) {
        setError(t('notifications.escalationIntervalRange'));
        return;
      }
      if (formData.maxEscalations < 1 || formData.maxEscalations > 10) {
        setError(t('notifications.maxEscalationsRange'));
        return;
      }

      onSubmit(formData);
    },
    [formData, onSubmit, t],
  );

  return (
    <div className="dialog-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <form
        className="dialog dialog--wide"
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
      >
        <h2 className="dialog__title">
          {mode === 'create'
            ? t('notifications.createReminder')
            : t('notifications.editReminder')}
        </h2>

        {error && <p className="dialog__error" role="alert">{error}</p>}

        {/* Entity Type */}
        <div className="dialog__field">
          <label className="dialog__label" htmlFor="entity-type">
            {t('notifications.entityType')}
          </label>
          <select
            id="entity-type"
            className="dialog__select"
            value={formData.entityType}
            onChange={(e) => updateField('entityType', e.target.value as EntityType)}
          >
            <option value="DAILY_TODO">{t('notifications.entityTypeTodo')}</option>
            <option value="GOAL_CHECKLIST">{t('notifications.entityTypeChecklist')}</option>
            <option value="HABIT_CHECKPOINT">{t('notifications.entityTypeHabit')}</option>
          </select>
        </div>

        {/* Entity Label */}
        <div className="dialog__field">
          <label className="dialog__label" htmlFor="entity-label">
            {t('notifications.entityLabel')}
          </label>
          <input
            id="entity-label"
            className="dialog__input"
            type="text"
            value={formData.entityLabel}
            onChange={(e) => updateField('entityLabel', e.target.value)}
            placeholder={t('notifications.entityLabelPlaceholder')}
            maxLength={200}
          />
        </div>

        {/* Scheduled Time */}
        <div className="dialog__field">
          <label className="dialog__label" htmlFor="scheduled-time">
            {t('notifications.scheduledTime')}
          </label>
          <input
            id="scheduled-time"
            className="dialog__input"
            type="time"
            value={formData.scheduledTime}
            onChange={(e) => updateField('scheduledTime', e.target.value)}
          />
        </div>

        {/* Recurrence Type */}
        <div className="dialog__field">
          <label className="dialog__label" htmlFor="recurrence-type">
            {t('notifications.recurrence')}
          </label>
          <select
            id="recurrence-type"
            className="dialog__select"
            value={formData.recurrenceType ?? ''}
            onChange={(e) => {
              const value = e.target.value || null;
              updateField('recurrenceType', value as RecurrenceType);
            }}
          >
            <option value="">{t('notifications.recurrenceNone')}</option>
            <option value="DAILY">{t('notifications.recurrenceDaily')}</option>
            <option value="SPECIFIC_WEEKDAYS">{t('notifications.recurrenceWeekdays')}</option>
            <option value="WEEKLY">{t('notifications.recurrenceWeekly')}</option>
            <option value="MONTHLY">{t('notifications.recurrenceMonthly')}</option>
            <option value="CUSTOM_INTERVAL">{t('notifications.recurrenceCustom')}</option>
          </select>
        </div>

        {/* One-off date field (only when no recurrence) */}
        {!formData.recurrenceType && (
          <div className="dialog__field">
            <label className="dialog__label" htmlFor="scheduled-date">
              {t('notifications.scheduledDate')}
            </label>
            <input
              id="scheduled-date"
              className="dialog__input"
              type="date"
              value={formData.scheduledDate}
              onChange={(e) => updateField('scheduledDate', e.target.value)}
            />
          </div>
        )}

        {/* Weekday selection (only for SPECIFIC_WEEKDAYS) */}
        {formData.recurrenceType === 'SPECIFIC_WEEKDAYS' && (
          <div className="dialog__field">
            <label className="dialog__label">{t('notifications.weekdays')}</label>
            <div className="weekday-selector">
              {WEEKDAY_ISO_VALUES.map((day, idx) => (
                <button
                  key={day}
                  type="button"
                  className={`weekday-selector__day${formData.recurrenceWeekdays.includes(day) ? ' weekday-selector__day--selected' : ''}`}
                  onClick={() => handleToggleWeekday(day)}
                  aria-pressed={formData.recurrenceWeekdays.includes(day)}
                >
                  {t(WEEKDAY_KEYS[idx])}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Custom interval days */}
        {formData.recurrenceType === 'CUSTOM_INTERVAL' && (
          <div className="dialog__field">
            <label className="dialog__label" htmlFor="interval-days">
              {t('notifications.intervalDays')}
            </label>
            <input
              id="interval-days"
              className="dialog__input"
              type="number"
              min={1}
              max={365}
              value={formData.recurrenceIntervalDays ?? ''}
              onChange={(e) =>
                updateField(
                  'recurrenceIntervalDays',
                  e.target.value ? parseInt(e.target.value, 10) : null,
                )
              }
              placeholder={t('notifications.intervalDaysPlaceholder')}
            />
          </div>
        )}

        {/* Conditional toggle */}
        <div className="dialog__field dialog__field--row">
          <label className="dialog__checkbox-label" htmlFor="conditional-toggle">
            <input
              id="conditional-toggle"
              type="checkbox"
              className="dialog__checkbox"
              checked={formData.conditionType === 'ENTITY_INCOMPLETE'}
              onChange={(e) =>
                updateField(
                  'conditionType',
                  e.target.checked ? 'ENTITY_INCOMPLETE' : null,
                )
              }
            />
            <span>{t('notifications.conditionalDescription')}</span>
          </label>
        </div>

        {/* Escalation Configuration */}
        <fieldset className="dialog__fieldset">
          <legend className="dialog__legend">{t('notifications.escalation')}</legend>

          <div className="dialog__field">
            <label className="dialog__label" htmlFor="escalation-interval">
              {t('notifications.escalationInterval')}
            </label>
            <input
              id="escalation-interval"
              className="dialog__input"
              type="number"
              min={5}
              max={120}
              value={formData.escalationIntervalMinutes}
              onChange={(e) =>
                updateField('escalationIntervalMinutes', parseInt(e.target.value, 10) || 15)
              }
            />
            <span className="dialog__hint">{t('notifications.escalationIntervalRange')}</span>
          </div>

          <div className="dialog__field">
            <label className="dialog__label" htmlFor="max-escalations">
              {t('notifications.maxEscalations')}
            </label>
            <input
              id="max-escalations"
              className="dialog__input"
              type="number"
              min={1}
              max={10}
              value={formData.maxEscalations}
              onChange={(e) =>
                updateField('maxEscalations', parseInt(e.target.value, 10) || 3)
              }
            />
            <span className="dialog__hint">{t('notifications.maxEscalationsRange')}</span>
          </div>
        </fieldset>

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

// ─── Delete Confirmation Dialog ──────────────────────────────────────────────

interface DeleteReminderDialogProps {
  reminder: LocalReminder;
  onConfirm: () => void;
  onClose: () => void;
}

function DeleteReminderDialog({ reminder, onConfirm, onClose }: DeleteReminderDialogProps) {
  const { t } = useTranslation();

  return (
    <div className="dialog-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h2 className="dialog__title">{t('notifications.deleteReminder')}</h2>
        <p>{t('notifications.confirmDelete')}</p>
        <p>
          <strong>{reminder.entityLabel}</strong> — {reminder.scheduledTime}
        </p>
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
            onClick={onConfirm}
          >
            {t('common.delete')}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Main Notifications Page ─────────────────────────────────────────────────

export default function NotificationsPage() {
  const { t } = useTranslation();
  const [dialogMode, setDialogMode] = useState<DialogMode>(null);
  const [editingReminder, setEditingReminder] = useState<LocalReminder | null>(null);
  const [deletingReminder, setDeletingReminder] = useState<LocalReminder | null>(null);

  // ─── Reactive data subscriptions ───────────────────────────────────────────

  const { data: reminders, loading } = useLiveQuery<LocalReminder[]>(
    () => db.reminders.where('userId').equals(STUB_USER_ID).toArray(),
    [STUB_USER_ID],
    [],
  );

  const remindersList = reminders ?? [];

  // ─── Handlers ──────────────────────────────────────────────────────────────

  const handleCreateReminder = useCallback(async (data: ReminderFormData) => {
    const now = new Date().toISOString();
    await db.reminders.put({
      id: crypto.randomUUID(),
      userId: STUB_USER_ID,
      entityType: data.entityType,
      entityId: data.entityId || crypto.randomUUID(), // placeholder entity ID
      entityLabel: data.entityLabel.trim(),
      scheduledTime: data.scheduledTime,
      scheduledDate: data.recurrenceType ? null : data.scheduledDate || null,
      recurrenceType: data.recurrenceType,
      recurrenceWeekdays: data.recurrenceWeekdays,
      recurrenceIntervalDays: data.recurrenceIntervalDays,
      conditionType: data.conditionType,
      escalationIntervalMinutes: data.escalationIntervalMinutes,
      maxEscalations: data.maxEscalations,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });
    setDialogMode(null);
  }, []);

  const handleEditReminder = useCallback(
    async (data: ReminderFormData) => {
      if (!editingReminder) return;
      await db.reminders.update(editingReminder.id, {
        entityType: data.entityType,
        entityId: data.entityId || editingReminder.entityId,
        entityLabel: data.entityLabel.trim(),
        scheduledTime: data.scheduledTime,
        scheduledDate: data.recurrenceType ? null : data.scheduledDate || null,
        recurrenceType: data.recurrenceType,
        recurrenceWeekdays: data.recurrenceWeekdays,
        recurrenceIntervalDays: data.recurrenceIntervalDays,
        conditionType: data.conditionType,
        escalationIntervalMinutes: data.escalationIntervalMinutes,
        maxEscalations: data.maxEscalations,
        updatedAt: new Date().toISOString(),
      });
      setDialogMode(null);
      setEditingReminder(null);
    },
    [editingReminder],
  );

  const handleDeleteReminder = useCallback(async () => {
    if (!deletingReminder) return;
    await db.reminders.delete(deletingReminder.id);
    setDialogMode(null);
    setDeletingReminder(null);
  }, [deletingReminder]);

  const handleToggleActive = useCallback(async (reminder: LocalReminder) => {
    await db.reminders.update(reminder.id, {
      isActive: !reminder.isActive,
      updatedAt: new Date().toISOString(),
    });
  }, []);

  const openEdit = useCallback((reminder: LocalReminder) => {
    setEditingReminder(reminder);
    setDialogMode('edit');
  }, []);

  const openDelete = useCallback((reminder: LocalReminder) => {
    setDeletingReminder(reminder);
    setDialogMode('delete');
  }, []);

  // ─── Loading state ─────────────────────────────────────────────────────────

  if (loading) {
    return (
      <main className="notifications-page">
        <div className="notifications-page__loading" role="status">
          {t('common.loading')}
        </div>
      </main>
    );
  }

  // ─── Main Render ───────────────────────────────────────────────────────────

  return (
    <main className="notifications-page">
      <header className="notifications-page__header">
        <h1 className="notifications-page__title">{t('notifications.title')}</h1>
        <button
          type="button"
          className="notifications-page__create-btn"
          onClick={() => setDialogMode('create')}
        >
          {t('notifications.createReminder')}
        </button>
      </header>

      {remindersList.length === 0 ? (
        <div className="notifications-page__empty" role="status">
          <p className="notifications-page__empty-text">
            {t('notifications.noReminders')}
          </p>
        </div>
      ) : (
        <div className="notifications-page__list">
          {remindersList.map((reminder) => (
            <ReminderCard
              key={reminder.id}
              reminder={reminder}
              onEdit={() => openEdit(reminder)}
              onDelete={() => openDelete(reminder)}
              onToggleActive={() => handleToggleActive(reminder)}
            />
          ))}
        </div>
      )}

      {/* Create dialog */}
      {dialogMode === 'create' && (
        <ReminderDialog
          mode="create"
          onSubmit={handleCreateReminder}
          onClose={() => setDialogMode(null)}
        />
      )}

      {/* Edit dialog */}
      {dialogMode === 'edit' && editingReminder && (
        <ReminderDialog
          mode="edit"
          initialData={{
            entityType: editingReminder.entityType,
            entityId: editingReminder.entityId,
            entityLabel: editingReminder.entityLabel,
            scheduledTime: editingReminder.scheduledTime,
            scheduledDate: editingReminder.scheduledDate ?? '',
            recurrenceType: editingReminder.recurrenceType,
            recurrenceWeekdays: editingReminder.recurrenceWeekdays,
            recurrenceIntervalDays: editingReminder.recurrenceIntervalDays,
            conditionType: editingReminder.conditionType,
            escalationIntervalMinutes: editingReminder.escalationIntervalMinutes,
            maxEscalations: editingReminder.maxEscalations,
          }}
          onSubmit={handleEditReminder}
          onClose={() => {
            setDialogMode(null);
            setEditingReminder(null);
          }}
        />
      )}

      {/* Delete confirmation */}
      {dialogMode === 'delete' && deletingReminder && (
        <DeleteReminderDialog
          reminder={deletingReminder}
          onConfirm={handleDeleteReminder}
          onClose={() => {
            setDialogMode(null);
            setDeletingReminder(null);
          }}
        />
      )}
    </main>
  );
}
