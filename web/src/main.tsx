import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './ui/styles/design-tokens.css'; // Design system tokens (must load before component styles)
import './ui/i18n/i18n'; // Initialize i18n before rendering
import App from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
