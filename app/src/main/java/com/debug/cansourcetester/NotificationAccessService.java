package com.debug.cansourcetester;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so MediaSessionManager.getActiveSessions(ComponentName) is
 * usable -- Android requires a bound, granted NotificationListenerService
 * component to call that API, even though we don't care about notification
 * content itself, only session access. User must grant "Notification
 * access" for this app once in Settings > Apps > Special access.
 */
public class NotificationAccessService extends NotificationListenerService {
}
