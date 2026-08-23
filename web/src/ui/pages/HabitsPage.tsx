import { useTranslation } from 'react-i18next';

/** Habit Roadmap page — implemented in task 17.5. */
export default function HabitsPage() {
  const { t } = useTranslation();
  return (
    <main>
      <h1>{t('nav.habits')}</h1>
    </main>
  );
}
