/**
 * DopaShift Service Worker
 *
 * Handles:
 * - Background sync for queued change_log events (tag: 'dopashift-sync')
 * - Push notification delivery for reminders
 * - Notification click handling to open the app
 *
 * Validates: Requirements 5.6
 */

// ─── Background Sync ─────────────────────────────────────────────────────────

/**
 * Listen for the 'sync' event with tag 'dopashift-sync'.
 * Delegates to the SyncEngine's push logic by posting a message
 * to active clients asking them to trigger sync.
 */
self.addEventListener('sync', (event) => {
  if (event.tag === 'dopashift-sync') {
    event.waitUntil(handleSync());
  }
});

/**
 * Perform background sync by messaging active clients.
 * If no clients are available, attempt a direct push from cached queue.
 */
async function handleSync() {
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });

  if (clients.length > 0) {
    // Delegate sync to the active page (which has access to Dexie + token)
    clients.forEach((client) => {
      client.postMessage({ type: 'DOPASHIFT_SYNC_REQUESTED' });
    });
  }
  // If no active clients, background sync will be retried by the browser
  // when connectivity is restored and a client becomes available.
}

// ─── Push Notifications ──────────────────────────────────────────────────────

/**
 * Listen for 'push' events from the push subscription.
 * Displays a notification for reminders.
 */
self.addEventListener('push', (event) => {
  if (!event.data) return;

  const payload = event.data.json();

  const title = payload.title || 'DopaShift Reminder';
  const options = {
    body: payload.body || '',
    icon: payload.icon || '/icons/icon-192.png',
    badge: payload.badge || '/icons/badge-72.png',
    tag: payload.tag || `dopashift-${Date.now()}`,
    data: {
      url: payload.url || '/',
      entityType: payload.entityType || null,
      entityId: payload.entityId || null,
      reminderId: payload.reminderId || null,
    },
    actions: payload.actions || [],
    requireInteraction: payload.requireInteraction || false,
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

// ─── Notification Click ──────────────────────────────────────────────────────

/**
 * Handle notification clicks — open or focus the app at the relevant URL.
 */
self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  const targetUrl = event.notification.data?.url || '/';

  event.waitUntil(
    self.clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((clientList) => {
        // If a window is already open, focus it and navigate
        for (const client of clientList) {
          if ('focus' in client) {
            client.focus();
            client.postMessage({
              type: 'DOPASHIFT_NOTIFICATION_CLICK',
              url: targetUrl,
              data: event.notification.data,
            });
            return;
          }
        }
        // Otherwise open a new window
        return self.clients.openWindow(targetUrl);
      }),
  );
});

// ─── Install & Activate ──────────────────────────────────────────────────────

self.addEventListener('install', () => {
  // Activate immediately without waiting for existing clients to close
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  // Claim all open clients so the SW is active immediately
  event.waitUntil(self.clients.claim());
});
