import { Outlet } from 'react-router-dom';
import NavigationDrawer from './NavigationDrawer';
import './AppLayout.css';

export default function AppLayout() {
  return (
    <div className="app-layout">
      <NavigationDrawer />
      <div className="app-layout__content">
        <Outlet />
      </div>
    </div>
  );
}
