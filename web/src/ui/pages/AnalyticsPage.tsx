/**
 * Analytics page — efficiency score bar chart, date range selector,
 * today's score display, "no data" indicator, and lazy-loaded paginated data.
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 16.4
 */

import { useState, useCallback, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { analyticsApi, type EfficiencyScore } from '@data/api/endpoints';
import './AnalyticsPage.css';

// ─── Constants ───────────────────────────────────────────────────────────────

/** Max records per lazy-loaded page (Req 7.3: paginated data). */
const PAGE_SIZE = 50;

type DateRangePreset = '7' | '30' | '90' | 'custom';

/** Get today as YYYY-MM-DD. */
function getTodayDate(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Subtract N days from today and return as YYYY-MM-DD. */
function getDateNDaysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

/** Format a date string to short display (e.g., "Jan 5" or "1/5"). */
function formatShortDate(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00');
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

/** Format seconds to a human-readable duration. */
function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const remainingMins = minutes % 60;
  return remainingMins > 0 ? `${hours}h ${remainingMins}m` : `${hours}h`;
}

// ─── Today's Score Ring ──────────────────────────────────────────────────────

interface TodayScoreProps {
  score: EfficiencyScore | null;
}

function TodayScoreCard({ score }: TodayScoreProps) {
  const { t } = useTranslation();
  const radius = 34;
  const circumference = 2 * Math.PI * radius;
  const percent = score?.scorePercent ?? 0;
  const offset = circumference - (percent / 100) * circumference;
  const hasData = score !== null && score.scorePercent !== null;

  return (
    <div className="analytics__today-card" aria-label={t('analytics.todaysScore')}>
      <div className="analytics__today-ring">
        <svg className="analytics__today-ring-svg" viewBox="0 0 80 80" aria-hidden="true">
          <circle className="analytics__today-ring-track" cx="40" cy="40" r={radius} />
          <circle
            className="analytics__today-ring-fill"
            cx="40"
            cy="40"
            r={radius}
            strokeDasharray={circumference}
            strokeDashoffset={hasData ? offset : circumference}
          />
        </svg>
        <span className="analytics__today-ring-value">
          {hasData ? `${score.scorePercent}%` : '—'}
        </span>
      </div>
      <div className="analytics__today-details">
        <span className="analytics__today-label">{t('analytics.todaysScore')}</span>
        <span className="analytics__today-score-text">
          {hasData ? `${score.scorePercent}%` : t('analytics.noDataForDay')}
        </span>
        {hasData && (
          <span className="analytics__today-subtitle">
            {formatDuration(score.productiveSeconds)} {t('analytics.productiveTime').toLowerCase()}
            {' / '}
            {formatDuration(score.totalTrackedSeconds)} {t('analytics.totalTracked').toLowerCase()}
          </span>
        )}
      </div>
    </div>
  );
}

// ─── Bar Chart ───────────────────────────────────────────────────────────────

interface BarChartProps {
  scores: EfficiencyScore[];
}

function EfficiencyBarChart({ scores }: BarChartProps) {
  const { t } = useTranslation();
  const chartHeight = 160; // px — maximum bar height

  if (scores.length === 0) {
    return (
      <div className="analytics__bar-chart">
        <p className="analytics__no-data">{t('common.noData')}</p>
      </div>
    );
  }

  return (
    <div className="analytics__bar-chart" role="img" aria-label={t('analytics.efficiencyScore')}>
      <div className="analytics__bar-chart-inner" style={{ height: `${chartHeight + 48}px` }}>
        {scores.map((entry) => {
          const hasData = entry.scorePercent !== null;
          const barHeight = hasData
            ? Math.max(4, (entry.scorePercent! / 100) * chartHeight)
            : 4;

          return (
            <div key={entry.date} className="analytics__bar-wrapper">
              <span className="analytics__bar-value">
                {hasData ? `${entry.scorePercent}%` : t('analytics.noDataForDay')}
              </span>
              <div
                className={`analytics__bar ${!hasData ? 'analytics__bar--no-data' : ''}`}
                style={{ height: `${barHeight}px` }}
                title={
                  hasData
                    ? `${formatShortDate(entry.date)}: ${entry.scorePercent}%`
                    : `${formatShortDate(entry.date)}: ${t('analytics.noDataForDay')}`
                }
                aria-label={
                  hasData
                    ? `${formatShortDate(entry.date)}: ${entry.scorePercent}%`
                    : `${formatShortDate(entry.date)}: ${t('analytics.noDataForDay')}`
                }
              />
              <span className="analytics__bar-date">{formatShortDate(entry.date)}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Summary Stats ───────────────────────────────────────────────────────────

interface SummaryStatsProps {
  scores: EfficiencyScore[];
}

function SummaryStats({ scores }: SummaryStatsProps) {
  const { t } = useTranslation();

  const validScores = scores.filter((s) => s.scorePercent !== null);

  const average =
    validScores.length > 0
      ? Math.round(validScores.reduce((sum, s) => sum + s.scorePercent!, 0) / validScores.length)
      : null;

  const totalProductiveSeconds = scores.reduce((sum, s) => sum + s.productiveSeconds, 0);
  const totalTrackedSeconds = scores.reduce((sum, s) => sum + s.totalTrackedSeconds, 0);

  return (
    <div className="analytics__summary" aria-label={t('analytics.title')}>
      <div className="analytics__stat-card">
        <span className="analytics__stat-label">{t('analytics.weeklyAverage')}</span>
        <span className="analytics__stat-value">
          {average !== null ? `${average}%` : '—'}
        </span>
      </div>
      <div className="analytics__stat-card">
        <span className="analytics__stat-label">{t('analytics.productiveTime')}</span>
        <span className="analytics__stat-value">{formatDuration(totalProductiveSeconds)}</span>
      </div>
      <div className="analytics__stat-card">
        <span className="analytics__stat-label">{t('analytics.totalTracked')}</span>
        <span className="analytics__stat-value">{formatDuration(totalTrackedSeconds)}</span>
      </div>
    </div>
  );
}

// ─── Main Analytics Page ─────────────────────────────────────────────────────

export default function AnalyticsPage() {
  const { t } = useTranslation();
  const today = getTodayDate();

  // ─── Date Range State ──────────────────────────────────────────────────────

  const [preset, setPreset] = useState<DateRangePreset>('30');
  const [customFrom, setCustomFrom] = useState(getDateNDaysAgo(30));
  const [customTo, setCustomTo] = useState(today);

  const { fromDate, toDate } = useMemo(() => {
    switch (preset) {
      case '7':
        return { fromDate: getDateNDaysAgo(7), toDate: today };
      case '30':
        return { fromDate: getDateNDaysAgo(30), toDate: today };
      case '90':
        return { fromDate: getDateNDaysAgo(90), toDate: today };
      case 'custom':
        return { fromDate: customFrom, toDate: customTo };
    }
  }, [preset, customFrom, customTo, today]);

  // ─── Scores State ─────────────────────────────────────────────────────────

  const [scores, setScores] = useState<EfficiencyScore[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [offlineNotice, setOfflineNotice] = useState(false);

  // ─── Pagination ────────────────────────────────────────────────────────────

  const [page, setPage] = useState(1);

  const totalPages = useMemo(() => {
    // Calculate total days in range for pagination
    const start = new Date(fromDate + 'T00:00:00');
    const end = new Date(toDate + 'T00:00:00');
    const totalDays = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / 86400000) + 1);
    return Math.ceil(totalDays / PAGE_SIZE);
  }, [fromDate, toDate]);

  // Reset page when range changes
  useEffect(() => {
    setPage(1);
  }, [fromDate, toDate]);

  // ─── Data Loading (lazy-loaded paginated from API) ─────────────────────────

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setOfflineNotice(false);

    async function fetchScores() {
      try {
        // Calculate paginated date range
        const start = new Date(fromDate + 'T00:00:00');
        const end = new Date(toDate + 'T00:00:00');
        const totalDays = Math.ceil((end.getTime() - start.getTime()) / 86400000) + 1;

        // For the current page, calculate the sub-range of dates
        const pageStart = (page - 1) * PAGE_SIZE;
        const pageEnd = Math.min(pageStart + PAGE_SIZE, totalDays);

        const pageFromDate = new Date(start);
        pageFromDate.setDate(pageFromDate.getDate() + pageStart);
        const pageToDate = new Date(start);
        pageToDate.setDate(pageToDate.getDate() + pageEnd - 1);

        // Clamp to the overall range
        const effectiveFrom = pageFromDate.toISOString().slice(0, 10);
        const effectiveTo = pageToDate > end
          ? toDate
          : pageToDate.toISOString().slice(0, 10);

        const data = await analyticsApi.getEfficiency(effectiveFrom, effectiveTo);
        if (!cancelled) {
          setScores(data);
          setLoading(false);
        }
      } catch {
        if (!cancelled) {
          // Req 7.4: Show offline notice if backend is unreachable
          setOfflineNotice(true);
          setScores([]);
          setLoading(false);
          setError(t('analytics.offlineNotice'));
        }
      }
    }

    fetchScores();
    return () => { cancelled = true; };
  }, [fromDate, toDate, page, t]);

  // ─── Today's Score ─────────────────────────────────────────────────────────

  const [todayScore, setTodayScore] = useState<EfficiencyScore | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadTodayScore() {
      try {
        const data = await analyticsApi.getEfficiency(today, today);
        if (!cancelled && data.length > 0) {
          setTodayScore(data[0]);
        }
      } catch {
        // Silently fail — today's score will show as no data
      }
    }

    loadTodayScore();
    return () => { cancelled = true; };
  }, [today]);

  // ─── Handlers ──────────────────────────────────────────────────────────────

  const handlePresetChange = useCallback((newPreset: DateRangePreset) => {
    setPreset(newPreset);
  }, []);

  const handlePrevPage = useCallback(() => {
    setPage((p) => Math.max(1, p - 1));
  }, []);

  const handleNextPage = useCallback(() => {
    setPage((p) => Math.min(totalPages, p + 1));
  }, [totalPages]);

  // ─── Render ────────────────────────────────────────────────────────────────

  return (
    <main className="analytics">
      {/* ─── Header + Date Range Selector ─── */}
      <header className="analytics__header">
        <h1 className="analytics__title">{t('analytics.title')}</h1>
        <nav className="analytics__date-range" aria-label={t('analytics.customRange')}>
          <button
            type="button"
            className={`analytics__range-btn ${preset === '7' ? 'analytics__range-btn--active' : ''}`}
            onClick={() => handlePresetChange('7')}
          >
            {t('analytics.last7Days')}
          </button>
          <button
            type="button"
            className={`analytics__range-btn ${preset === '30' ? 'analytics__range-btn--active' : ''}`}
            onClick={() => handlePresetChange('30')}
          >
            {t('analytics.last30Days')}
          </button>
          <button
            type="button"
            className={`analytics__range-btn ${preset === '90' ? 'analytics__range-btn--active' : ''}`}
            onClick={() => handlePresetChange('90')}
          >
            {t('analytics.last90Days')}
          </button>
          <button
            type="button"
            className={`analytics__range-btn ${preset === 'custom' ? 'analytics__range-btn--active' : ''}`}
            onClick={() => handlePresetChange('custom')}
          >
            {t('analytics.customRange')}
          </button>
        </nav>
      </header>

      {/* ─── Custom Range Inputs ─── */}
      {preset === 'custom' && (
        <div className="analytics__custom-range">
          <label>
            <span className="sr-only">{t('analytics.startDate')}</span>
            <input
              type="date"
              className="analytics__date-input"
              value={customFrom}
              max={customTo}
              onChange={(e) => setCustomFrom(e.target.value)}
              aria-label={t('analytics.startDate')}
            />
          </label>
          <span className="analytics__date-separator">—</span>
          <label>
            <span className="sr-only">{t('analytics.endDate')}</span>
            <input
              type="date"
              className="analytics__date-input"
              value={customTo}
              min={customFrom}
              max={today}
              onChange={(e) => setCustomTo(e.target.value)}
              aria-label={t('analytics.endDate')}
            />
          </label>
        </div>
      )}

      {/* ─── Today's Score ─── */}
      <TodayScoreCard score={todayScore} />

      {/* ─── Offline Notice (Req 7.4) ─── */}
      {offlineNotice && (
        <div className="analytics__offline-notice" role="status" aria-live="polite">
          {t('analytics.offlineNotice')}
        </div>
      )}

      {/* ─── Loading State ─── */}
      {loading && (
        <div className="analytics__loading" role="status" aria-live="polite">
          {t('common.loading')}
        </div>
      )}

      {/* ─── Chart and Stats ─── */}
      {!loading && (
        <>
          {/* ─── Efficiency Bar Chart (Req 7.3) ─── */}
          <section className="analytics__chart-section" aria-label={t('analytics.efficiencyScore')}>
            <h2 className="analytics__section-title">{t('analytics.efficiencyScore')}</h2>
            <EfficiencyBarChart scores={scores} />
          </section>

          {/* ─── Summary Stats ─── */}
          <SummaryStats scores={scores} />

          {/* ─── Pagination Controls (lazy-loaded paginated data) ─── */}
          {totalPages > 1 && (
            <nav className="analytics__pagination" aria-label="Pagination">
              <button
                type="button"
                className="analytics__page-btn"
                onClick={handlePrevPage}
                disabled={page <= 1}
                aria-label={t('common.back')}
              >
                &larr; {t('common.back')}
              </button>
              <span className="analytics__page-info">
                {page} / {totalPages}
              </span>
              <button
                type="button"
                className="analytics__page-btn"
                onClick={handleNextPage}
                disabled={page >= totalPages}
                aria-label={t('common.next')}
              >
                {t('common.next')} &rarr;
              </button>
            </nav>
          )}
        </>
      )}

      {/* ─── Error State (only when scores fail and no offline notice) ─── */}
      {!loading && error && !offlineNotice && (
        <div className="analytics__error" role="alert">
          <p className="analytics__error-text">{error}</p>
        </div>
      )}
    </main>
  );
}
