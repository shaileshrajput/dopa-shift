/**
 * React hook for subscribing to Dexie liveQuery observables.
 *
 * Dexie's `liveQuery()` creates a reactive query that automatically re-emits
 * whenever any of the Dexie tables touched by the querier function are mutated.
 * This provides sub-second (typically < 50ms) propagation of local DB changes
 * to the UI — well within the 2-second requirement.
 *
 * Validates: Requirements 14.4 (reactive/observer-based local data propagation)
 */

import { useEffect, useReducer, useRef } from 'react';
import { liveQuery, type Observable as DexieObservable } from 'dexie';

/** State of a live query subscription. */
export interface LiveQueryState<T> {
  /** Current data value, or undefined while the first result is loading. */
  data: T | undefined;
  /** Whether the initial query is still loading (no data emitted yet). */
  loading: boolean;
  /** Error from the query, if any. */
  error: Error | undefined;
}

type Action<T> =
  | { type: 'data'; value: T }
  | { type: 'error'; error: Error };

function reducer<T>(state: LiveQueryState<T>, action: Action<T>): LiveQueryState<T> {
  switch (action.type) {
    case 'data':
      return { data: action.value, loading: false, error: undefined };
    case 'error':
      return { ...state, loading: false, error: action.error };
  }
}

/**
 * Subscribe a React component to a Dexie liveQuery.
 *
 * The `querier` function is called once and its table accesses are tracked.
 * Whenever any tracked table is mutated, the querier re-runs and the component
 * re-renders with the new data.
 *
 * @param querier - Async function performing Dexie queries. Dependencies are auto-tracked.
 * @param deps - React dependency array controlling when the subscription is recreated.
 * @param defaultValue - Optional initial value while the first query is pending.
 *
 * @example
 * ```tsx
 * const { data: goals, loading } = useLiveQuery(
 *   () => db.goals.where('userId').equals(userId).toArray(),
 *   [userId],
 * );
 * ```
 */
export function useLiveQuery<T>(
  querier: () => T | Promise<T>,
  deps: React.DependencyList,
  defaultValue?: T,
): LiveQueryState<T> {
  const initialState: LiveQueryState<T> = {
    data: defaultValue,
    loading: defaultValue === undefined,
    error: undefined,
  };

  const [state, dispatch] = useReducer(reducer<T>, initialState);

  // Keep a ref to the querier so subscription logic always calls the latest version
  const querierRef = useRef(querier);
  querierRef.current = querier;

  useEffect(() => {
    // Create a Dexie liveQuery observable that auto-tracks table dependencies
    const observable: DexieObservable<T> = liveQuery(() => querierRef.current());

    const subscription = observable.subscribe({
      next: (value) => dispatch({ type: 'data', value }),
      error: (err) =>
        dispatch({
          type: 'error',
          error: err instanceof Error ? err : new Error(String(err)),
        }),
    });

    return () => subscription.unsubscribe();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return state;
}
