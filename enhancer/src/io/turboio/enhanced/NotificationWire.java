package io.turboio.enhanced;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

/** Version-locked ordinary-notification wire format: business 21, message type 2. */
final class NotificationWire {
    static final int BUSINESS_NOTIFICATION = 21;
    private static final AtomicInteger NEXT_UID = new AtomicInteger(
            (int) (System.currentTimeMillis() % (Integer.MAX_VALUE - 1)) + 1);

    static final class Packet {
        final String uid;
        final byte[] bytes;
        Packet(String uid, byte[] bytes) { this.uid = uid; this.bytes = bytes; }
    }

    private NotificationWire() {}

    static byte[] enableMaster() {
        return envelope(18, "{\"notification\":true}");
    }

    static Packet notification(String title, String content) {
        String cleanTitle = requireText(title, "标题", 80);
        String cleanContent = requireText(content, "正文", 500);
        int value = NEXT_UID.updateAndGet(current -> current >= Integer.MAX_VALUE - 1 ? 1 : current + 1);
        String uid = Integer.toString(value);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        String json = "{"
                + "\"notificationUID\":" + quote(uid) + ","
                + "\"appId\":\"com.rayneo.venus.pub\","
                + "\"appName\":\"Turbo IO\","
                + "\"title\":" + quote(cleanTitle) + ","
                + "\"subtitle\":\"\","
                + "\"content\":" + quote(cleanContent) + ","
                + "\"timestamp\":" + quote(format.format(new Date())) + ","
                + "\"category\":0,\"reply\":false,\"type\":1}"
                ;
        return new Packet(uid, envelope(2, json));
    }

    private static byte[] envelope(int type, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        if (body.length > 65536) throw new IllegalArgumentException("通知 payload 超过协议限制");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        varintField(out, 1, 1);
        varintField(out, 2, type);
        bytesField(out, 3, body);
        return out.toByteArray();
    }

    private static String requireText(String value, String name, int maxCodePoints) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(name + "不能为空");
        if (trimmed.codePointCount(0, trimmed.length()) > maxCodePoints) {
            throw new IllegalArgumentException(name + "不能超过 " + maxCodePoints + " 个字符");
        }
        for (int offset = 0; offset < trimmed.length();) {
            int codePoint = trimmed.codePointAt(offset);
            if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t') {
                throw new IllegalArgumentException(name + "包含控制字符");
            }
            offset += Character.charCount(codePoint);
        }
        return trimmed;
    }

    private static void varintField(ByteArrayOutputStream out, int field, int value) {
        varint(out, field << 3); varint(out, value);
    }

    private static void bytesField(ByteArrayOutputStream out, int field, byte[] value) {
        varint(out, (field << 3) | 2); varint(out, value.length); out.write(value, 0, value.length);
    }

    private static void varint(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7f) != 0) { out.write((value & 0x7f) | 0x80); value >>>= 7; }
        out.write(value);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default: if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch);
            }
        }
        return out.append('"').toString();
    }
}
