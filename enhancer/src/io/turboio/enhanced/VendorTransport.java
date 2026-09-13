package io.turboio.enhanced;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only attachment to the live 1.0.2 runtime. Never constructs a plugin or drains its queues. */
final class VendorTransport {
    interface Completion { void done(boolean ok); }
    private final ClassLoader loader=VendorTransport.class.getClassLoader();
    private final Object core, listener;
    final String device;
    VendorTransport()throws Exception {this(false);}
    private VendorTransport(boolean checkIdle)throws Exception {
        core=field(type("I3.v"),null,"h");
        if(core==null)throw new IllegalStateException("请先打开官方 App 并连接眼镜");
        Map<?,?> connected=devices();
        if(connected.size()!=1)throw new IllegalStateException("需要且只能连接一副眼镜");
        device=(String)connected.keySet().iterator().next();
        Object found=null;
        for(Object o:(Iterable<?>)field(type("com.rayneo.rayneo_venus_sdk_plugin.e"),null,"c")) {
            if(o.getClass().getName().equals("N7.b$b")) {if(found!=null)throw new IllegalStateException("多个官方语音实例");found=field(o,"a");}
        }
        if(found==null)throw new IllegalStateException("官方语音运行时尚未就绪，请先打开官方首页");
        listener=found;if(checkIdle)requireIdle();
    }
    Class<?> type(String n)throws ClassNotFoundException{return Class.forName(n,true,loader);}
    private Map<?,?> devices()throws Exception{return (Map<?,?>)type("I3.v").getMethod("h").invoke(null);}
    boolean connected(){try{return devices().containsKey(device);}catch(Exception e){return false;}}
    /** Arming the mode does not acquire audio. Only an actual wake requires exclusivity. */
    void requireIdle()throws Exception {
        String reason=busyReason(true);
        if(!reason.isEmpty())throw new IllegalStateException(reason);
    }
    String readiness() {
        try {String reason=busyReason(true);return reason.isEmpty()?"就绪，请用眼镜普通语音唤醒":"等待："+reason;}
        catch(Exception e){return "无法读取音频状态："+e.getClass().getSimpleName();}
    }
    void prepareOrdinaryWake()throws Exception {
        // N7.b$b.j invokes E.d(false) at the beginning of a new vendor wake.
        // Check current calls and active businesses FIRST; never clear their guard to force capture.
        String reason=busyReason(false);
        if(!reason.isEmpty())throw new IllegalStateException(reason);
        Object arbiter=field(listener,"d");
        arbiter.getClass().getMethod("d",boolean.class).invoke(arbiter,false);
        requireIdle();
    }
    private int audioMode()throws Exception {
        android.content.Context context=(android.content.Context)field(type("I3.v"),null,"i");
        if(context==null)throw new IllegalStateException("官方音频上下文未就绪");
        android.media.AudioManager audio=(android.media.AudioManager)context.getSystemService(android.content.Context.AUDIO_SERVICE);
        if(audio==null)throw new IllegalStateException("无法读取手机通信音频状态");
        return audio.getMode();
    }
    private String busyReason(boolean includeInterruption)throws Exception {
        if(!connected())return "眼镜连接已断开";
        java.util.List<String> reasons=new java.util.ArrayList<>();
        int mode=audioMode();
        if(mode!=android.media.AudioManager.MODE_NORMAL)reasons.add("手机通话或通信音频占用（mode="+mode+"）");
        Object arbiter=field(listener,"d");
        if(includeInterruption&&Boolean.TRUE.equals(field(arbiter,"a")))reasons.add("系统中断标志尚未复位（新唤醒时检查）");
        if(Boolean.TRUE.equals(field(arbiter,"b")))reasons.add("官方翻译运行中");
        if(Boolean.TRUE.equals(field(arbiter,"c")))reasons.add("官方助手占用音频");
        if(Boolean.TRUE.equals(field(arbiter,"d")))reasons.add("全天智记运行中");
        Object router=field(listener,"c");
        if(field(router,"m")!=null)reasons.add("共享音频会话运行中");
        if(Boolean.TRUE.equals(field(router,"n")))reasons.add("共享翻译运行中");
        if(Boolean.TRUE.equals(field(router,"o")))reasons.add("共享录音运行中");
        if(Boolean.TRUE.equals(field(router,"p")))reasons.add("录音业务待启动");
        if(boolSupplier("f"))reasons.add("官方语音助手仍在运行");
        if(boolSupplier("e"))reasons.add("官方语音处于模拟模式");
        // N7.b.h / !M7.a.g() is an engine input policy, not an Opus transport capability.
        return String.join("；",reasons);
    }
    private boolean boolSupplier(String name)throws Exception {
        return Boolean.TRUE.equals(supplier(name));
    }
    private Object supplier(String name)throws Exception {
        return type("oa.a").getMethod("invoke").invoke(field(listener,name));
    }
    static String diagnostics() {
        try {
            VendorTransport v=new VendorTransport(false);
            Object arbiter=field(v.listener,"d"),router=field(v.listener,"c");
            String snapshot="model="+v.supplier("g")+" running="+v.boolSupplier("f")+" mock="+v.boolSupplier("e")+" localRecorder="+v.boolSupplier("h")
                    +" systemInterrupted="+field(arbiter,"a")+" translateActive="+field(arbiter,"b")+" assistantActive="+field(arbiter,"c")+" alwaysOnActive="+field(arbiter,"d")
                    +" sharedSession="+(field(router,"m")!=null)+" sharedTranslate="+field(router,"n")+" sharedRecording="+field(router,"o")+" pendingRecording="+field(router,"p")+" audioMode="+v.audioMode();
            android.util.Log.i("TurboIODiagnostics",snapshot);
            return snapshot;
        }catch(Exception e){String result="runtime="+e.getClass().getSimpleName();android.util.Log.i("TurboIODiagnostics",result);return result;}
    }
    void send(int business,byte[] bytes,Completion callback)throws Exception {
        if(!connected())throw new IllegalStateException("device disconnected");
        Class<?> b=type("S3.h"),p=type("I3.P"),f=type("kotlin.jvm.functions.Function2");
        Object kind=type("S3.h$a").getMethod("a",byte.class).invoke(null,(byte)business);
        if(kind==null)throw new IllegalStateException("business");
        Object packet=p.getConstructor(byte[].class,String.class,b,String.class,String.class).newInstance(bytes,device,kind,UUID.randomUUID().toString(),"com.rayneo.rayneonet");
        AtomicBoolean once=new AtomicBoolean();
        Object unit=field(type("ca.x"),null,"a");
        Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{f},(self,m,args)->{
            if(m.getName().equals("invoke")) {if(once.compareAndSet(false,true))callback.done(args!=null&&args.length==2&&args[1]==null);return unit;}
            if(m.getName().equals("hashCode"))return System.identityHashCode(self);
            if(m.getName().equals("equals"))return self==args[0];
            return "TurboIOCompletion";
        });
        type("I3.v").getMethod("u",p,f).invoke(core,packet,proxy);
    }
    Decoder decoder()throws Exception {return new Decoder();}
    final class Decoder {
        final Object value;
        final Method decode,release;
        Decoder()throws Exception {
            Class.forName("com.rayneo.airuntime.AiRuntimeNative",true,loader);
            Class<?> c=type("com.rayneo.airuntime.utils.OpusDecoder");value=c.getConstructor().newInstance();
            decode=c.getMethod("decode2Bytes",byte[].class);release=c.getMethod("release");
            if(!Boolean.TRUE.equals(c.getMethod("init-uN6NtqM",int.class,int.class).invoke(value,16000,1)))throw new IllegalStateException("Opus decoder failed");
        }
        byte[] decode(byte[] b)throws Exception {if(b.length==0||b.length>4096)throw new IllegalArgumentException("Opus frame");return (byte[])decode.invoke(value,(Object)b);}
        void close(){try{release.invoke(value);}catch(Exception ignored){}}
    }
    private static Object field(Object o,String n)throws Exception{return field(o.getClass(),o,n);}
    private static Object field(Class<?> c,Object o,String n)throws Exception {Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
}
