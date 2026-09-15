package io.turboio.enhanced;

import java.util.ArrayList;
import java.util.List;

final class PaseoAgentSessionClient implements AgentSessionClient {
    private final PaseoClient delegate;
    PaseoAgentSessionClient(PaseoConfigStore.Config config){delegate=new PaseoClient(config);}
    public AgentPlatform platform(){return AgentPlatform.PASEO;}
    public void connect()throws Exception{delegate.connect();}
    public String state(){return delegate.state();}
    public List<Session> listSessions(int limit)throws Exception{List<Session> out=new ArrayList<>();for(PaseoClient.Agent a:delegate.listAgents(limit))out.add(new Session(a.id,a.title,a.provider,a.status,a.cwd,a.updatedAt));return out;}
    public List<CreationTarget> listCreationTargets(int limit)throws Exception{List<CreationTarget> out=new ArrayList<>();for(PaseoClient.Workspace w:delegate.listWorkspaces(limit))out.add(new CreationTarget(w.id,w.label(),w.project,"","",w.directory));return out;}
    public Session createSession(CreationTarget target)throws Exception{PaseoClient.Agent a=delegate.createCodex(new PaseoClient.Workspace(target.id,target.label,target.detail,target.location,""));return new Session(a.id,a.title,a.provider,a.status,a.cwd,a.updatedAt);}
    public String lastAssistant(String id)throws Exception{return delegate.lastAssistant(id);}
    public void send(String id,String text)throws Exception{delegate.send(id,text);}
    public Finish waitForFinish(String id,long timeout)throws Exception{PaseoClient.Finish f=delegate.waitForFinish(id,timeout);return new Finish(f.status,f.lastMessage,f.error);}
    public void cancel(String id)throws Exception{delegate.cancel(id);}
    public void close(){delegate.close();}
}
