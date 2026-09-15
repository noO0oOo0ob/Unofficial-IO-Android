package io.turboio.enhanced;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** CatPaw Managed Agents REST/SSE adapter. Opens the raw data-only SSE stream before user.message. */
final class ManagedAgentClient implements AgentSessionClient {
    static final class Config {
        final String baseUrl,apiKey,environmentId,agentId,title;final Integer agentVersion;
        Config(String baseUrl,String apiKey,String environmentId,String agentId,String title,Integer agentVersion){
            this.baseUrl=normalizeBaseUrl(baseUrl);this.apiKey=required(apiKey,"请输入 Managed Agent API Key");this.environmentId=required(environmentId,"请输入 Environment ID");this.agentId=required(agentId,"请输入 Agent ID");this.title=title==null?"RayNeo 眼镜会话":title.trim();this.agentVersion=agentVersion;
        }
    }

    private static final int CONNECT_TIMEOUT_MS=10000,READ_TIMEOUT_MS=30000;
    private final Config config;private final Map<String,EventStream> streams=new ConcurrentHashMap<>();private final Map<String,String> lastAnswers=new ConcurrentHashMap<>();private volatile boolean closed;
    ManagedAgentClient(Config config){this.config=config;}
    public AgentPlatform platform(){return AgentPlatform.MANAGED_AGENT;}
    public void connect(){if(closed)throw new IllegalStateException("Managed Agent 客户端已关闭");}
    public String state(){return closed?"已关闭":"已就绪";}

    public List<Session> listSessions(int limit)throws Exception{
        String query="?limit="+Math.max(1,Math.min(100,limit))+"&agent_id="+URLEncoder.encode(config.agentId,"UTF-8");
        Object root=request("GET","/v1/sessions"+query,null,"application/json");JSONArray rows=array(root,"data","sessions","items");List<Session> out=new ArrayList<>();
        for(int i=0;i<rows.length();i++){JSONObject value=rows.optJSONObject(i);if(value!=null)out.add(session(value));}
        return out;
    }

    public List<CreationTarget> listCreationTargets(int limit){return Collections.singletonList(new CreationTarget(config.environmentId,"Managed Agent",config.agentId,config.environmentId,config.agentId,""));}

    public Session createSession(CreationTarget ignored)throws Exception{
        Object agent=config.agentVersion==null?config.agentId:new JSONObject().put("type","agent").put("id",config.agentId).put("version",config.agentVersion.intValue());
        JSONObject body=new JSONObject().put("agent",agent).put("environment_id",config.environmentId);if(!config.title.isEmpty())body.put("title",config.title);
        Object root=request("POST","/v1/sessions",body,"application/json");JSONObject value=object(root,"session","data");return session(value);
    }

    public String lastAssistant(String sessionId)throws Exception{
        String cached=lastAnswers.get(sessionId);if(cached!=null&&!cached.isEmpty())return cached;
        Object root=request("GET","/v1/sessions/"+path(sessionId)+"/events?limit=100",null,"application/json");JSONArray events=array(root,"data","events","items");String found="";
        for(int i=0;i<events.length();i++){JSONObject event=events.optJSONObject(i);String text=event==null?"":assistantText(event);if(!text.isEmpty())found=text;}
        if(found.isEmpty())return"没有找到助手回复";lastAnswers.put(sessionId,found);return found;
    }

    public void send(String sessionId,String text)throws Exception{
        required(sessionId,"请先选择 Managed Agent Session");required(text,"消息不能为空");EventStream stream=openStream(sessionId);stream.awaitReady();
        JSONObject event=new JSONObject().put("type","user.message").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text",text)));
        stream.arm();try{request("POST","/v1/sessions/"+path(sessionId)+"/events",new JSONObject().put("events",new JSONArray().put(event)),"application/json");}catch(Exception e){streams.remove(sessionId,stream);stream.close();throw e;}
    }

