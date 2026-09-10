package io.turboio.enhanced;

import android.app.Activity;
import android.content.Context;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Version-locked reflection adapter that reuses CoreConnect already present in the official APK. */
final class InProcessRayNeoRuntime {
    interface EventListener { void onEvent(Object event); }

    private final Activity activity;
    private final EventListener listener;
    private final ClassLoader loader;
    private Object plugin;
    private Object core;
    private Object eventPump;
    private boolean initialized;

    InProcessRayNeoRuntime(Activity activity, EventListener listener) {
        this.activity = activity;
        this.listener = listener;
        this.loader = activity.getClassLoader();
    }

    String describe() { return "官方 APK 内置 CoreConnect 2.0.76-SNAPSHOT"; }

    synchronized void initialize() throws Exception {
        if (initialized) return;
        Class<?> pluginClass = load("com.rayneo.rayneo_venus_sdk_plugin.j");
        plugin = tryStaticField(pluginClass, "J");
        boolean alreadyAttached = plugin != null && tryField(plugin, "b") != null;
        if (plugin == null) {
            plugin = pluginClass.getDeclaredConstructor().newInstance();
            setStatic(pluginClass, "J", plugin);
        }
        set(plugin, "c", activity);

        if (!alreadyAttached) {
            set(plugin, "b", activity.getApplicationContext());
        }

        // VenusApplication may create and attach the plugin object before CoreConnect's
        // native singleton is ready. The manager initializer is idempotent and must run
        // even when the shared plugin already has a Context.
        Class<?> managerClass = load("com.rayneo.rayneo_venus_sdk_plugin.h$b");
        Object manager = staticField(managerClass, "a");
        Class<?> loggerClass = load("G7.H1");
        invoke(manager, "c", new Class<?>[]{Context.class, loggerClass},
                activity.getApplicationContext(), null);

        Class<?> coreClass = load("I3.v");
        Object accessor = staticField(coreClass, "g");
        core = invoke(accessor, "a", new Class<?>[0]);

        if (!alreadyAttached) {
            registerDelegate("a", "C");
            registerDelegate("b", "D");
            registerDelegate("c", "F");
            registerDelegate("d", "G");
            registerDelegate("e", "E");
            installDeviceLookup();
            set(plugin, "f", true);
        }
        eventPump = field(plugin, "q");
        initialized = true;
    }

    synchronized List<String> bondedDevices() throws Exception {
        ensureInitialized();
        return stringifyIterable(invokeStatic(load("I3.v"), "g", new Class<?>[0]));
    }

