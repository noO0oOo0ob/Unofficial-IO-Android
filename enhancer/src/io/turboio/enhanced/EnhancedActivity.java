package io.turboio.enhanced;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Extra launcher activity injected into the complete official RayNeo APK. */
public final class EnhancedActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private InProcessRayNeoRuntime runtime;
    private CodexBridgeClient codex;
    private EditText deviceId;
    private EditText glassesText;
    private EditText notificationTitle;
    private EditText notificationContent;
    private CheckBox enableNotificationMaster;
    private CheckBox pair;
    private EditText endpoint;
    private EditText token;
    private EditText codexText;
    private TextView logView;

    private final Runnable eventPoll = new Runnable() {
        @Override public void run() {
            if (runtime != null) worker.execute(runtime::drainEvents);
            main.postDelayed(this, 350);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        runtime = new InProcessRayNeoRuntime(this, event -> main.post(() -> log("EVENT " + event)));
        codex = new CodexBridgeClient(this);
        setContentView(buildUi());
        requestBluetoothPermissions();
        log("这是官方 RayNeo AI 1.0.1 完整 APK 内的新增入口。");
        log("设备运行时: " + runtime.describe());
        if (codex.hasUnknownDelivery()) log("⚠ 上次 Codex 提交结果未知，请先刷新电脑端核对，不会自动重发。");
        main.post(eventPoll);
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(eventPoll);
        worker.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(16);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad + dp(18), pad, dp(40));
        column.setBackgroundColor(Color.rgb(246, 247, 250));

        column.addView(text("Turbo IO · RayNeo AI", 25, Color.rgb(23, 28, 38)));
        column.addView(text("官方 APK 增强入口 · 同包、同进程、非跨 App 控制", 13, Color.DKGRAY));
        column.addView(fullButton("进入完整官方 RayNeo AI", this::openOfficial));

        column.addView(section("眼镜设备控制"));
        column.addView(fullButton("初始化官方 CoreConnect", () -> run("初始化", () -> { runtime.initialize(); return "成功"; })));
        LinearLayout queries = row();
        queries.addView(button("已绑定", () -> runList("已绑定", runtime::bondedDevices)));
        queries.addView(button("已连接", () -> runList("已连接", runtime::connectedDevices)));
        column.addView(queries);
        LinearLayout scan = row();
        scan.addView(button("开始扫描", () -> run("开始扫描", runtime::startDiscovery)));
        scan.addView(button("停止扫描", () -> run("停止扫描", runtime::stopDiscovery)));
        column.addView(scan);
        deviceId = input("设备 ID（从扫描事件复制）", false, false);
        column.addView(deviceId);
        pair = new CheckBox(this); pair.setText("连接时请求配对"); column.addView(pair);
        LinearLayout connection = row();
        connection.addView(button("BLE 连接", () -> run("连接", () -> runtime.connectBle(required(deviceId, "设备 ID"), pair.isChecked()))));
        connection.addView(button("BLE 断开", () -> run("断开", () -> runtime.disconnectBle(required(deviceId, "设备 ID")))));
        column.addView(connection);
        glassesText = input("显示到眼镜的文本", false, false); column.addView(glassesText);
        column.addView(fullButton("发送 AssistantMsg / ASR", () -> run("发送到眼镜", () -> {
            runtime.sendAsr(required(deviceId, "设备 ID"), required(glassesText, "文本")); return "已交给 CoreConnect";
        })));

        column.addView(section("眼镜消息推送"));
        column.addView(text("发送普通通知卡片（business 21/type 2），不会伪装成 ASR。设备 ID 留空时仅自动使用唯一已连接设备。", 13, Color.DKGRAY));
        notificationTitle = input("消息标题（最多 80 字）", false, false);
        notificationTitle.setText("Turbo IO 测试"); column.addView(notificationTitle);
        notificationContent = input("消息正文（最多 500 字）", true, false);
        notificationContent.setText("这是一条从手机推送到眼镜的测试消息。"); column.addView(notificationContent);
        enableNotificationMaster = new CheckBox(this);
        enableNotificationMaster.setText("发送前开启眼镜通知总开关（不覆盖来源过滤设置）");
        enableNotificationMaster.setChecked(true); column.addView(enableNotificationMaster);
        column.addView(fullButton("推送通知到眼镜", () -> {
            final String enteredDevice = deviceId.getText().toString().trim();
            final String title = notificationTitle.getText().toString();
            final String content = notificationContent.getText().toString();
            final boolean enableMaster = enableNotificationMaster.isChecked();
            run("推送眼镜通知", () -> {
                String target = enteredDevice.isEmpty() ? runtime.requireSingleConnectedDeviceId() : enteredDevice;
                main.post(() -> deviceId.setText(target));
                String uid = runtime.sendNotification(target, title, content, enableMaster);
                return "已提交到 CoreConnect，UID=" + uid + "；请观察镜片和后续 EVENT 回执";
            });
        }));

        column.addView(section("Codex Bridge"));
        column.addView(text("协议与 iOS Turbo-IO 一致。真机本地调试先执行 adb reverse，再用 127.0.0.1。", 13, Color.DKGRAY));
        endpoint = input("https://电脑上的-bridge", false, false);
        endpoint.setText(codex.endpoint()); column.addView(endpoint);
        token = input("独立 Bearer token（保存后不回显）", false, true); column.addView(token);
        column.addView(fullButton("加密保存桥接配置", () -> run("保存 Codex 配置", () -> {
            codex.save(required(endpoint, "bridge 地址"), required(token, "token"));
            main.post(() -> token.setText("")); return "已保存到 Android Keystore";
        })));
        LinearLayout codexStatus = row();
        codexStatus.addView(button("接收状态/结果", () -> run("Codex 状态", codex::refresh)));
        codexStatus.addView(button("停止当前任务", () -> run("停止 Codex", codex::stop)));
        column.addView(codexStatus);
        codexText = input("发送给 Codex 的任务要求", true, false); column.addView(codexText);
        column.addView(fullButton("发送 / 继续 Codex 任务", () -> run("发送 Codex 消息", () -> codex.message(required(codexText, "任务要求")))));
        column.addView(fullButton("我已在电脑核对：清除未知发送标记", () -> {
            codex.clearUnknownAfterManualCheck(); log("已清除未知发送标记；此操作不会重发旧请求。");
        }));

        column.addView(section("运行日志"));
        logView = text("", 12, Color.rgb(28, 34, 44));
        logView.setTextIsSelectable(true); column.addView(logView);
        ScrollView scroll = new ScrollView(this); scroll.addView(column); return scroll;
    }

    private void openOfficial() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).setClassName(getPackageName(), "com.rayneo.venus.MainActivity");
            startActivity(intent);
        } catch (Exception error) { log("✗ 无法打开官方入口: " + error.getMessage()); }
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 100);
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
    }

    private void run(String name, ThrowingSupplier<?> action) {
        log("→ " + name);
        worker.execute(() -> {
            try { Object value = action.get(); main.post(() -> log("✓ " + name + ": " + value)); }
            catch (Throwable error) { main.post(() -> log("✗ " + name + ": " + InProcessRayNeoRuntime.rootMessage(error))); }
        });
    }

    private void runList(String name, ThrowingSupplier<List<String>> action) {
        run(name, () -> { List<String> values = action.get(); return values.isEmpty() ? "[]" : "\n  " + String.join("\n  ", values); });
    }

    private TextView section(String title) {
        TextView view = text(title, 19, Color.rgb(28, 75, 155));
        view.setPadding(0, dp(18), 0, dp(4)); return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }

    private Button button(String label, Runnable click) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setOnClickListener(v -> click.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)); return button;
    }

    private Button fullButton(String label, Runnable click) {
        Button button = button(label, click);
        button.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); return row; }

    private EditText input(String hint, boolean multiline, boolean password) {
        EditText input = new EditText(this); input.setHint(hint);
        input.setSingleLine(!multiline); if (multiline) input.setMinLines(3);
        if (password) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
    }

    private void log(String message) { if (logView != null) logView.append(message + "\n"); }

    private static String required(EditText input, String name) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " 不能为空"); return value;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private interface ThrowingSupplier<T> { T get() throws Exception; }
}
