import { useState, useCallback } from 'react';

const ONBOARDING_KEY = 'dopashift_onboarding_complete';

/**
 * Hook to track onboarding completion status via localStorage.
 * Returns the current completion state and a function to mark onboarding done.
 */
export function useOnboardingComplete(): {
  isComplete: boolean;
  markComplete: () => void;
} {
  const [isComplete, setIsComplete] = useState<boolean>(() => {
    return localStorage.getItem(ONBOARDING_KEY) === 'true';
  });

  const markComplete = useCallback(() => {
    localStorage.setItem(ONBOARDING_KEY, 'true');
    setIsComplete(true);
  }, []);

  return { isComplete, markComplete };
}

/**
 * Checks onboarding completion synchronously (for router guards).
 */
export function isOnboardingComplete(): boolean {
  return localStorage.getItem(ONBOARDING_KEY) === 'true';
}
