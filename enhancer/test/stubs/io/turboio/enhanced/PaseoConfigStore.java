package io.turboio.enhanced;
import java.util.*;
final class PaseoConfigStore {
 static final class Config {final String url,password,clientId;Config(String u,String p,String c){url=u;password=p;clientId=c;}}
 static final class Source {final String id,name,url;final boolean hasPassword;Source(String i,String n,String u,boolean p){id=i;name=n;url=u;hasPassword=p;}}
 private final List<Source> sources=new ArrayList<>();private final Map<String,String> agents=new HashMap<>(),workspaces=new HashMap<>();private String active;
 PaseoConfigStore(android.content.Context c){sources.add(new Source("default","测试电脑","ws://test/ws",false));active="default";}
 Config read(){return readSource(active);}Config readSource(String id){Source s=find(id);if(s==null)throw new IllegalArgumentException("Paseo 源不存在");return new Config(s.url,"","test-client-"+id);}
 void save(String u,String p){}void clearPassword(){}void clearPassword(String id){}String url(){Source s=activeSource();return s==null?"":s.url;}String selectedAgent(){return agents.getOrDefault(active,"");}void selectAgent(String id){agents.put(active,id);}String selectedWorkspace(){return workspaces.getOrDefault(active,"");}void selectWorkspace(String id){workspaces.put(active,id);}boolean hasPassword(){Source s=activeSource();return s!=null&&s.hasPassword;}
 List<Source> sources(){return new ArrayList<>(sources);}Source activeSource(){return find(active);}String activeSourceId(){return active;}
 String saveSource(String id,String name,String url,String password){if(id==null){id="source-"+(sources.size()+1);sources.add(new Source(id,name,url,!password.isEmpty()));active=id;}else{for(int i=0;i<sources.size();i++)if(sources.get(i).id.equals(id))sources.set(i,new Source(id,name,url,sources.get(i).hasPassword||!password.isEmpty()));}return id;}
 void selectSource(String id){if(find(id)==null)throw new IllegalArgumentException("Paseo 源不存在");active=id;}void deleteSource(String id){sources.removeIf(s->s.id.equals(id));if(id.equals(active))active=sources.isEmpty()?"":sources.get(0).id;}
 private Source find(String id){for(Source s:sources)if(s.id.equals(id))return s;return null;}
}
