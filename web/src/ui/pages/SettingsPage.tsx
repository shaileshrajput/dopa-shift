/**
 * Settings page — profile, LLM config, locale, accent color, quiet hours.
 *
 * Sections:
 * - Profile: display name, avatar placeholder
 * - Password change form (via Keycloak Account Management API)
 * - Accent color picker (12+ predefined swatches + custom hex)
 * - Locale selection (en, hi, mr) — uses i18n.changeLanguage()
 * - LLM provider config (dropdown + API key + validate button)
 * - Quiet hours configuration
 *
 * Validates: Requirements 15.1, 19.1, 19.7, 19.12, 19.13, 12.1
 */

import { useState, useCallback, useEffect, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { apiClient } from '@data/api';
import './SettingsPage.css';

// ─── Constants ───────────────────────────────────────────────────────────────

/** Predefined accent color swatches (12+ colors). */
const ACCENT_SWATCHES = [
  '#0066FF', // Productive Blue (default)
  '#2DD4BF', // Momentum Teal
  '#6366F1', // Focus Indigo
  '#10B981', // Success Green
  '#F59E0B', // Warning Amber
  '#E11D48', // Rose
  '#8B5CF6', // Purple
  '#06B6D4', // Cyan
  '#F97316', // Orange
  '#EC4899', // Pink
  '#14B8A6', // Teal
  '#84CC16', // Lime
  '#A855F7', // Violet
] as const;

type LlmProvider = '' | 'openai' | 'gemini' | 'claude';
type ValidationStatus = 'idle' | 'validating' | 'success' | 'error';

/** Providers that have web search/browsing capability (informational, Req 15.9). */
const WEB_SEARCH_PROVIDERS: LlmProvider[] = ['openai', 'gemini'];

const HEX_COLOR_REGEX = /^#[0-9A-Fa-f]{6}$/;

const SUPPORTED_LOCALES = [
  { code: 'en', labelKey: 'settings.languageEn' },
  { code: 'hi', labelKey: 'settings.languageHi' },
  { code: 'mr', labelKey: 'settings.languageMr' },
] as const;

// ─── Profile Section ─────────────────────────────────────────────────────────

interface ProfileSectionProps {
  displayName: string;
  onDisplayNameChange: (name: string) => void;
}

function ProfileSection({ displayName, onDisplayNameChange }: ProfileSectionProps) {
  const { t } = useTranslation();

  // Generate initials for the default avatar
  const initials = displayName
    .split(' ')
    .map((w) => w[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase() || '?';

  return (
    <section className="settings-section" aria-labelledby="settings-profile-heading">
      <h2 id="settings-profile-heading" className="settings-section__title">
        {t('settings.profile')}
      </h2>
      <div className="settings-profile">
        <div className="settings-profile__avatar" aria-label={t('settings.profilePhoto')}>
          {initials}
        </div>
        <div className="settings-profile__info">
          <div className="settings-field">
            <label className="settings-field__label" htmlFor="display-name">
              {t('settings.displayName')}
            </label>
            <input
              id="display-name"
              className="settings-profile__name-input"
              type="text"
              value={displayName}
              onChange={(e) => onDisplayNameChange(e.target.value)}
              placeholder={t('settings.displayNamePlaceholder')}
              maxLength={100}
            />
          </div>
        </div>
      </div>
    </section>
  );
}

// ─── Password Change Section ─────────────────────────────────────────────────

function PasswordSection() {
  const { t } = useTranslation();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      setError('');
      setSuccess('');

      if (newPassword !== confirmPassword) {
        setError(t('settings.passwordMismatch'));
        return;
      }

      if (!currentPassword || !newPassword) {
        setError(t('common.required'));
        return;
      }

      setSubmitting(true);
      try {
        await apiClient.post('/auth/change-password', {
          currentPassword,
          newPassword,
        });
        setSuccess(t('settings.passwordChanged'));
        setCurrentPassword('');
        setNewPassword('');
        setConfirmPassword('');
      } catch {
        setError(t('settings.passwordChangeFailed'));
      } finally {
        setSubmitting(false);
      }
    },
    [currentPassword, newPassword, confirmPassword, t],
  );

  return (
    <section className="settings-section" aria-labelledby="settings-password-heading">
      <h2 id="settings-password-heading" className="settings-section__title">
        {t('settings.changePassword')}
      </h2>
      <form className="settings-password" onSubmit={handleSubmit}>
        <div className="settings-field">
          <label className="settings-field__label" htmlFor="current-password">
            {t('settings.currentPassword')}
          </label>
          <input
            id="current-password"
            className="settings-field__input"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            placeholder={t('settings.currentPasswordPlaceholder')}
            autoComplete="current-password"
          />
        </div>
        <div className="settings-field">
          <label className="settings-field__label" htmlFor="new-password">
            {t('settings.newPassword')}
          </label>
          <input
            id="new-password"
            className="settings-field__input"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder={t('settings.newPasswordPlaceholder')}
            autoComplete="new-password"
          />
        </div>
        <div className="settings-field">
          <label className="settings-field__label" htmlFor="confirm-password">
            {t('settings.confirmPassword')}
          </label>
          <input
            id="confirm-password"
            className="settings-field__input"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder={t('settings.confirmPasswordPlaceholder')}
            autoComplete="new-password"
          />
        </div>
        {error && (
          <p className="settings-password__error" role="alert">
            {error}
          </p>
        )}
        {success && (
          <p className="settings-password__success" role="status">
            {success}
          </p>
        )}
        <button
          type="submit"
          className="settings-btn settings-btn--primary"
          disabled={submitting}
        >
          {t('settings.changePassword')}
        </button>
      </form>
    </section>
  );
}

// ─── Accent Color Section ────────────────────────────────────────────────────

interface AccentColorSectionProps {
  selectedColor: string;
  onColorChange: (color: string) => void;
}

function AccentColorSection({ selectedColor, onColorChange }: AccentColorSectionProps) {
  const { t } = useTranslation();
  const [customHex, setCustomHex] = useState('');
  const [customError, setCustomError] = useState(false);

  const isCustom = !ACCENT_SWATCHES.includes(selectedColor as typeof ACCENT_SWATCHES[number]);

  useEffect(() => {
    if (isCustom) {
      setCustomHex(selectedColor);
    }
  }, [isCustom, selectedColor]);

  const handleCustomChange = useCallback(
    (value: string) => {
      setCustomHex(value);
      if (HEX_COLOR_REGEX.test(value)) {
        setCustomError(false);
        onColorChange(value);
      } else {
        setCustomError(value.length > 0);
      }
    },
    [onColorChange],
  );

  return (
    <section className="settings-section" aria-labelledby="settings-accent-heading">
      <h2 id="settings-accent-heading" className="settings-section__title">
        {t('settings.accentColor')}
      </h2>
      <div className="settings-colors">
        <div className="settings-colors__swatches" role="radiogroup" aria-label={t('settings.accentColor')}>
          {ACCENT_SWATCHES.map((color) => (
            <button
              key={color}
              type="button"
              className={`settings-colors__swatch${selectedColor === color ? ' settings-colors__swatch--selected' : ''}`}
              style={{ backgroundColor: color }}
              onClick={() => {
                onColorChange(color);
                setCustomHex('');
                setCustomError(false);
              }}
              aria-label={color}
              aria-checked={selectedColor === color}
              role="radio"
            />
          ))}
        </div>
        <div className="settings-colors__custom">
          <label className="settings-field__label" htmlFor="custom-color-hex">
            {t('settings.accentColorCustom')}:
          </label>
          <input
            id="custom-color-hex"
            className={`settings-colors__custom-input${customError ? ' settings-colors__custom-input--invalid' : ''}`}
            type="text"
            value={customHex}
            onChange={(e) => handleCustomChange(e.target.value)}
            placeholder={t('settings.accentColorHexPlaceholder')}
            maxLength={7}
            spellCheck={false}
          />
          {customHex && HEX_COLOR_REGEX.test(customHex) && (
            <span
              className="settings-colors__custom-preview"
              style={{ backgroundColor: customHex }}
              aria-hidden="true"
            />
          )}
        </div>
        {customError && (
          <p className="settings-field__error" role="alert">
            {t('settings.accentColorInvalid')}
          </p>
        )}
      </div>
    </section>
  );
}

// ─── Locale Section ──────────────────────────────────────────────────────────

interface LocaleSectionProps {
  currentLocale: string;
  onLocaleChange: (locale: string) => void;
}

function LocaleSection({ currentLocale, onLocaleChange }: LocaleSectionProps) {
  const { t } = useTranslation();

  return (
    <section className="settings-section" aria-labelledby="settings-locale-heading">
      <h2 id="settings-locale-heading" className="settings-section__title">
        {t('settings.language')}
      </h2>
      <div className="settings-locale" role="radiogroup" aria-label={t('settings.language')}>
        {SUPPORTED_LOCALES.map(({ code, labelKey }) => (
          <button
            key={code}
            type="button"
            className={`settings-locale__option${currentLocale === code ? ' settings-locale__option--selected' : ''}`}
            onClick={() => onLocaleChange(code)}
            aria-checked={currentLocale === code}
            role="radio"
          >
            {t(labelKey)}
          </button>
        ))}
      </div>
    </section>
  );
}

// ─── LLM Config Section ──────────────────────────────────────────────────────

interface LlmConfigSectionProps {
  provider: LlmProvider;
  apiKey: string;
  onProviderChange: (provider: LlmProvider) => void;
  onApiKeyChange: (key: string) => void;
}

function LlmConfigSection({
  provider,
  apiKey,
  onProviderChange,
  onApiKeyChange,
}: LlmConfigSectionProps) {
  const { t } = useTranslation();
  const [validationStatus, setValidationStatus] = useState<ValidationStatus>('idle');
  const [validationError, setValidationError] = useState('');

  const hasWebSearch = WEB_SEARCH_PROVIDERS.includes(provider);

  const handleValidate = useCallback(async () => {
    if (!provider || !apiKey) return;

    setValidationStatus('validating');
    setValidationError('');

    try {
      await apiClient.post('/llm/validate', { provider, apiKey });
      setValidationStatus('success');
    } catch (err: unknown) {
      setValidationStatus('error');
      // Parse specific error reasons per Requirement 15.8
      if (err && typeof err === 'object' && 'status' in err) {
        const status = (err as { status: number }).status;
        if (status === 408) {
          setValidationError(t('settings.llmTestTimeout'));
        } else if (status === 401) {
          setValidationError(t('settings.llmTestInvalidKey'));
        } else if (status === 429) {
          setValidationError(t('settings.llmTestRateLimit'));
        } else {
          setValidationError(t('settings.llmTestFailed'));
        }
      } else {
        setValidationError(t('settings.llmTestFailed'));
      }
    }
  }, [provider, apiKey, t]);

  return (
    <section className="settings-section" aria-labelledby="settings-llm-heading">
      <h2 id="settings-llm-heading" className="settings-section__title">
        {t('settings.llmConfig')}
      </h2>
      <div className="settings-llm">
        <div className="settings-field">
          <label className="settings-field__label" htmlFor="llm-provider">
            {t('settings.llmProvider')}
          </label>
          <select
            id="llm-provider"
            className="settings-field__select"
            value={provider}
            onChange={(e) => {
              onProviderChange(e.target.value as LlmProvider);
              setValidationStatus('idle');
              setValidationError('');
            }}
          >
            <option value="">{t('settings.llmProviderNone')}</option>
            <option value="openai">{t('settings.llmProviderOpenAI')}</option>
            <option value="gemini">{t('settings.llmProviderGemini')}</option>
            <option value="claude">{t('settings.llmProviderClaude')}</option>
          </select>
        </div>

        {provider && (
          <>
            <div className="settings-llm__row">
              <div className="settings-field">
                <label className="settings-field__label" htmlFor="llm-api-key">
                  {t('settings.llmApiKey')}
                </label>
                <input
                  id="llm-api-key"
                  className="settings-field__input"
                  type="password"
                  value={apiKey}
                  onChange={(e) => {
                    onApiKeyChange(e.target.value);
                    setValidationStatus('idle');
                    setValidationError('');
                  }}
                  placeholder={t('settings.llmApiKeyPlaceholder')}
                  maxLength={256}
                  autoComplete="off"
                />
              </div>
              <button
                type="button"
                className="settings-llm__validate-btn"
                onClick={handleValidate}
                disabled={!apiKey || validationStatus === 'validating'}
              >
                {validationStatus === 'validating'
                  ? t('settings.llmValidating')
                  : t('settings.llmTestConnection')}
              </button>
            </div>

            {validationStatus === 'success' && (
              <p className="settings-llm__status settings-llm__status--success" role="status">
                {t('settings.llmTestSuccess')}
              </p>
            )}
            {validationStatus === 'error' && (
              <p className="settings-llm__status settings-llm__status--error" role="alert">
                {validationError || t('settings.llmTestFailed')}
              </p>
            )}

            {/* Informational: web search capability indicator (Req 15.9) */}
            <div className="settings-llm__web-search-note">
              {hasWebSearch ? (
                <>
                  <span className="settings-llm__web-search-badge">
                    {t('settings.llmWebSearch')}
                  </span>
                  <span>{t('settings.llmWebSearchNote')}</span>
                </>
              ) : (
                <span>{t('settings.llmWebSearchNote')}</span>
              )}
            </div>
          </>
        )}
      </div>
    </section>
  );
}

// ─── Quiet Hours Section ─────────────────────────────────────────────────────

interface QuietHoursSectionProps {
  enabled: boolean;
  startTime: string;
  endTime: string;
  onEnabledChange: (enabled: boolean) => void;
  onStartTimeChange: (time: string) => void;
  onEndTimeChange: (time: string) => void;
}

function QuietHoursSection({
  enabled,
  startTime,
  endTime,
  onEnabledChange,
  onStartTimeChange,
  onEndTimeChange,
}: QuietHoursSectionProps) {
  const { t } = useTranslation();

  return (
    <section className="settings-section" aria-labelledby="settings-quiet-hours-heading">
      <h2 id="settings-quiet-hours-heading" className="settings-section__title">
        {t('settings.quietHours')}
      </h2>
      <div className="settings-quiet-hours">
        <label className="settings-quiet-hours__toggle">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => onEnabledChange(e.target.checked)}
          />
          <span>{t('settings.quietHoursEnabled')}</span>
        </label>
        {enabled && (
          <div className="settings-quiet-hours__times">
            <div className="settings-field">
              <label className="settings-field__label" htmlFor="quiet-start">
                {t('settings.quietHoursStart')}
              </label>
              <input
                id="quiet-start"
                className="settings-field__input"
                type="time"
                value={startTime}
                onChange={(e) => onStartTimeChange(e.target.value)}
              />
            </div>
            <div className="settings-field">
              <label className="settings-field__label" htmlFor="quiet-end">
                {t('settings.quietHoursEnd')}
              </label>
              <input
                id="quiet-end"
                className="settings-field__input"
                type="time"
                value={endTime}
                onChange={(e) => onEndTimeChange(e.target.value)}
              />
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

// ─── Main Settings Page ──────────────────────────────────────────────────────

export default function SettingsPage() {
  const { t, i18n } = useTranslation();

  // ─── State ─────────────────────────────────────────────────────────────────

  const [displayName, setDisplayName] = useState('');
  const [accentColor, setAccentColor] = useState('#0066FF');
  const [llmProvider, setLlmProvider] = useState<LlmProvider>('');
  const [llmApiKey, setLlmApiKey] = useState('');
  const [quietHoursEnabled, setQuietHoursEnabled] = useState(false);
  const [quietHoursStart, setQuietHoursStart] = useState('22:00');
  const [quietHoursEnd, setQuietHoursEnd] = useState('07:00');
  const [showToast, setShowToast] = useState(false);

  // ─── Accent color: apply to CSS custom property immediately ────────────────

  useEffect(() => {
    document.documentElement.style.setProperty('--color-accent', accentColor);
  }, [accentColor]);

  // ─── Locale change: uses i18n.changeLanguage() (no page reload, Req 12.3) ─

  const handleLocaleChange = useCallback(
    (locale: string) => {
      i18n.changeLanguage(locale);
    },
    [i18n],
  );

  // ─── Save handler ──────────────────────────────────────────────────────────

  const handleSave = useCallback(async () => {
    try {
      await apiClient.put('/user/preferences', {
        displayName,
        accentColor,
        locale: i18n.language,
        llmProvider: llmProvider || null,
        quietHours: quietHoursEnabled
          ? { startTime: quietHoursStart, endTime: quietHoursEnd }
          : null,
      });
      setShowToast(true);
      setTimeout(() => setShowToast(false), 3000);
    } catch {
      // Silently handle — error is non-blocking per UX guidelines
    }
  }, [displayName, accentColor, i18n.language, llmProvider, quietHoursEnabled, quietHoursStart, quietHoursEnd]);

  // ─── Render ────────────────────────────────────────────────────────────────

  return (
    <main className="settings-page">
      <h1 className="settings-page__title">{t('settings.title')}</h1>

      <ProfileSection
        displayName={displayName}
        onDisplayNameChange={setDisplayName}
      />

      <PasswordSection />

      <AccentColorSection
        selectedColor={accentColor}
        onColorChange={setAccentColor}
      />

      <LocaleSection
        currentLocale={i18n.language}
        onLocaleChange={handleLocaleChange}
      />

      <LlmConfigSection
        provider={llmProvider}
        apiKey={llmApiKey}
        onProviderChange={setLlmProvider}
        onApiKeyChange={setLlmApiKey}
      />

      <QuietHoursSection
        enabled={quietHoursEnabled}
        startTime={quietHoursStart}
        endTime={quietHoursEnd}
        onEnabledChange={setQuietHoursEnabled}
        onStartTimeChange={setQuietHoursStart}
        onEndTimeChange={setQuietHoursEnd}
      />

      <button
        type="button"
        className="settings-btn settings-btn--primary"
        onClick={handleSave}
      >
        {t('common.save')}
      </button>

      {showToast && (
        <div className="settings-toast" role="status" aria-live="polite">
          {t('settings.saved')}
        </div>
      )}
    </main>
  );
}
