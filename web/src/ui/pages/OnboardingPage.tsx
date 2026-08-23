/**
 * Onboarding flow — 4 screens guiding first-time users through
 * goal creation and notification permissions setup.
 *
 * Validates: Requirements 17.4
 */

import { useState, useCallback, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { db } from '@data/db';
import { useOnboardingComplete } from '../hooks/useOnboardingComplete';
import './OnboardingPage.css';

// ─── Constants ───────────────────────────────────────────────────────────────

const TOTAL_STEPS = 4;
const STUB_USER_ID = 'current-user'; // Placeholder until auth context is wired
const MAX_GOAL_NAME = 100;
const MAX_CATEGORY = 50;
const MAX_KEYWORD = 50;

// ─── Step Components ─────────────────────────────────────────────────────────

function WelcomeStep({ onNext }: { onNext: () => void }) {
  const { t } = useTranslation();

  return (
    <>
      <div className="onboarding__content">
        <div className="onboarding__icon" aria-hidden="true">
          🚀
        </div>
        <h1 className="onboarding__title">{t('onboarding.welcome')}</h1>
        <p className="onboarding__description">{t('onboarding.welcomeDescription')}</p>
      </div>
      <div className="onboarding__actions">
        <button
          className="onboarding__btn onboarding__btn--primary"
          onClick={onNext}
          type="button"
        >
          {t('onboarding.getStarted')}
        </button>
      </div>
    </>
  );
}

interface CreateGoalStepProps {
  onNext: () => void;
  onBack: () => void;
}

function CreateGoalStep({ onNext, onBack }: CreateGoalStepProps) {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [category, setCategory] = useState('');
  const [keyword, setKeyword] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const handleSubmit = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      setError('');

      const trimmedName = name.trim();
      const trimmedCategory = category.trim();
      const trimmedKeyword = keyword.trim();

      if (!trimmedName) {
        setError(t('errors.goalNameRequired'));
        return;
      }
      if (!trimmedCategory) {
        setError(t('errors.categoryRequired'));
        return;
      }
      if (!trimmedKeyword) {
        setError(t('errors.keywordRequired'));
        return;
      }

      setSaving(true);
      try {
        // Check for duplicate name
        const existing = await db.goals
          .where('name')
          .equals(trimmedName)
          .and((g) => g.userId === STUB_USER_ID)
          .first();

        if (existing) {
          setError(t('goals.nameTaken'));
          setSaving(false);
          return;
        }

        const now = new Date().toISOString();
        await db.goals.add({
          id: crypto.randomUUID(),
          userId: STUB_USER_ID,
          name: trimmedName,
          category: trimmedCategory,
          keywords: [trimmedKeyword],
          isActive: true,
          createdAt: now,
          updatedAt: now,
        });

        onNext();
      } catch {
        setError(t('errors.generic'));
      } finally {
        setSaving(false);
      }
    },
    [name, category, keyword, t, onNext],
  );

  return (
    <>
      <div className="onboarding__content">
        <div className="onboarding__icon" aria-hidden="true">
          🎯
        </div>
        <h1 className="onboarding__title">{t('onboarding.createGoalTitle')}</h1>
        <p className="onboarding__description">{t('onboarding.createGoalDescription')}</p>
      </div>

      <form className="onboarding__form" onSubmit={handleSubmit} noValidate>
        <div className="onboarding__field">
          <label className="onboarding__label" htmlFor="onboarding-goal-name">
            {t('onboarding.goalNameLabel')}
          </label>
          <input
            id="onboarding-goal-name"
            className="onboarding__input"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('onboarding.goalNamePlaceholder')}
            maxLength={MAX_GOAL_NAME}
            autoFocus
            required
          />
        </div>

        <div className="onboarding__field">
          <label className="onboarding__label" htmlFor="onboarding-category">
            {t('onboarding.categoryLabel')}
          </label>
          <input
            id="onboarding-category"
            className="onboarding__input"
            type="text"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            placeholder={t('onboarding.categoryPlaceholder')}
            maxLength={MAX_CATEGORY}
            required
          />
        </div>

        <div className="onboarding__field">
          <label className="onboarding__label" htmlFor="onboarding-keyword">
            {t('onboarding.keywordLabel')}
          </label>
          <input
            id="onboarding-keyword"
            className="onboarding__input"
            type="text"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder={t('onboarding.keywordPlaceholder')}
            maxLength={MAX_KEYWORD}
            required
          />
        </div>

        {error && (
          <p className="onboarding__error" role="alert">
            {error}
          </p>
        )}

        <div className="onboarding__actions">
          <button
            className="onboarding__btn onboarding__btn--secondary"
            onClick={onBack}
            type="button"
          >
            {t('onboarding.back')}
          </button>
          <button
            className="onboarding__btn onboarding__btn--primary"
            type="submit"
            disabled={saving}
          >
            {t('onboarding.next')}
          </button>
        </div>
      </form>
    </>
  );
}

