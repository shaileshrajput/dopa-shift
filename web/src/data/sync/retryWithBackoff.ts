/**
 * Retry utility with exponential backoff.
 *
 * Backoff schedule: 1s, 2s, 4s, 8s, 16s (doubling from 1000ms base).
 * Maximum 5 attempts before giving up.
 *
 * Validates: Requirements 4.10
 */

/** Options for configuring retry behavior. */
export interface RetryOptions {
  /** Maximum number of retry attempts (default: 5). */
  maxAttempts?: number;
  /** Base delay in milliseconds (default: 1000). */
  baseDelayMs?: number;
  /** Optional callback invoked before each retry with the attempt number. */
  onRetry?: (attempt: number, error: unknown) => void;
  /** Optional AbortSignal to cancel pending retries. */
  signal?: AbortSignal;
}

const DEFAULT_MAX_ATTEMPTS = 5;
const DEFAULT_BASE_DELAY_MS = 1000;

/**
 * Executes an async operation with exponential backoff retry logic.
 *
 * Delay schedule: baseDelay * 2^(attempt-1)
 * With defaults: 1000, 2000, 4000, 8000, 16000 ms
 *
 * @param fn - The async function to execute.
 * @param options - Retry configuration options.
 * @returns The result of the successful function call.
 * @throws The last error encountered after all retries are exhausted.
 */
export async function retryWithBackoff<T>(
  fn: () => Promise<T>,
  options: RetryOptions = {},
): Promise<T> {
  const {
    maxAttempts = DEFAULT_MAX_ATTEMPTS,
    baseDelayMs = DEFAULT_BASE_DELAY_MS,
    onRetry,
    signal,
  } = options;

  let lastError: unknown;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    if (signal?.aborted) {
      throw new DOMException('Retry aborted', 'AbortError');
    }

    try {
      return await fn();
    } catch (error: unknown) {
      lastError = error;

      if (attempt === maxAttempts) {
        break;
      }

      if (onRetry) {
        onRetry(attempt, error);
      }

      const delayMs = baseDelayMs * Math.pow(2, attempt - 1);
      await delay(delayMs, signal);
    }
  }

  throw lastError;
}

/**
 * Waits for the specified duration, respecting an optional AbortSignal.
 */
function delay(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Delay aborted', 'AbortError'));
      return;
    }

    const timer = setTimeout(resolve, ms);

    if (signal) {
      const onAbort = () => {
        clearTimeout(timer);
        reject(new DOMException('Delay aborted', 'AbortError'));
      };
      signal.addEventListener('abort', onAbort, { once: true });
    }
  });
}
