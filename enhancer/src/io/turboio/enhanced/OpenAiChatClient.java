package io.turboio.enhanced;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** One non-streaming OpenAI-compatible chat-completions request. */
final class OpenAiChatClient {
    private volatile HttpURLConnection connection;private volatile boolean cancelled;
    void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    String complete(ChatModelConfigStore.Config config,String query)throws Exception {
        if(cancelled)throw new InterruptedIOException();HttpURLConnection c=(HttpURLConnection)new URL(config.endpoint()).openConnection();connection=c;
        try {
            c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(60000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            if(!config.apiKey.isEmpty())c.setRequestProperty("Authorization","Bearer "+config.apiKey);
            JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content","你是运行在 RayNeo 眼镜上的通用语音助手。先给结论，最多四个短要点，尽量不超过350个中文字符，不用表格，不输出长代码。涉及实时信息但无法确认时明确说明，不要编造。"));messages.put(new JSONObject().put("role","user").put("content",query));
            JSONObject request=new JSONObject().put("model",config.model).put("stream",false).put("messages",messages);
            byte[] body=request.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){if(cancelled)throw new InterruptedIOException();out.write(body);}
            int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("聊天模型 HTTP "+status);
            JSONObject root=new JSONObject(new String(read(c.getInputStream()),StandardCharsets.UTF_8));String content=parseContent(root).replaceAll("(?s)<think>.*?</think>","").trim();
            if(content.trim().isEmpty())throw new IOException("聊天模型返回空内容");return content.trim();
        } finally {c.disconnect();connection=null;}
    }
    static String parseContent(JSONObject root)throws JSONException {
        Object value=root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").get("content");
        if(value instanceof String)return(String)value;
        if(value instanceof JSONArray){StringBuilder out=new StringBuilder();JSONArray items=(JSONArray)value;for(int i=0;i<items.length();i++){Object item=items.get(i);if(item instanceof JSONObject){String text=((JSONObject)item).optString("text","");if(!text.isEmpty())out.append(text);}}return out.toString();}
        return String.valueOf(value);
    }
    private byte[] read(InputStream input)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=input){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(cancelled)throw new InterruptedIOException();if(out.size()+n>1048576)throw new IOException("聊天模型响应过大");out.write(b,0,n);}}return out.toByteArray();}
}
