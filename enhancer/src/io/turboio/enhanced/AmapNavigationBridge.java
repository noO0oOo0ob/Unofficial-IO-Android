package io.turboio.enhanced;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Package-scoped, opt-in AMap notification to RayNeo ordinary-notification bridge. */
final class AmapNavigationBridge {
    static final String AMAP_PACKAGE = "com.autonavi.minimap";
    private static final long MIN_SEND_INTERVAL_MS = 2500;
    private static final long SAME_TEXT_DEDUPE_MS = 30000;
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "TurboIO-amap-navigation");
        thread.setDaemon(true);
        return thread;
    });
    private static Pending pending;
    private static ScheduledFuture<?> scheduled;
    private static String lastSent = "";
    private static long lastSentAt;

    private AmapNavigationBridge() {}

    static void onNotification(Context context, StatusBarNotification sbn) {
        if (context == null || sbn == null || !AMAP_PACKAGE.equals(sbn.getPackageName())) return;
        if (!AmapNavigationListenerService.isEnabled(context)) return;
        Notification notification = sbn.getNotification();
        if (notification == null) return;
        Bundle extras = notification.extras;
        AmapNavigationText.Candidate candidate = AmapNavigationText.select(
                value(extras, Notification.EXTRA_TITLE),
                value(extras, Notification.EXTRA_TEXT),
                value(extras, Notification.EXTRA_BIG_TEXT),
                lines(extras == null ? null : extras.get(Notification.EXTRA_TEXT_LINES)),
                value(extras, Notification.EXTRA_SUB_TEXT),
                value(extras, Notification.EXTRA_SUMMARY_TEXT),
                notification.category,
                (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        if (candidate == null) return;
        submit(context.getApplicationContext(), sbn.getKey(), candidate.content);
    }

    private static synchronized void submit(Context context, String key, String content) {
        long now = SystemClock.elapsedRealtime();
        if ((content.equals(lastSent) && now - lastSentAt < SAME_TEXT_DEDUPE_MS)
                || (pending != null && content.equals(pending.content))) return;
        pending = new Pending(context, key, content);
        if (scheduled != null && !scheduled.isDone()) return;
        long delay = Math.max(0, MIN_SEND_INTERVAL_MS - (now - lastSentAt));
        scheduled = WORKER.schedule(AmapNavigationBridge::flush, delay, TimeUnit.MILLISECONDS);
    }

    private static void flush() {
        Pending next;
        synchronized (AmapNavigationBridge.class) {
            next = pending;
            pending = null;
            scheduled = null;
            if (next == null || (next.content.equals(lastSent)
                    && SystemClock.elapsedRealtime() - lastSentAt < SAME_TEXT_DEDUPE_MS)) return;
        }
        DemoTrace.init(next.context);
        try {
            VendorTransport transport = new VendorTransport();
            String uid = Integer.toUnsignedString((next.key == null ? AMAP_PACKAGE : next.key).hashCode());
            NotificationWire.Packet packet = NotificationWire.notification(
                    AMAP_PACKAGE, "高德地图", 6010, "高德导航", next.content, uid);
            transport.send(NotificationWire.BUSINESS_NOTIFICATION, packet.bytes,
                    ok -> DemoTrace.record("amap_navigation_delivery ok=" + ok));
            synchronized (AmapNavigationBridge.class) {
                lastSent = next.content;
                lastSentAt = SystemClock.elapsedRealtime();
            }
            DemoTrace.record("amap_navigation_submitted");
        } catch (Exception | LinkageError error) {
            DemoTrace.record("amap_navigation_failed reason=" + error.getClass().getSimpleName());
        }
        synchronized (AmapNavigationBridge.class) {
            if (pending != null && (scheduled == null || scheduled.isDone())) {
                long delay = Math.max(0, MIN_SEND_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastSentAt));
                scheduled = WORKER.schedule(AmapNavigationBridge::flush, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    private static String value(Bundle extras, String key) {
        Object value = extras == null ? null : extras.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> lines(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Object[]) {
            for (Object item : (Object[]) value) if (item != null) result.add(String.valueOf(item));
        } else if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) if (item != null) result.add(String.valueOf(item));
        }
        return result;
    }

    private static final class Pending {
        final Context context;
        final String key;
        final String content;
        Pending(Context context, String key, String content) { this.context = context; this.key = key; this.content = content; }
    }
}
