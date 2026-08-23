/**
 * Service Worker registration for the DopaShift web portal.
 *
 * Registers the service worker on app startup and sets up message
 * handling for background sync requests coming from the SW.
 *
 * Validates: Requirements 5.6
 */

// ─── Types ───────────────────────────────────────────────────────────────────

export interface ServiceWorkerRegistrationResult {
  success: boolean;
  registration?: ServiceWorkerRegistration;
  error?: string;
}

/** Messages the service worker may post to this client. */
interface SWMessage {
  type: string;
  url?: string;
  data?: unknown;
}

// ─── Registration ────────────────────────────────────────────────────────────

/**
 * Register the DopaShift service worker.
 *
 * Call this once during app initialization (e.g., in main.tsx or App.tsx).
 * The service worker handles:
 * - Background sync for queued change_log events
 * - Push notification delivery for reminders
 * - Notification click routing
 *
 * @param onSyncRequested - Callback invoked when the SW requests a sync cycle
 *   (triggered when browser regains connectivity in the background).
 * @param onNotificationClick - Optional callback invoked when a notification is clicked.
 */
export async function registerServiceWorker(options?: {
  onSyncRequested?: () => void;
  onNotificationClick?: (url: string, data?: unknown) => void;
}): Promise<ServiceWorkerRegistrationResult> {
  if (!('serviceWorker' in navigator)) {
    return { success: false, error: 'Service Workers not supported in this browser.' };
  }

  try {
    const registration = await navigator.serviceWorker.register('/service-worker.js', {
      scope: '/',
    });

    // Listen for messages from the service worker
    navigator.serviceWorker.addEventListener('message', (event: MessageEvent<SWMessage>) => {
      const { type, url, data } = event.data;

      switch (type) {
        case 'DOPASHIFT_SYNC_REQUESTED':
          options?.onSyncRequested?.();
          break;
        case 'DOPASHIFT_NOTIFICATION_CLICK':
          options?.onNotificationClick?.(url ?? '/', data);
          break;
      }
    });

    return { success: true, registration };
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Service Worker registration failed.';
    return { success: false, error: message };
  }
}

// ─── Push Subscription ───────────────────────────────────────────────────────

/**
 * Subscribe to push notifications via the Push API.
 *
 * Requires the user to grant notification permissions and a VAPID public key
 * from the backend. Returns the PushSubscription that should be sent to the
 * server for sending future push messages.
 *
 * @param vapidPublicKey - The server's VAPID public key (base64 URL-safe encoded).
 */
export async function subscribeToPush(
  vapidPublicKey: string,
): Promise<PushSubscription | null> {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    return null;
  }

  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    return null;
  }

  const registration = await navigator.serviceWorker.ready;

  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
  });

  return subscription;
}

// ─── Utilities ───────────────────────────────────────────────────────────────

/**
 * Convert a base64 URL-safe encoded string to a Uint8Array.
 * Used for the VAPID applicationServerKey.
 */
function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; i++) {
    outputArray[i] = rawData.charCodeAt(i);
  }

  return outputArray;
}
