package io.turboio.enhanced;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit, mode-gated debug sends. A successful callback is not lens-display proof. */
final class DebugMessageSender {
    private static final int MAX_PAYLOAD_BYTES=8192;
    private DebugMessageSender() {}

    static String sendAssistant(DemoController controller,String text)throws Exception {
        return send(controller,AssistantWire.BUSINESS_VOICE_ASSISTANT,AssistantWire.asrText(text));
    }

    static String sendRaw(DemoController controller,String businessText,String hex)throws Exception {
        int business;
        try {business=Integer.parseInt(require(businessText,"Business ID"));}
        catch(NumberFormatException e){throw new IllegalArgumentException("Business ID 必须是 0-255 的整数");}
        if(business<0||business>255)throw new IllegalArgumentException("Business ID 必须是 0-255 的整数");
        return send(controller,business,parseHex(hex));
    }

    static byte[] parseHex(String input) {
        String value=require(input,"Hex payload").replace(" ","").replace("\n","").replace("\t","");
        if(value.startsWith("0x")||value.startsWith("0X"))value=value.substring(2);
        if(value.isEmpty()||(value.length()&1)!=0)throw new IllegalArgumentException("Hex payload 长度必须为非零偶数");
        if(value.length()/2>MAX_PAYLOAD_BYTES)throw new IllegalArgumentException("Payload 不能超过 8192 bytes");
        byte[] output=new byte[value.length()/2];
        for(int i=0;i<output.length;i++){
            int high=Character.digit(value.charAt(i*2),16),low=Character.digit(value.charAt(i*2+1),16);
            if(high<0||low<0)throw new IllegalArgumentException("Hex payload 包含非十六进制字符");
            output[i]=(byte)((high<<4)|low);
        }
        return output;
    }

    private static String send(DemoController controller,int business,byte[] payload)throws Exception {
        if(!controller.canDebugSend())throw new IllegalStateException("请先关闭 TurboIO 接管模式并等待当前任务结束");
        VendorTransport transport=new VendorTransport(true);
        CountDownLatch latch=new CountDownLatch(1);AtomicBoolean ok=new AtomicBoolean();
        DemoTrace.record("debug_send business="+business+" bytes="+payload.length);
        transport.send(business,payload,result->{ok.set(result);latch.countDown();});
        if(!latch.await(6,TimeUnit.SECONDS)){
            DemoTrace.record("debug_send_result business="+business+" timeout=true");
            throw new IllegalStateException("发送回调超时；结果未知，不会自动重发");
        }
        DemoTrace.record("debug_send_result business="+business+" ok="+ok.get());
        if(!ok.get())throw new IllegalStateException("厂商发送调用返回失败");
        return "已提交 "+payload.length+" bytes；请在眼镜端确认是否显示";
    }

    private static String require(String value,String name){
        String clean=value==null?"":value.trim();
        if(clean.isEmpty())throw new IllegalArgumentException(name+" 不能为空");
        return clean;
    }
}
