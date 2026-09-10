package io.turboio.enhanced;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class AssistantWire {
    static final int BUSINESS_VOICE_ASSISTANT = 13;
    private AssistantWire() {}

    static byte[] asrText(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("文本不能为空");
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 1024) throw new IllegalArgumentException("文本超过 1024 UTF-8 bytes");
        String json = "{\"final\":true,\"text\":" + quote(text) + "}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        varintField(out, 1, 1);
        varintField(out, 2, 5);
        bytesField(out, 3, json.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
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
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default: if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch);
            }
        }
        return out.append('"').toString();
    }
}
