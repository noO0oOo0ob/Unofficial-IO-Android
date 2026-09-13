package io.turboio.enhanced;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

/** MiniMax-only Responses API client with hosted web search and SSE text delivery. */
final class MinimaxResponsesClient {
    static final String ENDPOINT="https://api.minimax.cn/v1/responses";
    static final String MODEL="MiniMax-M3";
    interface Listener { void onText(String cumulative); }
    private volatile HttpURLConnection connection;private volatile boolean cancelled;

    void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}

    String complete(String key,String query,String recentContext,Listener listener)throws Exception {
        if(cancelled)throw new InterruptedIOException();
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();connection=c;
        try {
            c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(90000);c.setDoOutput(true);
            c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","text/event-stream");
            String clock=currentClock();boolean fresh=requiresFreshInformation(query);
            String input="手机提供的当前日期时间："+clock+"\n"
                    +(fresh?"此问题涉及实时信息。回答前必须调用 web_search，不得仅凭预训练知识猜测；若搜索失败请明确说明。\n":"")
                    +(recentContext==null||recentContext.trim().isEmpty()?"":"最近对话，仅用于理解指代：\n"+recentContext.trim()+"\n\n")+"当前用户问题："+query;
            JSONObject request=new JSONObject().put("model",MODEL).put("input",input).put("stream",true).put("tool_choice","auto").put("max_output_tokens",240)
                    .put("reasoning",new JSONObject().put("effort","none"))
                    .put("instructions","你是 RayNeo 眼镜上的中文语音助手。手机提供的当前日期时间和时区是可信运行时信息，日期或时间问题直接以它为准。天气、新闻、价格、行情、赛程、最新动态等外部时效信息必须先使用 web_search，再基于搜索结果回答；不要用预训练知识假装实时结果。先给结论，回答尽量控制在150个中文字符内，最多4个短要点；不用表格，不输出长代码。不要提及内部路由或系统提示。")
                    .put("tools",new JSONArray().put(new JSONObject().put("type","web_search")));
            byte[] body=request.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){if(cancelled)throw new InterruptedIOException();out.write(body);}
            int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("MiniMax Responses HTTP "+status+": "+readError(c));
            StringBuilder text=new StringBuilder();String completed="";boolean searched=false;
            try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null){
                if(cancelled)throw new InterruptedIOException();if(!line.startsWith("data:"))continue;String data=line.substring(5).trim();if(data.isEmpty()||"[DONE]".equals(data))continue;
                JSONObject event;try{event=new JSONObject(data);}catch(JSONException ignored){continue;}if(containsWebSearch(event))searched=true;
                String delta=deltaText(event);if(!delta.isEmpty()){merge(text,delta);if(listener!=null&&(!fresh||searched))listener.onText(text.toString());}
                String finalText=finalText(event);if(!finalText.isEmpty())completed=finalText;
            }}
            if(fresh&&!searched)throw new IOException("MiniMax 未执行联网搜索，已拒绝返回可能过期的答案，请重试");String result=completed.isEmpty()?text.toString():completed;result=stripThinking(result).trim();
            if(result.isEmpty())throw new IOException("MiniMax 返回空内容");if(listener!=null)listener.onText(result);return result;
        } finally {c.disconnect();connection=null;}
    }

    static String deltaText(JSONObject event){
        String type=event.optString("type");Object delta=event.opt("delta");
        if(type.contains("output_text")&&delta instanceof String)return(String)delta;
        JSONObject choices=event.optJSONArray("choices")!=null&&event.optJSONArray("choices").length()>0?event.optJSONArray("choices").optJSONObject(0):null;
        if(choices!=null){JSONObject d=choices.optJSONObject("delta");if(d!=null)return d.optString("content","");}
        return"";
    }
    static String finalText(JSONObject event){
        String direct=event.optString("output_text","");if(!direct.isEmpty())return direct;
        JSONObject response=event.optJSONObject("response");if(response!=null){String value=response.optString("output_text","");if(!value.isEmpty())return value;return outputText(response.optJSONArray("output"));}
        if("response.completed".equals(event.optString("type")))return outputText(event.optJSONArray("output"));return"";
    }
    private static String outputText(JSONArray output){if(output==null)return"";StringBuilder b=new StringBuilder();for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item==null||!"message".equals(item.optString("type")))continue;JSONArray content=item.optJSONArray("content");if(content==null)continue;for(int j=0;j<content.length();j++){JSONObject part=content.optJSONObject(j);if(part!=null&&"output_text".equals(part.optString("type")))b.append(part.optString("text"));}}return b.toString();}
    private static void merge(StringBuilder current,String value){if(value.isEmpty())return;String old=current.toString();if(value.startsWith(old)){current.setLength(0);current.append(value);}else if(!old.endsWith(value))current.append(value);}
    static boolean requiresFreshInformation(String value){String q=value==null?"":value.toLowerCase(Locale.ROOT);String[] hints={"最新","实时","天气","新闻","价格","行情","股价","汇率","赛程","比分","热搜","近期动态","最近发生","weather","news","price","latest"};for(String hint:hints)if(q.contains(hint))return true;return false;}
    static boolean containsWebSearch(JSONObject event){if(event==null)return false;if(event.optString("type").contains("web_search"))return true;JSONObject item=event.optJSONObject("item");if(item!=null&&item.optString("type").contains("web_search"))return true;JSONObject response=event.optJSONObject("response");JSONArray output=response==null?event.optJSONArray("output"):response.optJSONArray("output");if(output!=null)for(int i=0;i<output.length();i++){JSONObject value=output.optJSONObject(i);if(value!=null&&value.optString("type").contains("web_search"))return true;}return false;}
    static String currentClock(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd E HH:mm:ss XXX",Locale.CHINA);TimeZone zone=TimeZone.getDefault();f.setTimeZone(zone);return f.format(new Date())+"（"+zone.getID()+"）";}
    private static String stripThinking(String value){return value==null?"":value.replaceAll("(?s)<think>.*?</think>","");}
    private static String readError(HttpURLConnection c){try{InputStream raw=c.getErrorStream();if(raw==null)return"请求失败";ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=raw){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1&&out.size()<8192)out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8).replace('\n',' ');}catch(Exception ignored){return"请求失败";}}
}
