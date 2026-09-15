package io.turboio.unofficial;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Version-locked adapter around the CoreConnect bytecode bundled in this APK.
 * No installed RayNeo application is queried or loaded.
 */
final class BundledRayNeoRuntime {
    interface EventListener {
        void onEvent(Object event);
    }

    private static final String RUNTIME_VERSION = "RayNeo AI 1.0.2 (68) / CoreConnect 2.0.76-SNAPSHOT";
    private static final Map<String, String> DEX_HASHES = new LinkedHashMap<>();

    static {
        DEX_HASHES.put("classes.dex", "7777aafca2eb6ad95dc0e4c2858d406d50183142d8c9201739f5d72525e36117");
        DEX_HASHES.put("classes2.dex", "04a7d400568ea8d66df06133562d329790318f968a378eea524f1459e3e4b35c");
        DEX_HASHES.put("classes3.dex", "99bcb84be054579256a9e4c0ec2e3c86b5dcf26887a72580c1dbbbaa8db8e85c");
    }

    private final Activity activity;
    private final EventListener listener;
    private ClassLoader loader;
    private Object plugin;
    private Object core;
    private Object eventPump;
    private boolean initialized;

    BundledRayNeoRuntime(Activity activity, EventListener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    String describe() {
        return RUNTIME_VERSION + ", ABI=" + Build.SUPPORTED_ABIS[0];
    }

    synchronized void initialize() throws Exception {
        if (initialized) return;
        if (!supportsArm64()) {
            throw new IllegalStateException("初版仅包含 arm64-v8a，当前 ABI=" + String.join(",", Build.SUPPORTED_ABIS));
        }

        loader = createVerifiedClassLoader();
        Class<?> pluginClass = load("com.rayneo.rayneo_venus_sdk_plugin.j");
        plugin = pluginClass.getDeclaredConstructor().newInstance();
        set(plugin, "b", activity.getApplicationContext());
        set(plugin, "c", activity);

        Class<?> managerClass = load("com.rayneo.rayneo_venus_sdk_plugin.h$b");
        Object manager = staticField(managerClass, "a");
        Class<?> loggerClass = load("G7.H1");
        invoke(manager, "c", new Class<?>[]{Context.class, loggerClass},
                activity.getApplicationContext(), null);

        Class<?> coreClass = load("I3.v");
        Object accessor = staticField(coreClass, "g");
        core = invoke(accessor, "a", new Class<?>[0]);

        registerDelegate("a", "C"); // discovery
        registerDelegate("b", "D"); // connection
        registerDelegate("c", "F"); // messages
        registerDelegate("d", "G"); // analytics
        registerDelegate("e", "E"); // file transfer

        eventPump = field(plugin, "q");
        installDeviceLookup();
        set(plugin, "f", true);
        initialized = true;
    }

    synchronized List<String> bondedDevices() throws Exception {
        ensureInitialized();
        Object result = invokeStatic(load("I3.v"), "g", new Class<?>[0]);
        return stringifyIterable(result);
    }

    synchronized List<String> connectedDevices() throws Exception {
        ensureInitialized();
        Object result = invokeStatic(load("I3.v"), "h", new Class<?>[0]);
        List<String> output = new ArrayList<>();
        if (result instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) result).entrySet()) {
                output.add(entry.getKey() + " = " + entry.getValue());
            }
        }
        return output;
    }

    @SuppressLint("MissingPermission")
    List<String> systemBondedDevices() {
        requireBluetoothConnectPermission();
        return formatBluetoothDevices(bluetoothAdapter().getBondedDevices());
    }

    @SuppressLint("MissingPermission")
    List<String> systemGattDevices() {
        requireBluetoothConnectPermission();
        BluetoothManager manager = activity.getSystemService(BluetoothManager.class);
        if (manager == null) throw new IllegalStateException("系统 BluetoothManager 不可用");
        return formatBluetoothDevices(manager.getConnectedDevices(BluetoothProfile.GATT));
    }

    @SuppressLint("MissingPermission")
    String preferredSystemDeviceId() {
        requireBluetoothConnectPermission();
        Set<BluetoothDevice> devices = bluetoothAdapter().getBondedDevices();
        for (BluetoothDevice device : devices) {
            String name = device.getName();
            if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains("rayneo")) {
                return device.getAddress();
            }
        }
        return "";
    }

    synchronized boolean startDiscovery() throws Exception {
        ensureInitialized();
        String preferred = preferredSystemDeviceId();
        startDiscovery(preferred.isEmpty() ? new String[0] : new String[]{preferred});
        return true;
    }

    private void startDiscovery(String[] retrieveIds) throws Exception {
        Class<?> type = load("S3.F");
        // An empty list makes Android perform an unfiltered BLE scan. CoreConnect emits
        // RayNeo advertisements as well as unrelated BLE devices, so the UI warns before
        // connecting. This works when the controller reports zero hardware filter slots.
        Object filters = Array.newInstance(type, 0);
        // Call the manager directly: the public helper catches every exception and only
        // returns false, which hides the actual compatibility error from this PoC.
        Class<?> managerClass = load("com.rayneo.rayneo_venus_sdk_plugin.h$b");
        Object manager = staticField(managerClass, "a");
        invoke(manager, "b", new Class<?>[]{filters.getClass(), String[].class},
                filters, retrieveIds);
    }

    synchronized boolean stopDiscovery() throws Exception {
        ensureInitialized();
        Object value = invokeStatic(load("com.rayneo.rayneo_venus_sdk_plugin.h"),
                "n", new Class<?>[0]);
        return Boolean.TRUE.equals(value);
    }

    synchronized boolean connectBle(String deviceId, boolean pair) throws Exception {
        ensureInitialized();
        ensureDeviceKnown(deviceId);
        Object value = invokeStatic(load("com.rayneo.rayneo_venus_sdk_plugin.h"),
                "b", new Class<?>[]{String.class, String.class, boolean.class},
                deviceId, "ble", pair);
        if (!Boolean.TRUE.equals(value)) {
            throw new IllegalStateException("CoreConnect 拒绝连接；请确认眼镜已开机且官方 App 未占用连接");
        }
        return true;
    }

    synchronized boolean disconnectBle(String deviceId) throws Exception {
        ensureInitialized();
        Object value = invokeStatic(load("com.rayneo.rayneo_venus_sdk_plugin.h"),
                "d", new Class<?>[]{String.class, String.class}, deviceId, "ble");
        return Boolean.TRUE.equals(value);
    }

    synchronized void send(int businessId, String deviceId, byte[] payload) throws Exception {
        ensureInitialized();
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (!isCoreConnected(deviceId)) {
            throw new IllegalStateException("本进程 CoreConnect 尚未连接此设备，请先点击 BLE 连接并等待连接事件");
        }
        Class<?> businessClass = load("S3.h");
        Object business = invokeStatic(load("S3.h$a"), "a",
                new Class<?>[]{byte.class}, (byte) businessId);
        if (business == null) throw new IllegalArgumentException("未知 business id: " + businessId);

        Class<?> messageClass = load("I3.P");
        Constructor<?> ctor = messageClass.getDeclaredConstructor(
                byte[].class, String.class, businessClass, String.class, String.class);
        ctor.setAccessible(true);
        Object message = ctor.newInstance(payload, deviceId, business,
                UUID.randomUUID().toString(), "com.rayneo.rayneonet");
        invoke(core, "t", new Class<?>[]{messageClass}, message);
    }

    private void ensureDeviceKnown(String deviceId) throws Exception {
        if (lookupDevice(deviceId) != null) return;
        startDiscovery(new String[]{deviceId});
        try {
            for (int attempt = 0; attempt < 50; attempt++) {
                Thread.sleep(100);
                if (lookupDevice(deviceId) != null) return;
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        }
        try {
            stopDiscovery();
        } catch (Exception ignored) {
            // Preserve the more useful discovery failure below.
        }
        throw new IllegalStateException("定向发现未找到设备 " + deviceId
                + "；请确认眼镜已开机，或暂时退出官方 App 后重试");
    }

    private Object lookupDevice(String deviceId) throws Exception {
        return invoke(plugin, "y", new Class<?>[]{String.class}, deviceId);
    }

    private boolean isCoreConnected(String deviceId) throws Exception {
        Object result = invokeStatic(load("I3.v"), "h", new Class<?>[0]);
        if (!(result instanceof Map<?, ?>)) return false;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) result).entrySet()) {
            if (deviceId.equals(String.valueOf(entry.getKey()))) return true;
            Object value = entry.getValue();
            if (value != null) {
                try {
                    if (deviceId.equals(String.valueOf(field(value, "a")))) return true;
                } catch (Exception ignored) {
                    // Map values vary by CoreConnect transport; the key is normally the device ID.
                }
            }
        }
        return false;
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

    private ClassLoader createVerifiedClassLoader() throws Exception {
        File runtimeDir = new File(activity.getCodeCacheDir(), "rayneo-1.0.2-68");
        if (!runtimeDir.exists() && !runtimeDir.mkdirs()) {
            throw new IllegalStateException("无法创建运行时目录: " + runtimeDir);
        }
        List<String> paths = new ArrayList<>();
        for (Map.Entry<String, String> entry : DEX_HASHES.entrySet()) {
            File dex = new File(runtimeDir, entry.getKey());
            installVerifiedAsset("vendor/" + entry.getKey(), dex, entry.getValue());
            paths.add(dex.getAbsolutePath());
        }
        String dexPath = String.join(File.pathSeparator, paths);
        String nativePath = activity.getApplicationInfo().nativeLibraryDir;
        return new DexClassLoader(dexPath, runtimeDir.getAbsolutePath(), nativePath,
                activity.getClassLoader());
    }

    private void installVerifiedAsset(String asset, File target, String expectedHash) throws Exception {
        if (target.isFile() && expectedHash.equals(sha256(target))) {
            if (target.canWrite() && !target.setReadOnly()) {
                throw new IllegalStateException("无法将 DEX 标记为只读: " + target);
            }
            return;
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("无法替换旧 DEX: " + target);
        }
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (InputStream in = activity.getAssets().open(asset);
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.getFD().sync();
        }
        String actual = sha256(temp);
        if (!expectedHash.equals(actual)) {
            temp.delete();
            throw new SecurityException(asset + " SHA-256 不匹配: " + actual);
        }
        if (!temp.setReadOnly()) {
            temp.delete();
            throw new IllegalStateException("无法将临时 DEX 标记为只读");
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IllegalStateException("无法安装 DEX: " + target);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder value = new StringBuilder();
        for (byte b : digest.digest()) value.append(String.format("%02x", b & 0xff));
        return value.toString();
    }

    private static boolean supportsArm64() {
        for (String abi : Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) return true;
        return false;
    }

    private BluetoothAdapter bluetoothAdapter() {
        BluetoothManager manager = activity.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) throw new IllegalStateException("系统蓝牙不可用");
        if (!adapter.isEnabled()) throw new IllegalStateException("请先开启系统蓝牙");
        return adapter;
    }

    private void requireBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= 31
                && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("缺少附近设备权限，请先允许蓝牙连接权限");
        }
    }

    @SuppressLint("MissingPermission")
    private List<String> formatBluetoothDevices(Iterable<BluetoothDevice> devices) {
        requireBluetoothConnectPermission();
        List<String> output = new ArrayList<>();
        for (BluetoothDevice device : devices) {
            String name = device.getName();
            output.add((name == null || name.isEmpty() ? "未命名设备" : name)
                    + " | " + device.getAddress());
        }
        return output;
    }

    private static List<String> stringifyIterable(Object result) {
        List<String> output = new ArrayList<>();
        if (result instanceof Iterable<?>) {
            for (Object device : (Iterable<?>) result) output.add(String.valueOf(device));
        }
        return output;
    }

    private void drainQueue(Object value) throws Exception {
        if (!(value instanceof ArrayDeque<?>)) return;
        ArrayDeque<?> queue = (ArrayDeque<?>) value;
        Object item;
        while ((item = queue.pollFirst()) != null) {
            Object payload = field(item, "b");
            listener.onEvent(payload instanceof Map<?, ?> ? payload : String.valueOf(payload));
        }
    }

    private void installDeviceLookup() throws Exception {
        Class<?> function1 = load("kotlin.jvm.functions.Function1");
        Object lookup = Proxy.newProxyInstance(loader, new Class<?>[]{function1}, (proxy, method, args) -> {
            if ("invoke".equals(method.getName())) {
                return invoke(plugin, "y", new Class<?>[]{String.class}, args[0]);
            }
            if ("toString".equals(method.getName())) return "UnofficialIODeviceLookup";
            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
            if ("equals".equals(method.getName())) return proxy == args[0];
            return null;
        });
        setStatic(load("com.rayneo.rayneo_venus_sdk_plugin.h"), "h", lookup);
    }

    private void registerDelegate(String coreListField, String pluginDelegateField) throws Exception {
        Object list = field(core, coreListField);
        Object delegate = field(plugin, pluginDelegateField);
        invoke(list, "a", new Class<?>[]{Object.class}, delegate);
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("请先初始化内置运行时");
    }

    private Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type, name);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object invokeStatic(Class<?> type, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Object staticField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setStatic(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        String detail = cursor.getMessage();
        return cursor.getClass().getSimpleName() + (detail == null ? "" : ": " + detail);
    }
}
