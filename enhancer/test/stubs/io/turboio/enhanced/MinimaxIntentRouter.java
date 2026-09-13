package io.turboio.enhanced;
import java.io.IOException;import java.util.*;import org.json.JSONObject;
final class MinimaxIntentRouter {
 static volatile int calls;static void reset(){calls=0;}void cancel(){}String diagnostic(){return"stub";}
 VoiceCommandRouter.Match classify(String key,String text,List<String> titles,List<String> workspaces,String context,boolean confirming){calls++;VoiceCommandRouter.Kind k;
  if(text.contains("列出工作区"))k=VoiceCommandRouter.Kind.LIST_WORKSPACES;else if(text.contains("新建"))k=VoiceCommandRouter.Kind.CREATE_SESSION;else if(text.contains("选择第一个工作区"))return new VoiceCommandRouter.Match(VoiceCommandRouter.Kind.SELECT_WORKSPACE,1,"",.9);else if(text.contains("列出"))k=VoiceCommandRouter.Kind.LIST;else if(text.contains("继续第一个"))return new VoiceCommandRouter.Match(VoiceCommandRouter.Kind.SELECT_NUMBER,1,"",.9);else if(text.contains("构建失败"))k=VoiceCommandRouter.Kind.CODEX_QUERY;else if(text.contains("停止当前"))k=VoiceCommandRouter.Kind.STOP;else if(text.contains("确认停止"))k=VoiceCommandRouter.Kind.CONFIRM_STOP;else if(text.contains("退下"))k=VoiceCommandRouter.Kind.EXIT;else k=VoiceCommandRouter.Kind.CHAT;
  return new VoiceCommandRouter.Match(k,0,"",.9);
 }
 static VoiceCommandRouter.Match parseContent(String content,String original,List<String> titles,List<String> workspaces)throws IOException {try{String clean=content.replaceAll("(?s)<think>.*?</think>","").replace("```json","").replace("```","").trim();JSONObject j=new JSONObject(clean);VoiceCommandRouter.Kind k=VoiceCommandRouter.Kind.valueOf(j.getString("intent"));if(k==VoiceCommandRouter.Kind.QUERY)k=VoiceCommandRouter.Kind.CHAT;return new VoiceCommandRouter.Match(k,j.optInt("number",0),original,.9);}catch(Exception e){throw new IOException(e);}}
}
