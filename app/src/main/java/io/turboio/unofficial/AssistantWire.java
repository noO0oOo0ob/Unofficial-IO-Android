package io.turboio.unofficial;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Wire format shared with Turbo-IO's AssistantMsg protobuf. */
public final class AssistantWire {
    public static final int BUSINESS_VOICE_ASSISTANT = 13;
    public static final int TYPE_RECORDER_CONTROL = 2;
    public static final int TYPE_ASR_TEXT = 5;
    private static final int MAX_TEXT_BYTES = 1024;

    private AssistantWire() {}

    public static byte[] asrText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be empty");
        }
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("text exceeds 1024 UTF-8 bytes");
        }
        String json = "{\"final\":true,\"text\":" + jsonQuote(text) + "}";
        return assistantMessage(TYPE_ASR_TEXT, json, null);
    }

    public static byte[] recorderControl(boolean start) {
        String json = "{\"rc\":" + (start ? 1 : 2) + "}";
        return assistantMessage(TYPE_RECORDER_CONTROL, json, new byte[0]);
    }

    static byte[] assistantMessage(int type, String message, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarintField(out, 1, 1); // protocol version
        writeVarintField(out, 2, type);
        writeBytesField(out, 3, message.getBytes(StandardCharsets.UTF_8));
        if (data != null) {
            writeBytesField(out, 4, data);
        }
        return out.toByteArray();
    }

    private static void writeVarintField(ByteArrayOutputStream out, int field, int value) {
        writeVarint(out, (field << 3));
        writeVarint(out, value);
    }

    private static void writeBytesField(ByteArrayOutputStream out, int field, byte[] value) {
        writeVarint(out, (field << 3) | 2);
        writeVarint(out, value.length);
        out.write(value, 0, value.length);
    }

    private static void writeVarint(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7f) != 0) {
            out.write((value & 0x7f) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static String jsonQuote(String value) {
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
                default:
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
            }
        }
        return out.append('"').toString();
    }
}
