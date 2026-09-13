package io.turboio.enhanced;
/** Exercises the real reflection adapter against the vendor ABI, not a fake adapter. */
public final class AdapterTest {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void blocked(String expected,boolean wake)throws Exception {
  try{VendorTransport v=new VendorTransport();if(wake)v.prepareOrdinaryWake();else v.requireIdle();throw new AssertionError("must block "+expected);}
  catch(IllegalStateException e){if(!e.getMessage().contains(expected))throw e;checks++;}
 }
 public static void main(String[] args)throws Exception {
  N7.b state=((N7.b$b)com.rayneo.rayneo_venus_sdk_plugin.e.c.get(0)).a;
  new VendorTransport().requireIdle();checks++;
  state.h=()->false;new VendorTransport().requireIdle();checks++;state.h=()->true;
  state.f=()->true;blocked("仍在运行",true);state.f=()->false;
  state.e=()->true;blocked("模拟模式",true);state.e=()->false;
  String[] flags={"a","b","c","d"},labels={"系统中断","翻译","助手占用","全天智记"};
  for(int n=0;n<flags.length;n++){
   G7.E.class.getField(flags[n]).setBoolean(state.d,true);
   VendorTransport v=new VendorTransport();check(v.readiness().contains(labels[n]),"arming reports reason "+flags[n]);
   blocked(labels[n],false);
   if(n>0)blocked(labels[n],true);
   G7.E.class.getField(flags[n]).setBoolean(state.d,false);
  }
  state.d.a=true;new VendorTransport().prepareOrdinaryWake();check(!state.d.a,"fresh wake rearms historical interrupt exactly like official method");
  state.d.a=true;android.media.AudioManager.mode=2;blocked("通信音频",true);check(state.d.a,"active call must not clear interrupt");android.media.AudioManager.mode=0;
  state.d.b=true;blocked("翻译",true);check(state.d.a,"translation must not clear interrupt");state.d.b=false;state.d.a=false;
  state.c.m=new Object();blocked("共享音频",true);state.c.m=null;
  String[] rf={"n","o","p"},rl={"共享翻译","共享录音","待启动"};
  for(int n=0;n<rf.length;n++){N7.d.class.getField(rf[n]).setBoolean(state.c,true);blocked(rl[n],true);N7.d.class.getField(rf[n]).setBoolean(state.c,false);}
  I3.v.h=null;blocked("连接眼镜",false);I3.v.h=new I3.v();
  String snapshot=VendorTransport.diagnostics();check(snapshot.contains("systemInterrupted=")&&snapshot.contains("alwaysOnActive=")&&snapshot.contains("audioMode="),"diagnostic has actual guard flags");
  System.out.println("PASS "+checks+" real adapter regression checks");
 }
}