    public Finish waitForFinish(String sessionId,long timeoutMs)throws Exception{
        EventStream stream=streams.get(sessionId);if(stream==null)throw new IllegalStateException("请先发送消息，再等待 Managed Agent 完成");
        Finish result=stream.finished.poll(Math.max(1,timeoutMs),TimeUnit.MILLISECONDS);if(result==null){stream.close();streams.remove(sessionId,stream);throw new SocketTimeoutException("Managed Agent 等待完成超时");}
        streams.remove(sessionId,stream);stream.close();if(result.lastMessage!=null&&!result.lastMessage.isEmpty())lastAnswers.put(sessionId,result.lastMessage);return result;
    }

    public void cancel(String sessionId)throws Exception{
        JSONObject interrupt=new JSONObject().put("type","user.interrupt");request("POST","/v1/sessions/"+path(sessionId)+"/events",new JSONObject().put("events",new JSONArray().put(interrupt)),"application/json");
    }

    private EventStream openStream(String sessionId){EventStream existing=streams.remove(sessionId);if(existing!=null)existing.close();EventStream created=new EventStream(sessionId);streams.put(sessionId,created);created.start();return created;}

    private final class EventStream implements Closeable {
        final String sessionId;final CountDownLatch ready=new CountDownLatch(1);final ArrayBlockingQueue<Finish> finished=new ArrayBlockingQueue<>(1);final StringBuilder answer=new StringBuilder();volatile HttpURLConnection connection;volatile Exception failure;volatile boolean armed;
        EventStream(String sessionId){this.sessionId=sessionId;}
        void start(){Thread thread=new Thread(this::run,"ManagedAgent-SSE");thread.setDaemon(true);thread.start();}
        void awaitReady()throws Exception{if(!ready.await(CONNECT_TIMEOUT_MS+2000L,TimeUnit.MILLISECONDS))throw new SocketTimeoutException("Managed Agent SSE 连接超时");if(failure!=null)throw failure;}
        void arm(){armed=true;}
        void run(){try{URL url=new URL(config.baseUrl+"/v1/sessions/"+path(sessionId)+"/events/stream");HttpURLConnection c=(HttpURLConnection)url.openConnection();connection=c;c.setRequestMethod("GET");c.setConnectTimeout(CONNECT_TIMEOUT_MS);c.setReadTimeout(0);headers(c,"text/event-stream");int code=c.getResponseCode();if(code<200||code>=300)throw httpError(c,code);ready.countDown();try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;StringBuilder data=new StringBuilder();while(!closed&&(line=in.readLine())!=null){if(line.isEmpty()){dispatch(data);data.setLength(0);}else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).trim());}}dispatch(data);}}catch(Exception e){failure=e;ready.countDown();finished.offer(new Finish("error",answer.toString(),message(e)));}finally{HttpURLConnection c=connection;if(c!=null)c.disconnect();}}
        void dispatch(StringBuilder data){if(data.length()==0)return;try{JSONObject event=new JSONObject(data.toString());String type=event.optString("type");String text=assistantText(event);if(!text.isEmpty()){if("agent.message".equals(type)||type.contains("chunk")||type.contains("delta"))answer.append(text);else{answer.setLength(0);answer.append(text);}}if(armed&&("session.status_idle".equals(type)||"session.status_terminated".equals(type))){String status=type.substring("session.status_".length());String error=event.optString("error","");finished.offer(new Finish(status,answer.toString(),error));}}catch(JSONException ignored){}}
        public void close(){HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    }

    private Object request(String method,String route,JSONObject body,String accept)throws Exception{
        if(closed)throw new IllegalStateException("Managed Agent 客户端已关闭");HttpURLConnection c=(HttpURLConnection)new URL(config.baseUrl+route).openConnection();try{c.setRequestMethod(method);c.setConnectTimeout(CONNECT_TIMEOUT_MS);c.setReadTimeout(READ_TIMEOUT_MS);headers(c,accept);if(body!=null){c.setDoOutput(true);byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}int code=c.getResponseCode();if(code<200||code>=300)throw httpError(c,code);try(InputStream in=c.getInputStream()){String text=read(in);return text.trim().isEmpty()?new JSONObject():new JSONTokener(text).nextValue();}}finally{c.disconnect();}}
    private void headers(HttpURLConnection c,String accept){c.setRequestProperty("X-Api-Key",config.apiKey);c.setRequestProperty("Accept",accept);c.setRequestProperty("Content-Type","application/json;charset=UTF-8");}
    private static IOException httpError(HttpURLConnection c,int code){String body="";try{InputStream in=c.getErrorStream();if(in!=null)body=read(in);}catch(Exception ignored){}return new IOException("Managed Agent HTTP "+code+(body.isEmpty()?"":": "+shorten(body,240)));}
    private static String read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];for(int n;(n=in.read(buf))>=0;)out.write(buf,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    private static Session session(JSONObject value){String id=text(value,"id");if(id.isEmpty())throw new IllegalStateException("Managed Agent 响应缺少 Session ID");JSONObject agent=value.optJSONObject("agent");String provider=agent==null?text(value,"agent_id"):text(agent,"id");return new Session(id,text(value,"title"),provider,text(value,"status"),text(value,"environment_id"),text(value,"updated_at"));}
    private static JSONObject object(Object root,String... wrappers){if(root instanceof JSONObject){JSONObject object=(JSONObject)root;for(String key:wrappers){JSONObject nested=object.optJSONObject(key);if(nested!=null)return nested;}return object;}throw new IllegalStateException("Managed Agent 返回格式异常");}
    private static JSONArray array(Object root,String... wrappers){if(root instanceof JSONArray)return(JSONArray)root;if(root instanceof JSONObject){JSONObject object=(JSONObject)root;for(String key:wrappers){JSONArray nested=object.optJSONArray(key);if(nested!=null)return nested;}}return new JSONArray();}
    private static String assistantText(JSONObject event){String type=event.optString("type");if(!(type.startsWith("agent.")||type.startsWith("assistant.")))return"";String direct=event.optString("text","");if(!direct.isEmpty())return direct;Object content=event.opt("content");return contentText(content);}
    private static String contentText(Object value){if(value instanceof JSONArray){StringBuilder out=new StringBuilder();JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)out.append(contentText(a.opt(i)));return out.toString();}if(value instanceof JSONObject){JSONObject o=(JSONObject)value;if("text".equals(o.optString("type")))return o.optString("text","");String delta=o.optString("delta","");if(!delta.isEmpty())return delta;String text=o.optString("text","");if(!text.isEmpty())return text;return contentText(o.opt("content"));}return"";}
    static String normalizeBaseUrl(String value){try{String clean=required(value,"请输入 Managed Agent Base URL");URI uri=new URI(clean);String scheme=uri.getScheme();if(!("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new Exception();String path=uri.getPath();if(path!=null&&!path.isEmpty()&&!"/".equals(path))throw new Exception();return new URI(scheme.toLowerCase(Locale.US),null,uri.getHost(),uri.getPort(),null,null,null).toString();}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Managed Agent Base URL 格式无效");}}
    private static String required(String value,String message){String clean=value==null?"":value.trim();if(clean.isEmpty())throw new IllegalArgumentException(message);return clean;}
    private static String path(String value){try{return URLEncoder.encode(required(value,"Session ID 不能为空"),"UTF-8").replace("+","%20");}catch(UnsupportedEncodingException impossible){throw new AssertionError(impossible);}}
    private static String text(JSONObject value,String key){Object raw=value.opt(key);return raw==null||raw==JSONObject.NULL?"":String.valueOf(raw);}
    private static String message(Exception e){String value=e.getMessage();return value==null||value.isEmpty()?e.getClass().getSimpleName():value;}
    private static String shorten(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"…";}
    public void close(){closed=true;for(EventStream stream:streams.values())stream.close();streams.clear();}
}
