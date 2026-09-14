package io.turboio.enhanced;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Separate listener so the official RayNeo notification pipeline remains untouched. */
public final class AmapNavigationListenerService extends NotificationListenerService {
    private static final String PREFS = "turbo_io_navigation";
    private static final String ENABLED = "amap_enabled";

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply();
        DemoTrace.init(context);
        DemoTrace.record("amap_navigation_enabled=" + enabled);
    }

    public static boolean hasAccess(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationListenerAccessGranted(new ComponentName(context, AmapNavigationListenerService.class));
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        AmapNavigationBridge.onNotification(this, sbn);
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        DemoTrace.init(this);
        DemoTrace.record("amap_notification_listener_connected");
    }

    @Override public void onListenerDisconnected() {
        DemoTrace.record("amap_notification_listener_disconnected");
        super.onListenerDisconnected();
    }
}
