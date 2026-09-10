package io.turboio.unofficial;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
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

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private BundledRayNeoRuntime runtime;
    private EditText deviceId;
    private EditText asrText;
    private EditText businessId;
    private EditText rawHex;
    private CheckBox pair;
    private TextView logView;

    private final Runnable eventPoll = new Runnable() {
        @Override public void run() {
            if (runtime != null) worker.execute(runtime::drainEvents);
            main.postDelayed(this, 350);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        runtime = new BundledRayNeoRuntime(this, event -> main.post(() -> log("EVENT " + event)));
        setContentView(buildUi());
        requestBluetoothPermissions();
        log("内置运行时: " + runtime.describe());
        log("先点击“初始化”，再扫描或连接。无需安装官方 App。\n");
        log("兼容扫描会显示附近所有 BLE 设备，请勿连接无法确认归属的设备。\n");
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
        column.setPadding(pad, pad + dp(20), pad, pad);

        TextView title = text("Unofficial IO Android", 24);
        column.addView(title);
        column.addView(text("RayNeo CoreConnect 自包含互操作 PoC", 14));

        column.addView(fullButton("初始化内置运行时", () -> run("初始化", () -> {
            runtime.initialize();
            return "成功";
        })));

        LinearLayout queries = row();
        queries.addView(button("已绑定", () -> runList("已绑定", runtime::bondedDevices)));
        queries.addView(button("已连接", () -> runList("已连接", runtime::connectedDevices)));
        column.addView(queries);

        LinearLayout scan = row();
        scan.addView(button("开始扫描", () -> run("开始扫描", runtime::startDiscovery)));
        scan.addView(button("停止扫描", () -> run("停止扫描", runtime::stopDiscovery)));
        column.addView(scan);

        deviceId = input("设备 ID（从扫描事件复制）", false);
        column.addView(deviceId);
        pair = new CheckBox(this);
        pair.setText("连接时请求配对");
        column.addView(pair);

        LinearLayout connection = row();
        connection.addView(button("BLE 连接", () -> run("BLE 连接", () ->
                runtime.connectBle(value(deviceId, "设备 ID"), pair.isChecked()))));
        connection.addView(button("BLE 断开", () -> run("BLE 断开", () ->
                runtime.disconnectBle(value(deviceId, "设备 ID")))));
        column.addView(connection);

        asrText = input("发送到眼镜的 ASR 文本", false);
        column.addView(asrText);
        column.addView(fullButton("发送 AssistantMsg / ASR（business 13）", () -> run("发送 ASR", () -> {
            byte[] payload = AssistantWire.asrText(value(asrText, "ASR 文本"));
            runtime.send(AssistantWire.BUSINESS_VOICE_ASSISTANT, value(deviceId, "设备 ID"), payload);
            return payload.length + " bytes";
        })));

        businessId = input("原始消息 business id（例如 13）", true);
        businessId.setText(R.string.business_default);
        rawHex = input("原始 payload，十六进制（例如 08011005）", false);
        column.addView(businessId);
        column.addView(rawHex);
        column.addView(fullButton("发送原始消息", () -> run("发送原始消息", () -> {
            int business = Integer.parseInt(value(businessId, "business id"));
            byte[] payload = parseHex(value(rawHex, "payload"));
            runtime.send(business, value(deviceId, "设备 ID"), payload);
            return payload.length + " bytes";
        })));

        logView = text("", 12);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(12), 0, dp(40));
        column.addView(logView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        return scroll;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT}, 100);
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
    }

    private void run(String name, ThrowingSupplier<?> action) {
        log("→ " + name);
        worker.execute(() -> {
            try {
                Object result = action.get();
                main.post(() -> log("✓ " + name + ": " + result));
            } catch (Throwable error) {
                main.post(() -> log("✗ " + name + ": " + BundledRayNeoRuntime.rootMessage(error)));
            }
        });
    }

    private void runList(String name, ThrowingSupplier<List<String>> action) {
        run(name, () -> {
            List<String> values = action.get();
            return values.isEmpty() ? "[]" : "\n  " + String.join("\n  ", values);
        });
    }

    private Button button(String label, Runnable click) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> click.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return button;
    }

    private Button fullButton(String label, Runnable click) {
        Button button = button(label, click);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private EditText input(String hint, boolean numeric) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        if (numeric) input.setInputType(InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private void log(String message) {
        if (logView == null) return;
        logView.append(message + "\n");
    }

    private static String value(EditText input, String name) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " 不能为空");
        return value;
    }

    private static byte[] parseHex(String input) {
        String value = input.replace(" ", "").replace("0x", "");
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("十六进制长度必须为偶数");
        byte[] output = new byte[value.length() / 2];
        for (int i = 0; i < output.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("payload 包含非十六进制字符");
            output[i] = (byte) ((high << 4) | low);
        }
        return output;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
