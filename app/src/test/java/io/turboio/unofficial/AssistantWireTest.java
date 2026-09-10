package io.turboio.unofficial;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class AssistantWireTest {
    @Test
    public void asrTextMatchesAssistantMsgWireFormat() {
        byte[] body = "{\"final\":true,\"text\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        byte[] expected = new byte[6 + body.length];
        expected[0] = 8;
        expected[1] = 1;
        expected[2] = 16;
        expected[3] = 5;
        expected[4] = 26;
        expected[5] = (byte) body.length;
        System.arraycopy(body, 0, expected, 6, body.length);

        assertArrayEquals(expected, AssistantWire.asrText("test"));
    }

    @Test
    public void longUtf8PayloadUsesMultiByteVarint() {
        String text = "中".repeat(80);
        byte[] wire = AssistantWire.asrText(text);
        assertTrue((wire[5] & 0x80) != 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedText() {
        AssistantWire.asrText("中".repeat(400));
    }
}
