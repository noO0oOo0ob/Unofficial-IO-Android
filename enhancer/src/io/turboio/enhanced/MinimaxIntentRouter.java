package io.turboio.enhanced;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** MiniMax M3 Responses-based semantic router for ASR-damaged control commands. */
final class MinimaxIntentRouter {
    static final String ENDPOINT="https://api.minimax.cn/v1/responses";
    static final String MODEL="MiniMax-M3";
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;
    private volatile String diagnostic="not_started";

    void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    String diagnostic(){return diagnostic;}

    VoiceCommandRouter.Match classify(String key,String transcript,List<String> titles,List<String> workspaces,String context,boolean confirming)throws Exception {
        if(cancelled)throw new InterruptedIOException();
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();connection=c;
        try {
            c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setDoOutput(true);
            c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            JSONObject request=new JSONObject().put("model",MODEL).put("stream",false).put("max_output_tokens",128).put("temperature",0)
                    .put("reasoning",new JSONObject().put("effort","none"))
                    .put("instructions",systemPrompt()).put("input",userPrompt(transcript,titles,workspaces,context,confirming));
            byte[] body=request.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){if(cancelled)throw new InterruptedIOException();out.write(body);}
            int status=c.getResponseCode();if(status!=200){diagnostic="http="+status;throw new IOException("命令理解 HTTP "+status);}
            byte[] response=read(c.getInputStream());
            JSONObject root=new JSONObject(new String(response,StandardCharsets.UTF_8));String content=responseText(root);JSONObject usage=root.optJSONObject("usage");JSONArray output=root.optJSONArray("output");
            diagnostic="http=200 status="+root.optString("status","missing")+" contentChars="+content.length()+" outputItems="+(output==null?0:output.length())+" outputTokens="+(usage==null?-1:usage.optInt("output_tokens",-1))+" responseBytes="+response.length;
            if(!"completed".equals(root.optString("status"))||content.isEmpty())throw new IOException("命令理解响应未完成");
            return parseContent(content,transcript,titles,workspaces);
        } finally {c.disconnect();connection=null;}
    }

    static String responseText(JSONObject root){String direct=root.optString("output_text","");if(!direct.isEmpty())return direct;JSONArray output=root.optJSONArray("output");if(output==null)return"";StringBuilder text=new StringBuilder();for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item==null||!"message".equals(item.optString("type")))continue;JSONArray content=item.optJSONArray("content");if(content==null)continue;for(int j=0;j<content.length();j++){JSONObject part=content.optJSONObject(j);if(part!=null&&"output_text".equals(part.optString("type")))text.append(part.optString("text"));}}return text.toString();}

    static VoiceCommandRouter.Match parseContent(String content,String original,List<String> titles,List<String> workspaces)throws IOException {
        String clean=content==null?"":content.replaceAll("(?s)<think>.*?</think>","").replace("```json","").replace("```","").trim();
        int first=clean.indexOf('{'),last=clean.lastIndexOf('}');if(first<0||last<first)throw new IOException("命令理解结果无效");
        try {
            JSONObject json=new JSONObject(clean.substring(first,last+1));String name=json.getString("intent").trim().toUpperCase(Locale.US);
            VoiceCommandRouter.Kind kind=VoiceCommandRouter.Kind.valueOf(name);
            if(kind==VoiceCommandRouter.Kind.QUERY)kind=VoiceCommandRouter.Kind.CHAT;
            if(kind==VoiceCommandRouter.Kind.SELECT_TITLE)kind=VoiceCommandRouter.Kind.SELECT_NUMBER;
            int number=json.optInt("number",0);
            if((kind==VoiceCommandRouter.Kind.SELECT_NUMBER||kind==VoiceCommandRouter.Kind.INSPECT_NUMBER)&&(number<1||number>titles.size()))throw new IOException("命令编号无效");
            if(kind==VoiceCommandRouter.Kind.SELECT_WORKSPACE&&(number<1||number>workspaces.size()))throw new IOException("工作区编号无效");
            return new VoiceCommandRouter.Match(kind,number,"",.90);
        } catch(IllegalArgumentException|JSONException e){throw new IOException("命令理解结果无效",e);}
    }

    private static String systemPrompt(){return "你是 RayNeo 眼镜的唯一意图路由器。ASR 可能有同音字错误，例如把会话识别成绘画。结合状态和最近交互理解省略、指代和连续命令。只输出一个 JSON 对象，不要解释。intent 只能是 LIST、LIST_NEXT、LIST_PREVIOUS、SELECT_NUMBER、INSPECT_NUMBER、LIST_WORKSPACES、SELECT_WORKSPACE、CREATE_SESSION、CURRENT、LAST_REPLY、ANSWER_NEXT、ANSWER_PREVIOUS、STOP、CONFIRM_STOP、CANCEL、EXIT、CHAT、CODEX_QUERY。选择会话、查看会话或选择工作区时同时输出 number。CHAT 用于天气、百科、写作、翻译、闲聊和不需要操作工程的问答；CODEX_QUERY 用于编程、修改或检查代码、运行测试、操作工程文件，或状态明确说明下一句话应发给已选择 Codex 会话。CREATE_SESSION 表示新建 Codex 会话；若尚未选择工作区，客户端会引导选择。不能改写用户原话。例：列出绘画=>{\"intent\":\"LIST\"}；列出项目或工作区=>{\"intent\":\"LIST_WORKSPACES\"}；选择第二个工作区=>{\"intent\":\"SELECT_WORKSPACE\",\"number\":2}；新建代码会话=>{\"intent\":\"CREATE_SESSION\"}；退下=>{\"intent\":\"EXIT\"}；今天天气怎么样=>{\"intent\":\"CHAT\"}。";}
    private static String userPrompt(String transcript,List<String> titles,List<String> workspaces,String context,boolean confirming){StringBuilder b=new StringBuilder("当前是否等待停止确认：").append(confirming).append("\n当前状态与最近交互：\n").append(context==null?"":context).append("\n可选会话：");for(int i=0;i<titles.size();i++)b.append('\n').append(i+1).append('.').append(titles.get(i));b.append("\n可选工作区：");for(int i=0;i<workspaces.size();i++)b.append('\n').append(i+1).append('.').append(workspaces.get(i));return b.append("\n当前 ASR 文本：").append(transcript).toString();}
    private byte[] read(InputStream input)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=input){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(cancelled)throw new InterruptedIOException();if(out.size()+n>131072)throw new IOException("命令理解响应过大");out.write(b,0,n);}}return out.toByteArray();}
}