interface PermissionsStepProps {
  onNext: () => void;
  onBack: () => void;
}

function PermissionsStep({ onNext, onBack }: PermissionsStepProps) {
  const { t } = useTranslation();
  const [permissionGranted, setPermissionGranted] = useState(false);
  const [requesting, setRequesting] = useState(false);

  const requestNotificationPermission = useCallback(async () => {
    if (!('Notification' in window)) {
      // Notifications not supported, skip gracefully
      onNext();
      return;
    }

    setRequesting(true);
    try {
      const result = await Notification.requestPermission();
      setPermissionGranted(result === 'granted');
    } catch {
      // If permission request throws, skip gracefully
    } finally {
      setRequesting(false);
    }
  }, [onNext]);

  return (
    <>
      <div className="onboarding__content">
        <div className="onboarding__icon" aria-hidden="true">
          🔔
        </div>
        <h1 className="onboarding__title">{t('onboarding.permissionsTitle')}</h1>
        <p className="onboarding__description">{t('onboarding.permissionsDescription')}</p>
        {permissionGranted && (
          <p className="onboarding__permission-status onboarding__permission-status--granted">
            {t('onboarding.notificationsEnabled')}
          </p>
        )}
      </div>

      <div className="onboarding__actions">
        <button
          className="onboarding__btn onboarding__btn--secondary"
          onClick={onBack}
          type="button"
        >
          {t('onboarding.back')}
        </button>
        {!permissionGranted ? (
          <button
            className="onboarding__btn onboarding__btn--primary"
            onClick={requestNotificationPermission}
            disabled={requesting}
            type="button"
          >
            {t('onboarding.enableNotifications')}
          </button>
        ) : (
          <button
            className="onboarding__btn onboarding__btn--primary"
            onClick={onNext}
            type="button"
          >
            {t('onboarding.next')}
          </button>
        )}
      </div>
      {!permissionGranted && (
        <button
          className="onboarding__btn onboarding__btn--outline"
          onClick={onNext}
          type="button"
        >
          {t('onboarding.skip')}
        </button>
      )}
    </>
  );
}

interface DoneStepProps {
  onFinish: () => void;
}

function DoneStep({ onFinish }: DoneStepProps) {
  const { t } = useTranslation();

  return (
    <>
      <div className="onboarding__content">
        <div className="onboarding__icon" aria-hidden="true">
          ✅
        </div>
        <h1 className="onboarding__title">{t('onboarding.doneTitle')}</h1>
        <p className="onboarding__description">{t('onboarding.doneDescription')}</p>
      </div>
      <div className="onboarding__actions">
        <button
          className="onboarding__btn onboarding__btn--primary"
          onClick={onFinish}
          type="button"
        >
          {t('onboarding.goToDashboard')}
        </button>
      </div>
    </>
  );
}

// ─── Main Onboarding Page ────────────────────────────────────────────────────

export default function OnboardingPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { markComplete } = useOnboardingComplete();
  const [step, setStep] = useState(1);

  const handleNext = useCallback(() => {
    setStep((s) => Math.min(s + 1, TOTAL_STEPS));
  }, []);

  const handleBack = useCallback(() => {
    setStep((s) => Math.max(s - 1, 1));
  }, []);

  const handleFinish = useCallback(() => {
    markComplete();
    navigate('/dashboard', { replace: true });
  }, [markComplete, navigate]);

  const progressPercent = (step / TOTAL_STEPS) * 100;

  return (
    <main className="onboarding" aria-label={t('onboarding.welcome')}>
      <div className="onboarding__card">
        <div className="onboarding__step-indicator" aria-live="polite">
          {t('onboarding.step', { current: step, total: TOTAL_STEPS })}
        </div>

        <div className="onboarding__progress" role="progressbar" aria-valuenow={step} aria-valuemin={1} aria-valuemax={TOTAL_STEPS}>
          <div
            className="onboarding__progress-fill"
            style={{ width: `${progressPercent}%` }}
          />
        </div>

        {step === 1 && <WelcomeStep onNext={handleNext} />}
        {step === 2 && <CreateGoalStep onNext={handleNext} onBack={handleBack} />}
        {step === 3 && <PermissionsStep onNext={handleNext} onBack={handleBack} />}
        {step === 4 && <DoneStep onFinish={handleFinish} />}
      </div>
    </main>
  );
}
