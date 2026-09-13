package io.turboio.enhanced;
final class PaseoConfigStore {
 static final class Config {final String url,password,clientId;Config(String u,String p,String c){url=u;password=p;clientId=c;}}
 private String selected="",workspace="";PaseoConfigStore(android.content.Context c){}
 Config read(){return new Config("ws://test/ws","","test-client");}void save(String u,String p){}void clearPassword(){}String url(){return "ws://test/ws";}String selectedAgent(){return selected;}void selectAgent(String id){selected=id;}String selectedWorkspace(){return workspace;}void selectWorkspace(String id){workspace=id;}boolean hasPassword(){return false;}
}
