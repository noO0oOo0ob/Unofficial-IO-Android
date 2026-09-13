package io.turboio.enhanced;
import java.util.*;
final class PaseoClient {
 static final class Agent {final String id,title,provider,status,cwd,updatedAt;Agent(String i,String t,String p,String s,String c,String u){id=i;title=t;provider=p;status=s;cwd=c;updatedAt=u;}String label(){return title.isEmpty()?id:title;}}
 static final class Workspace {final String id,name,project,directory,status;Workspace(String i,String n,String p,String d,String s){id=i;name=n;project=p;directory=d;status=s;}String label(){return name.isEmpty()?project:name;}}
 static final class Finish {final String status,lastMessage,error;Finish(String s,String m,String e){status=s;lastMessage=m;error=e;}}
 static int sends,cancels,connects,creates;static String lastText="";static List<Agent> agents=new ArrayList<>();static List<Workspace> workspaces=new ArrayList<>();
 PaseoClient(PaseoConfigStore.Config c){}void connect(){connects++;}String state(){return "已连接";}void close(){}
 List<Agent> listAgents(int limit){return new ArrayList<>(agents);}String lastAssistant(String id){return "最后回复";}
 List<Workspace> listWorkspaces(int limit){return new ArrayList<>(workspaces);}Agent createCodex(Workspace w){creates++;return new Agent("new-agent","新会话","codex","idle",w.directory,"now");}
 void send(String id,String text){sends++;lastText=text;}Finish waitForFinish(String id,long timeout){return new Finish("idle","完成回答","");}void cancel(String id){cancels++;}
 static void reset(){sends=cancels=connects=creates=0;lastText="";agents.clear();workspaces.clear();agents.add(new Agent("a","会话A","codex","idle","/tmp/a","now"));workspaces.add(new Workspace("w","main","Project","/tmp/project","done"));}
}
