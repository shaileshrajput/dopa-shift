import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router-dom';
import './NavigationDrawer.css';

interface NavItem {
  path: string;
  labelKey: string;
}

const NAV_ITEMS: NavItem[] = [
  { path: '/dashboard', labelKey: 'nav.dashboard' },
  { path: '/goals', labelKey: 'nav.goals' },
  { path: '/tasks', labelKey: 'nav.tasks' },
  { path: '/habits', labelKey: 'nav.habits' },
  { path: '/analytics', labelKey: 'nav.analytics' },
  { path: '/notifications', labelKey: 'nav.notifications' },
  { path: '/settings', labelKey: 'nav.settings' },
];

export default function NavigationDrawer() {
  const { t } = useTranslation();

  return (
    <nav className="nav-drawer" aria-label={t('app.name')}>
      <div className="nav-drawer__header">
        <span className="nav-drawer__brand">{t('app.name')}</span>
      </div>
      <ul className="nav-drawer__list" role="list">
        {NAV_ITEMS.map((item) => (
          <li key={item.path} className="nav-drawer__item">
            <NavLink
              to={item.path}
              className={({ isActive }) =>
                `nav-drawer__link${isActive ? ' nav-drawer__link--active' : ''}`
              }
            >
              {t(item.labelKey)}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
