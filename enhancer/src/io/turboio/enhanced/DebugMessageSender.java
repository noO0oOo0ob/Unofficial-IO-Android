package io.turboio.enhanced;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Human-readable, mode-gated debug sends. Protocol payloads are encoded internally. */
final class DebugMessageSender {
    enum MessageType {
        ASR_TEXT("ASR 文本",13,5,"输入模拟识别出的文字","显示在眼镜助手页，适合验证文字回显链路"),
        ASSISTANT_ANSWER("助手回答",13,32,"输入要显示的回答内容","模拟最终回答，适合验证助手正文展示"),
        NOTIFICATION("普通通知",21,2,"输入通知正文","发送 Turbo IO 测试通知，需已开启眼镜通知");
        final String title,hint,description;final int business,wireType;
        MessageType(String t,int b,int w,String h,String d){title=t;business=b;wireType=w;hint=h;description=d;}
        String label(){return title+"  ·  Business "+business+" / Type "+wireType;}
    }

    private DebugMessageSender() {}

    static String send(DemoController controller,MessageType type,String content)throws Exception {
        if(type==null)throw new IllegalArgumentException("请选择消息类型");String clean=require(content,"测试内容");byte[] payload;
        switch(type){
            case ASR_TEXT:payload=AssistantWire.asrText(clean);break;
            case ASSISTANT_ANSWER:payload=AssistantWire.answer(clean,"调试消息",UUID.randomUUID().toString());break;
            case NOTIFICATION:payload=NotificationWire.notification("Turbo IO 测试",clean).bytes;break;
            default:throw new IllegalArgumentException("不支持的消息类型");
        }
        return sendPayload(controller,type,payload);
    }

    static String sendAssistant(DemoController controller,String text)throws Exception {return send(controller,MessageType.ASR_TEXT,text);}

    private static String sendPayload(DemoController controller,MessageType type,byte[] payload)throws Exception {
        if(!controller.canDebugSend())throw new IllegalStateException("请先关闭 TurboIO 接管模式并等待当前任务结束");
        VendorTransport transport=new VendorTransport(true);CountDownLatch latch=new CountDownLatch(1);AtomicBoolean ok=new AtomicBoolean();
        DemoTrace.record("debug_send business="+type.business+" type="+type.wireType+" bytes="+payload.length);
        transport.send(type.business,payload,result->{ok.set(result);latch.countDown();});
        if(!latch.await(6,TimeUnit.SECONDS)){DemoTrace.record("debug_send_result business="+type.business+" type="+type.wireType+" timeout=true");throw new IllegalStateException("发送回调超时；结果未知，不会自动重发");}
        DemoTrace.record("debug_send_result business="+type.business+" type="+type.wireType+" ok="+ok.get());
        if(!ok.get())throw new IllegalStateException("厂商发送调用返回失败");
        return type.title+"已提交 · Business "+type.business+" / Type "+type.wireType+" · "+payload.length+" bytes\n请在眼镜端确认是否显示";
    }

    private static String require(String value,String name){String clean=value==null?"":value.trim();if(clean.isEmpty())throw new IllegalArgumentException(name+"不能为空");return clean;}
}
