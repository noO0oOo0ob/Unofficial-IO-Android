package io.turboio.enhanced;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only attachment to the live official runtime. Never constructs a plugin or drains its queues. */
final class VendorTransport {
    interface Completion { void done(boolean ok); }
    private final ClassLoader loader=VendorTransport.class.getClassLoader();
    private final Abi abi;
    private final Object core, listener;
    final String device;

    VendorTransport()throws Exception {this(false);}
    VendorTransport(boolean checkIdle)throws Exception {
        abi=Abi.detect(loader);
        core=field(type(abi.core),null,"h");
        if(core==null)throw new IllegalStateException("请先打开官方 App 并连接眼镜");
        Map<?,?> connected=devices();
        if(connected.size()!=1)throw new IllegalStateException("需要且只能连接一副眼镜");
        device=(String)connected.keySet().iterator().next();
        Object found=null;
        for(Object o:(Iterable<?>)field(type("com.rayneo.rayneo_venus_sdk_plugin.e"),null,"c")) {
            if(o.getClass().getName().equals(abi.voiceListener)) {
                if(found!=null)throw new IllegalStateException("多个官方语音实例");
                found=field(o,"a");
            }
        }
        if(found==null)throw new IllegalStateException("官方语音运行时尚未就绪，请先打开官方首页");
        listener=found;
        if(checkIdle)requireIdle();
    }

    Class<?> type(String n)throws ClassNotFoundException{return Class.forName(n,true,loader);}
    private Map<?,?> devices()throws Exception{return (Map<?,?>)type(abi.core).getMethod("h").invoke(null);}
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
        // Both 1.0.2 N7.b$b.j and 1.0.4 K7.b$b.j clear the historical
        // system-interruption bit before a new ordinary wake.
        String reason=busyReason(false);
        if(!reason.isEmpty())throw new IllegalStateException(reason);
        Object arbiter=field(listener,"d");
        arbiter.getClass().getMethod("d",boolean.class).invoke(arbiter,false);
        requireIdle();
    }

    private int audioMode()throws Exception {
        android.content.Context context=(android.content.Context)field(type(abi.core),null,"i");
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
        if(field(router,abi.sharedModeField)!=null)reasons.add("共享音频会话运行中");
        if(!abi.latest) {
            if(Boolean.TRUE.equals(field(router,"n")))reasons.add("共享翻译运行中");
            if(Boolean.TRUE.equals(field(router,"o")))reasons.add("共享录音运行中");
            if(Boolean.TRUE.equals(field(router,"p")))reasons.add("录音业务待启动");
        }
        if(boolSupplier("f"))reasons.add("官方语音助手仍在运行");
        if(boolSupplier("e"))reasons.add("官方语音处于模拟模式");
        return String.join("；",reasons);
    }

    private boolean boolSupplier(String name)throws Exception{return Boolean.TRUE.equals(supplier(name));}
    private Object supplier(String name)throws Exception {
        return type(abi.supplier).getMethod("invoke").invoke(field(listener,name));
    }

    static String diagnostics() {
        try {
            VendorTransport v=new VendorTransport(false);
            Object arbiter=field(v.listener,"d"),router=field(v.listener,"c");
            String snapshot="abi="+v.abi.label+" model="+v.supplier("g")+" running="+v.boolSupplier("f")+" mock="+v.boolSupplier("e")
                    +" systemInterrupted="+field(arbiter,"a")+" translateActive="+field(arbiter,"b")+" assistantActive="+field(arbiter,"c")+" alwaysOnActive="+field(arbiter,"d")
                    +" sharedMode="+field(router,v.abi.sharedModeField)+" audioMode="+v.audioMode();
            android.util.Log.i("TurboIODiagnostics",snapshot);
            return snapshot;
        }catch(Exception e){String result="runtime="+e.getClass().getSimpleName();android.util.Log.i("TurboIODiagnostics",result);return result;}
    }

    void send(int business,byte[] bytes,Completion callback)throws Exception {
        if(!connected())throw new IllegalStateException("device disconnected");
        Class<?> b=type(abi.business),p=type(abi.message),f=type("kotlin.jvm.functions.Function2");
        Object kind=type(abi.business+"$a").getMethod("a",byte.class).invoke(null,(byte)business);
        if(kind==null||((Number)field(kind,"b")).byteValue()!=(byte)business)throw new IllegalStateException("business");
        Object packet=p.getConstructor(byte[].class,String.class,b,String.class,String.class).newInstance(bytes,device,kind,UUID.randomUUID().toString(),"com.rayneo.rayneonet");
        AtomicBoolean once=new AtomicBoolean();
        Object unit=field(type(abi.unit),null,"a");
        Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{f},(self,m,args)->{
            if(m.getName().equals("invoke")) {if(once.compareAndSet(false,true))callback.done(args!=null&&args.length==2&&args[1]==null);return unit;}
            if(m.getName().equals("hashCode"))return System.identityHashCode(self);
            if(m.getName().equals("equals"))return self==args[0];
            return "TurboIOCompletion";
        });
        type(abi.core).getMethod("u",p,f).invoke(core,packet,proxy);
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

    private static final class Abi {
        final boolean latest;
        final String label,core,message,business,unit,voiceListener,supplier,sharedModeField;
        Abi(boolean latest,String label,String core,String message,String business,String unit,String voiceListener,String supplier,String sharedModeField){
            this.latest=latest;this.label=label;this.core=core;this.message=message;this.business=business;this.unit=unit;this.voiceListener=voiceListener;this.supplier=supplier;this.sharedModeField=sharedModeField;
        }
        static Abi detect(ClassLoader loader)throws ClassNotFoundException {
            try {
                Class<?> core=Class.forName("E3.u",false,loader);
                if(core.getDeclaredField("h").getType()==core&&Map.class.isAssignableFrom(core.getMethod("h").getReturnType()))
                    return new Abi(true,"1.0.4/core-2.0.80","E3.u","E3.Q","P3.h","Y9.x","K7.b$b","ka.a","n");
            } catch(ReflectiveOperationException ignored) {}
            Class.forName("I3.v",false,loader);
            return new Abi(false,"1.0.2/core-2.0.76","I3.v","I3.P","S3.h","ca.x","N7.b$b","oa.a","m");
        }
    }

    private static Object field(Object o,String n)throws Exception{return field(o.getClass(),o,n);}
    private static Object field(Class<?> c,Object o,String n)throws Exception {Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
}
