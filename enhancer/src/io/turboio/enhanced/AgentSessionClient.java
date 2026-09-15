package io.turboio.enhanced;

import java.io.Closeable;
import java.util.List;

interface AgentSessionClient extends Closeable {
    final class Session {
        final String id,title,provider,status,context,updatedAt;
        Session(String id,String title,String provider,String status,String context,String updatedAt){this.id=id;this.title=title;this.provider=provider;this.status=status;this.context=context;this.updatedAt=updatedAt;}
        String label(){return title==null||title.isEmpty()?shortId(id):title;}
        private static String shortId(String value){String clean=value==null?"":value;return clean.substring(0,Math.min(12,clean.length()));}
    }

    final class CreationTarget {
        final String id,label,detail,environmentId,agentId,location;
        CreationTarget(String id,String label,String detail,String environmentId,String agentId,String location){this.id=id;this.label=label;this.detail=detail;this.environmentId=environmentId;this.agentId=agentId;this.location=location;}
    }

    final class Finish {
        final String status,lastMessage,error;
        Finish(String status,String lastMessage,String error){this.status=status;this.lastMessage=lastMessage;this.error=error;}
    }

    AgentPlatform platform();
    void connect()throws Exception;
    String state();
    List<Session> listSessions(int limit)throws Exception;
    List<CreationTarget> listCreationTargets(int limit)throws Exception;
    Session createSession(CreationTarget target)throws Exception;
    String lastAssistant(String sessionId)throws Exception;
    void send(String sessionId,String text)throws Exception;
    Finish waitForFinish(String sessionId,long timeoutMs)throws Exception;
    void cancel(String sessionId)throws Exception;
}
