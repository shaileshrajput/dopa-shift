import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import AppLayout from '../components/AppLayout';
import { isOnboardingComplete } from '../hooks/useOnboardingComplete';

// Route-based code-splitting via React.lazy
const DashboardPage = lazy(() => import('../pages/DashboardPage'));
const GoalsPage = lazy(() => import('../pages/GoalsPage'));
const TasksPage = lazy(() => import('../pages/TasksPage'));
const HabitsPage = lazy(() => import('../pages/HabitsPage'));
const AnalyticsPage = lazy(() => import('../pages/AnalyticsPage'));
const NotificationsPage = lazy(() => import('../pages/NotificationsPage'));
const SettingsPage = lazy(() => import('../pages/SettingsPage'));
const OnboardingPage = lazy(() => import('../pages/OnboardingPage'));

function LoadingFallback() {
  const { t } = useTranslation();
  return (
    <div role="status" aria-live="polite">
      {t('common.loading')}
    </div>
  );
}

/**
 * Redirects to /onboarding if onboarding has not been completed.
 */
function RequireOnboarding({ children }: { children: React.ReactElement }) {
  if (!isOnboardingComplete()) {
    return <Navigate to="/onboarding" replace />;
  }
  return children;
}

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          <Route path="/onboarding" element={<OnboardingPage />} />
          <Route
            element={
              <RequireOnboarding>
                <AppLayout />
              </RequireOnboarding>
            }
          >
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/goals" element={<GoalsPage />} />
            <Route path="/tasks" element={<TasksPage />} />
            <Route path="/habits" element={<HabitsPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
