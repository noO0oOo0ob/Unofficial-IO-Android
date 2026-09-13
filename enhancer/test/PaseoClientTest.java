package io.turboio.enhanced;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

public final class PaseoClientTest {
 static int checks;static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  ServerSocket server=new ServerSocket(0);final Throwable[] failure={null};
  Thread peer=new Thread(()->{try(Socket s=server.accept()){InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();String headers=readHeaders(in);check(headers.startsWith("GET /ws HTTP/1.1"),"path");check(headers.contains("Authorization: Bearer secret"),"authorization");check(headers.contains("Sec-WebSocket-Protocol: paseo.bearer.secret"),"subprotocol");String key=header(headers,"Sec-WebSocket-Key");String accept=java.util.Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\nSec-WebSocket-Protocol: paseo.bearer.secret\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
    JSONObject hello=new JSONObject(readClientText(in));check("hello".equals(hello.getString("type"))&&hello.getInt("protocolVersion")==1&&"mobile".equals(hello.getString("clientType")),"hello");
    send(out,new JSONObject().put("type","session").put("message",new JSONObject().put("type","status").put("payload",new JSONObject().put("status","server_info").put("serverId","test-server"))).toString());
    JSONObject envelope=new JSONObject(readClientText(in));JSONObject req=envelope.getJSONObject("message");check("fetch_agents_request".equals(req.getString("type")),"fetch request");String id=req.getString("requestId");
    JSONObject agent=new JSONObject().put("id","agent-1").put("title","Test session").put("provider","codex").put("status","idle").put("cwd","/tmp/project").put("updatedAt","now");
    JSONObject payload=new JSONObject().put("requestId",id).put("entries",new JSONArray().put(new JSONObject().put("agent",agent).put("project",new JSONObject()))).put("pageInfo",new JSONObject().put("hasMore",false));
    send(out,new JSONObject().put("type","session").put("message",new JSONObject().put("type","fetch_agents_response").put("payload",payload)).toString());
    envelope=new JSONObject(readClientText(in));req=envelope.getJSONObject("message");check("fetch_workspaces_request".equals(req.getString("type")),"workspace fetch request");id=req.getString("requestId");
    JSONObject workspace=new JSONObject().put("id","workspace-1").put("projectId","project-1").put("projectDisplayName","Demo project").put("projectRootPath","/tmp/project").put("workspaceDirectory","/tmp/project").put("projectKind","git").put("workspaceKind","local_checkout").put("name","main").put("status","done").put("activityAt","now");
    payload=new JSONObject().put("requestId",id).put("entries",new JSONArray().put(workspace)).put("pageInfo",new JSONObject().put("nextCursor",JSONObject.NULL).put("prevCursor",JSONObject.NULL).put("hasMore",false));
    send(out,new JSONObject().put("type","session").put("message",new JSONObject().put("type","fetch_workspaces_response").put("payload",payload)).toString());
    envelope=new JSONObject(readClientText(in));req=envelope.getJSONObject("message");check("create_agent_request".equals(req.getString("type"))&&"codex".equals(req.getJSONObject("config").getString("provider"))&&"workspace-1".equals(req.getString("workspaceId")),"create agent request");id=req.getString("requestId");
    JSONObject created=new JSONObject(agent.toString()).put("id","agent-2").put("title","New session");
    payload=new JSONObject().put("status","agent_created").put("requestId",id).put("agent",created);
    send(out,new JSONObject().put("type","session").put("message",new JSONObject().put("type","status").put("payload",payload)).toString());
    Thread.sleep(50);
   }catch(Throwable t){failure[0]=t;}},"fake-paseo");peer.start();
  PaseoClient client=new PaseoClient(new PaseoConfigStore.Config("ws://127.0.0.1:"+server.getLocalPort()+"/ws","secret","rayneo-test"));client.connect();List<PaseoClient.Agent> agents=client.listAgents(5);check(agents.size()==1&&"agent-1".equals(agents.get(0).id)&&"Test session".equals(agents.get(0).label()),"response parsing");List<PaseoClient.Workspace> workspaces=client.listWorkspaces(5);check(workspaces.size()==1&&"main".equals(workspaces.get(0).label()),"workspace parsing");PaseoClient.Agent created=client.createCodex(workspaces.get(0));check("agent-2".equals(created.id),"agent creation parsing");client.close();peer.join(2000);server.close();if(failure[0]!=null)throw new AssertionError(failure[0]);System.out.println("PASS "+checks+" Paseo WebSocket/protocol checks");
 }
 static String readHeaders(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int state=0;while(state<4){int x=in.read();if(x<0)throw new EOFException();b.write(x);state=state==0&&x=='\r'?1:state==1&&x=='\n'?2:state==2&&x=='\r'?3:state==3&&x=='\n'?4:0;}return b.toString("ISO-8859-1");}
 static String header(String h,String name){for(String line:h.split("\r\n"))if(line.regionMatches(true,0,name+":",0,name.length()+1))return line.substring(name.length()+1).trim();throw new AssertionError("missing "+name);}
 static String readClientText(InputStream in)throws IOException{int a=in.read(),b=in.read();if((a|b)<0)throw new EOFException();int n=b&127;if(n==126)n=(in.read()<<8)|in.read();else if(n==127)throw new IOException("unexpected large test frame");byte[] mask=read(in,4),body=read(in,n);for(int i=0;i<n;i++)body[i]^=mask[i&3];return new String(body,StandardCharsets.UTF_8);}
 static byte[] read(InputStream in,int n)throws IOException{byte[] b=new byte[n];int p=0;while(p<n){int r=in.read(b,p,n-p);if(r<0)throw new EOFException();p+=r;}return b;}
 static void send(OutputStream out,String text)throws IOException{byte[] b=text.getBytes(StandardCharsets.UTF_8);out.write(0x81);if(b.length<126)out.write(b.length);else{out.write(126);out.write(b.length>>>8);out.write(b.length);}out.write(b);out.flush();}
}
