package io.turboio.enhanced;
import android.os.Handler;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.ByteArrayOutputStream;

public class DemoTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static DemoController controller()throws Exception {Handler.reset();VendorTransport.reset();MinimaxAsr.reset();MinimaxIntentRouter.reset();MinimaxResponsesClient.reset();PaseoClient.reset();DemoHooks.active=null;android.util.Log.events.clear();Constructor<DemoController> x=DemoController.class.getDeclaredConstructor(android.content.Context.class);x.setAccessible(true);return x.newInstance(new android.content.Context());}
 static void shutdown(DemoController c)throws Exception {MinimaxAsr.release.countDown();Field f=DemoController.class.getDeclaredField("network");f.setAccessible(true);ExecutorService ex=(ExecutorService)f.get(c);ex.shutdown();check(ex.awaitTermination(2,TimeUnit.SECONDS),"network drain");DemoHooks.active=null;}
 static byte[] audio(byte[] pcm){ByteArrayOutputStream o=new ByteArrayOutputStream();o.write(8);o.write(1);o.write(16);o.write(3);o.write(34);int n=pcm.length;while(n>127){o.write((n&127)|128);n>>>=7;}o.write(n);o.write(pcm,0,pcm.length);return o.toByteArray();}
 static byte[] voiced(){byte[] b=new byte[640];for(int i=0;i<b.length;i+=2){b[i]=(byte)0xd0;b[i+1]=7;}return b;}
 static void capture(DemoController c)throws Exception {check(DemoHooks.onAssistantMessage("glasses",DemoWire.control(1,1)),"wake consumed");for(int i=0;i<20;i++){check(DemoHooks.onAssistantMessage("glasses",audio(voiced())),"audio consumed");Handler.advance(0);}c.finishUtterance();check(MinimaxAsr.entered.await(2,TimeUnit.SECONDS),"ASR entered");}
 static void route(DemoController c,String text)throws Exception {Method m=DemoController.class.getDeclaredMethod("handleSpeech",String.class);m.setAccessible(true);m.invoke(c,text);}
 static void await(BooleanSupplier test)throws Exception {long end=System.currentTimeMillis()+2000;while(!test.get()&&System.currentTimeMillis()<end)Thread.sleep(5);check(test.get(),"async result");}
 interface BooleanSupplier{boolean get();}
 public static void main(String[] args)throws Exception {
  DemoWire p=DemoWire.parse(DemoWire.control(2,1));check(p.type==2&&p.json.equals("{\"rc\":1}"),"control protocol");
  check(DemoWire.parse(AssistantWire.asrText("你好")).type==5,"ASR wire");DemoWire answer=DemoWire.parse(AssistantWire.answer("回答","问题","round"));org.json.JSONObject answerJson=new org.json.JSONObject(answer.json);check(answer.type==32&&"回答".equals(answerJson.getJSONObject("answer").getString("text"))&&answerJson.getJSONObject("answer").getBoolean("isFinal")&&"paseo".equals(answerJson.getString("sub")),"final answer wire");check(DemoWire.parse(AssistantWire.responseComplete()).type==12,"complete wire");
  NotificationWire.Packet amap=NotificationWire.notification("com.autonavi.minimap","高德地图",6010,"高德导航","前方100米右转","stable-1");org.json.JSONObject amapJson=new org.json.JSONObject(DemoWire.parse(amap.bytes).json);check("stable-1".equals(amap.uid)&&"com.autonavi.minimap".equals(amapJson.getString("appId"))&&amapJson.getInt("category")==6010,"AMap ordinary notification wire");
  for(byte[] bad:new byte[][]{new byte[0],{8},{0},{8,1,34,(byte)255},{8,2}}){try{DemoWire.parse(bad);throw new AssertionError("accepted malformed");}catch(IllegalArgumentException expected){checks++;}}
  SpeechBuffer b=new SpeechBuffer();for(int i=0;i<20;i++)check(!b.add(voiced()),"speech");for(int i=0;i<49;i++)check(!b.add(new byte[640]),"silence");check(b.add(new byte[640]),"endpoint");check(b.takeWav().length==44+70*640,"wav");

  VoiceCommandRouter r=new VoiceCommandRouter();List<String> titles=Arrays.asList("登录问题排查","旅行计划");
  check(r.route("把会话列表列一下",titles,false).kind==VoiceCommandRouter.Kind.LIST,"fuzzy list");
  check(r.route("列出绘画",titles,false).kind==VoiceCommandRouter.Kind.LIST,"ASR homophone list");
  check(r.route("继续第两个",titles,false).number==2,"Chinese number");
  check(VoiceCommandRouter.parseNumber("十二")==12&&VoiceCommandRouter.parseNumber("二十")==20,"Chinese tens");
  check(r.route("基于登陆问题排查继续",titles,false).kind==VoiceCommandRouter.Kind.SELECT_TITLE,"fuzzy title");
  check(r.route("帮我检查一下构建失败原因",titles,false).kind==VoiceCommandRouter.Kind.QUERY,"ordinary query");
  check(r.route("确认停止",titles,true).kind==VoiceCommandRouter.Kind.CONFIRM_STOP,"stop confirmation");
  check(r.route("退下",titles,false).kind==VoiceCommandRouter.Kind.EXIT&&r.route("退出助手",titles,false).kind==VoiceCommandRouter.Kind.EXIT,"voice exit");
  check(MinimaxIntentRouter.parseContent("```json\n{\"intent\":\"LIST\"}\n```","列出绘画",titles,Collections.emptyList()).kind==VoiceCommandRouter.Kind.LIST,"model route parse");
  check(MinimaxIntentRouter.parseContent("<think>x</think>{\"intent\":\"CHAT\"}","今天天气怎么样",titles,Collections.emptyList()).kind==VoiceCommandRouter.Kind.CHAT,"model chat route");

  DemoController c=controller();check(!DemoHooks.onAssistantMessage("glasses",null),"off passthrough");c.enable();
  check(!DemoHooks.onAssistantMessage("other",DemoWire.control(1,1)),"other device");
  check(DemoHooks.onAssistantMessage("glasses",DemoWire.control(1,1)),"wake");check(VendorTransport.sent.size()==1,"start only");
  c.disable();check(!c.enabled(),"disable ACKed");check(VendorTransport.sent.size()==3,"start stop exit, got "+VendorTransport.sent.size());shutdown(c);

  c=controller();c.enable();capture(c);MinimaxAsr.release.countDown();await(()->VendorTransport.sent.stream().anyMatch(x->x.business==13&&DemoWire.parse(x.bytes).type==12));
  List<Integer> types=new ArrayList<>();for(VendorTransport.Sent s:VendorTransport.sent)if(s.business==13)types.add(DemoWire.parse(s.bytes).type);
  check(types.equals(Arrays.asList(2,4,5,32,32,12)),"continuous audio then transcript, streaming and final answer: "+types);
  Handler.advance(11000);check(VendorTransport.sent.stream().noneMatch(x->x.business==13&&DemoWire.parse(x.bytes).type==7),"completed stream stays open beyond old 10-second timeout");
  String longStream="开头"+String.join("",Collections.nCopies(200,"回答内容"))+"最新结尾";String latest=DemoController.latestWindow(longStream,420);check(latest.startsWith("…")&&latest.endsWith("最新结尾")&&!latest.contains("开头")&&latest.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=420,"stream window follows newest text within wire limit");
  check(android.util.Log.events.stream().noneMatch(x->x.contains("测试语音")||x.contains("test-key-not-a-real-credential")),"no content in logs");
  int sentBeforeNext=VendorTransport.sent.size();check(DemoHooks.onAssistantMessage("glasses",DemoWire.control(11,1)),"next round");check(VendorTransport.sent.size()==sentBeforeNext,"continuous recorder needs no duplicate START");c.disable();shutdown(c);

  c=controller();c.enable();route(c,"列出会话");await(()->VendorTransport.sent.stream().anyMatch(x->x.business==21));long notices=VendorTransport.sent.stream().filter(x->x.business==21).count();route(c,"继续第一个");await(()->VendorTransport.sent.stream().filter(x->x.business==21).count()>notices);Field owns=DemoController.class.getDeclaredField("ownsDevice");owns.setAccessible(true);owns.setBoolean(c,true);long answersBefore=VendorTransport.sent.stream().filter(x->x.business==13&&DemoWire.parse(x.bytes).type==32).count();route(c,"检查构建失败原因");await(()->PaseoClient.sends==1);
  check(PaseoClient.lastText.contains("RayNeo 眼镜回复要求")&&PaseoClient.lastText.contains("检查构建失败原因"),"query prompt wrapper");
  await(()->VendorTransport.sent.stream().filter(x->x.business==21).count()>=3);check(VendorTransport.sent.stream().filter(x->x.business==13&&DemoWire.parse(x.bytes).type==32).count()==answersBefore+1,"Codex result stays out of assistant stream");route(c,"停止当前任务");Thread.sleep(20);check(PaseoClient.cancels==0,"stop requires confirmation");route(c,"确认停止");await(()->PaseoClient.cancels==1);c.disable();shutdown(c);

  c=controller();c.enable();check(PaseoClient.connects==0,"Paseo is lazy before routing");route(c,"今天天气怎么样");await(()->MinimaxResponsesClient.calls==1);Handler.advance(300);check(PaseoClient.sends==0&&"今天天气怎么样".equals(MinimaxResponsesClient.lastQuery),"general chat bypasses Codex");check(MinimaxIntentRouter.calls==1,"all speech uses contextual intent model");check(VendorTransport.sent.stream().anyMatch(x->x.business==13&&DemoWire.parse(x.bytes).type==32&&!new org.json.JSONObject(DemoWire.parse(x.bytes).json).getJSONObject("answer").getBoolean("isFinal")),"MiniMax answer streams to glasses");c.disable();shutdown(c);

  c=controller();c.enable();route(c,"列出会话");await(()->PaseoClient.connects>0);route(c,"继续第一个");Thread.sleep(20);int routesBeforeQuery=MinimaxIntentRouter.calls;route(c,"介绍一下修改方案");await(()->PaseoClient.sends==1);check(MinimaxResponsesClient.calls==0,"first utterance after selecting session is forced to Codex");check(MinimaxIntentRouter.calls==routesBeforeQuery,"selected Codex query bypasses intent HTTP entirely");c.disable();shutdown(c);

  c=controller();c.enable();route(c,"新建会话");await(()->VendorTransport.sent.stream().anyMatch(x->x.business==21));route(c,"选择第一个工作区");await(()->PaseoClient.creates==1);check(PaseoClient.sends==0,"creation does not send a prompt yet");route(c,"检查这个工程");await(()->PaseoClient.sends==1);c.disable();shutdown(c);

  c=controller();c.enable();MinimaxResponsesClient.block=true;capture(c);MinimaxAsr.release.countDown();check(MinimaxResponsesClient.entered.await(2,TimeUnit.SECONDS),"stream entered while duplex audio remains active");for(int i=0;i<16;i++){check(DemoHooks.onAssistantMessage("glasses",audio(voiced())),"barge audio consumed");Handler.advance(0);}await(()->MinimaxResponsesClient.cancelled);check(VendorTransport.sent.stream().anyMatch(x->x.business==13&&DemoWire.parse(x.bytes).type==4&&new org.json.JSONObject(DemoWire.parse(x.bytes).json).optInt("rc")==1),"speech onset sends VAD start and interrupts model");c.disable();shutdown(c);

  c=controller();c.enable();capture(c);c.disable();check(MinimaxAsr.cancelled,"ASR canceled on disable");MinimaxAsr.release.countDown();shutdown(c);check(VendorTransport.sent.stream().noneMatch(x->x.business==13&&DemoWire.parse(x.bytes).type==5),"late ASR ignored");

  c=controller();final DemoController exitController=c;c.enable();check(DemoHooks.onAssistantMessage("glasses",DemoWire.control(1,1)),"wake before exit");route(c,"退下");await(()->VendorTransport.sent.stream().anyMatch(x->x.business==13&&DemoWire.parse(x.bytes).type==7));check(exitController.enabled(),"voice exit keeps bridge armed");c.disable();shutdown(c);
  System.out.println("PASS "+checks+" assertions (controller, command router, wire payloads; fake transport/ASR/Paseo). ");
 }
}