    synchronized List<String> connectedDevices() throws Exception {
        ensureInitialized();
        Object value = invokeStatic(load("I3.v"), "h", new Class<?>[0]);
        List<String> output = new ArrayList<>();
        if (value instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                output.add(entry.getKey() + " = " + entry.getValue());
            }
        }
        return output;
    }

    synchronized boolean startDiscovery() throws Exception {
        ensureInitialized();
        Class<?> type = load("S3.F");
        Object filters = Array.newInstance(type, 0);
        Class<?> managerClass = load("com.rayneo.rayneo_venus_sdk_plugin.h$b");
        Object manager = staticField(managerClass, "a");
        invoke(manager, "b", new Class<?>[]{filters.getClass(), String[].class}, filters, new String[0]);
        return true;
    }

    synchronized boolean stopDiscovery() throws Exception {
        ensureInitialized();
        return Boolean.TRUE.equals(invokeStatic(load("com.rayneo.rayneo_venus_sdk_plugin.h"), "n", new Class<?>[0]));
    }

    synchronized boolean connectBle(String deviceId, boolean pair) throws Exception {
        ensureInitialized();
        return Boolean.TRUE.equals(invokeStatic(load("com.rayneo.rayneo_venus_sdk_plugin.h"), "b",
                new Class<?>[]{String.class, String.class, boolean.class}, deviceId, "ble", pair));
    }

    synchronized boolean disconnectBle(String deviceId) throws Exception {
        ensureInitialized();
        return Boolean.TRUE.equals(invokeStatic(load("com.rayneo.rayneo_venus_sdk_plugin.h"), "d",
                new Class<?>[]{String.class, String.class}, deviceId, "ble"));
    }

    synchronized void sendAsr(String deviceId, String text) throws Exception {
        send(AssistantWire.BUSINESS_VOICE_ASSISTANT, deviceId, AssistantWire.asrText(text));
    }

    synchronized void send(int businessId, String deviceId, byte[] payload) throws Exception {
        ensureInitialized();
        if (deviceId == null || deviceId.trim().isEmpty()) throw new IllegalArgumentException("deviceId 不能为空");
        Class<?> businessClass = load("S3.h");
        Object business = invokeStatic(load("S3.h$a"), "a", new Class<?>[]{byte.class}, (byte) businessId);
        if (business == null) throw new IllegalArgumentException("未知 business id: " + businessId);
        Class<?> messageClass = load("I3.P");
        Constructor<?> constructor = messageClass.getDeclaredConstructor(
                byte[].class, String.class, businessClass, String.class, String.class);
        constructor.setAccessible(true);
        Object message = constructor.newInstance(payload, deviceId, business,
                UUID.randomUUID().toString(), "com.rayneo.rayneonet");
        invoke(core, "t", new Class<?>[]{messageClass}, message);
    }

    synchronized void drainEvents() {
        if (!initialized || eventPump == null) return;
        try {
            Object lock = field(eventPump, "e");
            synchronized (lock) {
                drainQueue(field(eventPump, "h"));
                drainQueue(field(eventPump, "f"));
            }
        } catch (Throwable error) {
            listener.onEvent("event-poll-error: " + rootMessage(error));
        }
    }

    private void drainQueue(Object value) throws Exception {
        if (!(value instanceof ArrayDeque<?>)) return;
        Object item;
        while ((item = ((ArrayDeque<?>) value).pollFirst()) != null) {
            Object payload = field(item, "b");
            listener.onEvent(payload instanceof Map<?, ?> ? new LinkedHashMap<>((Map<?, ?>) payload) : String.valueOf(payload));
        }
    }

    private void installDeviceLookup() throws Exception {
        Class<?> function1 = load("kotlin.jvm.functions.Function1");
        Object lookup = Proxy.newProxyInstance(loader, new Class<?>[]{function1}, (proxy, method, args) -> {
            if ("invoke".equals(method.getName())) return invoke(plugin, "y", new Class<?>[]{String.class}, args[0]);
            if ("toString".equals(method.getName())) return "TurboIODeviceLookup";
            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
            if ("equals".equals(method.getName())) return proxy == args[0];
            return null;
        });
        setStatic(load("com.rayneo.rayneo_venus_sdk_plugin.h"), "h", lookup);
    }

    private void registerDelegate(String coreField, String pluginField) throws Exception {
        invoke(field(core, coreField), "a", new Class<?>[]{Object.class}, field(plugin, pluginField));
    }

    private static List<String> stringifyIterable(Object value) {
        List<String> output = new ArrayList<>();
        if (value instanceof Iterable<?>) for (Object item : (Iterable<?>) value) output.add(String.valueOf(item));
        return output;
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("请先初始化官方设备运行时");
    }

    private Class<?> load(String name) throws ClassNotFoundException { return Class.forName(name, true, loader); }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
    }

    private static Object invokeStatic(Class<?> type, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(null, args);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    private static Object tryField(Object target, String name) {
        try { return field(target, name); } catch (Exception ignored) { return null; }
    }

    private static Object staticField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }

    private static Object tryStaticField(Class<?> type, String name) {
        try { return staticField(type, name); } catch (Exception ignored) { return null; }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static void setStatic(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }

    static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        String detail = cursor.getMessage();
        return cursor.getClass().getSimpleName() + (detail == null ? "" : ": " + detail);
    }
}
