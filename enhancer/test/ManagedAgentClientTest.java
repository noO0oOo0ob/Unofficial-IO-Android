package io.turboio.enhanced;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class ManagedAgentClientTest {
    private static int checks;private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);ExecutorService pool=Executors.newCachedThreadPool();server.setExecutor(pool);
        List<String> requests=Collections.synchronizedList(new ArrayList<>());CountDownLatch streamReady=new CountDownLatch(1),messagePosted=new CountDownLatch(1);String[] createBody={""};
        server.createContext("/v1/sessions",exchange->{try{
            check("secret".equals(exchange.getRequestHeaders().getFirst("X-Api-Key")),"API key header");
            if("POST".equals(exchange.getRequestMethod())){createBody[0]=body(exchange);respond(exchange,201,new JSONObject().put("id","sesn-new").put("title","RayNeo").put("status","idle").put("environment_id","env-1").toString());}
            else respond(exchange,200,new JSONObject().put("data",new JSONArray().put(new JSONObject().put("id","sesn-old").put("title","旧会话").put("status","idle"))).toString());
        }catch(Exception e){throw new RuntimeException(e);}});
        server.createContext("/v1/sessions/sesn-new/events/stream",exchange->{try{
            exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);OutputStream out=exchange.getResponseBody();out.flush();streamReady.countDown();messagePosted.await(5,TimeUnit.SECONDS);
            out.write("data: {\"type\":\"agent.message\",\"content\":[{\"type\":\"text\",\"text\":\"完成\"}]}\n\n".getBytes(StandardCharsets.UTF_8));
            out.write("data: {\"type\":\"agent.message\",\"content\":[{\"type\":\"text\",\"text\":\"结果\"}]}\n\n".getBytes(StandardCharsets.UTF_8));
            out.write("data: {\"type\":\"session.thread_status_idle\",\"stop_reason\":{\"type\":\"end_turn\"}}\n\n".getBytes(StandardCharsets.UTF_8));
            out.write("data: {\"type\":\"session.status_idle\",\"stop_reason\":{\"type\":\"end_turn\"}}\n\n".getBytes(StandardCharsets.UTF_8));out.flush();out.close();
        }catch(Exception e){throw new RuntimeException(e);}});
        server.createContext("/v1/sessions/sesn-new/events",exchange->{try{String value=body(exchange);requests.add(value);if(value.contains("user.message")){check(streamReady.getCount()==0,"SSE connects before user.message");messagePosted.countDown();}respond(exchange,200,"{}");}catch(Exception e){throw new RuntimeException(e);}});
        server.start();
        try{
            String base="http://127.0.0.1:"+server.getAddress().getPort()+"/";ManagedAgentClient.Config config=new ManagedAgentClient.Config(base,"secret","env-1","agt-1","RayNeo",null);check(!config.baseUrl.endsWith("/"),"base URL normalized");
            ManagedAgentClient client=new ManagedAgentClient(config);check(client.platform()==AgentPlatform.MANAGED_AGENT,"platform enum");check(client.listSessions(10).size()==1,"list sessions");
            AgentSessionClient.Session created=client.createSession(client.listCreationTargets(1).get(0));check("sesn-new".equals(created.id),"create session");JSONObject create=new JSONObject(createBody[0]);check("agt-1".equals(create.getString("agent"))&&"env-1".equals(create.getString("environment_id")),"create payload");
            client.send(created.id,"检查工程");AgentSessionClient.Finish finish=client.waitForFinish(created.id,5000);check("completed".equals(finish.status)&&"完成结果".equals(finish.lastMessage),"raw data-only SSE parsed until completed");check("完成结果".equals(client.lastAssistant(created.id)),"last answer cache");
            check(ManagedAgentClient.terminalFinish(new JSONObject().put("type","session.status_idle").put("stop_reason",new JSONObject().put("type","requires_action")),"部分结果",true)==null,"requires-action idle is not terminal");
            AgentSessionClient.Finish endTurn=ManagedAgentClient.terminalFinish(new JSONObject().put("type","session.status_idle").put("stop_reason",new JSONObject().put("type","end_turn")),"完成结果",true);check(endTurn!=null&&"completed".equals(endTurn.status)&&"完成结果".equals(endTurn.lastMessage),"end-turn idle completes one interaction");
            AgentSessionClient.Finish completed=ManagedAgentClient.terminalFinish(new JSONObject().put("type","session.status_completed"),"完成结果",true);check(completed!=null&&"completed".equals(completed.status),"completed session terminal event parsed");
            AgentSessionClient.Finish failed=ManagedAgentClient.terminalFinish(new JSONObject().put("type","session.status_failed").put("error","模型失败"),"部分结果",true);check(failed!=null&&"failed".equals(failed.status)&&"模型失败".equals(failed.error),"failed terminal event parsed");
            client.cancel(created.id);check(requests.get(requests.size()-1).contains("user.interrupt"),"interrupt event");client.close();
            boolean invalid=false;try{new ManagedAgentClient.Config("ws://bad","k","e","a","",null);}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"reject non-http base URL");
        }finally{server.stop(0);pool.shutdownNow();}
        System.out.println("PASS "+checks+" Managed Agent REST/SSE checks");
    }
    private static String body(HttpExchange exchange)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];for(int n;(n=exchange.getRequestBody().read(b))>=0;)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    private static void respond(HttpExchange exchange,int code,String body)throws IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(code,bytes.length);try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}}
}
