package io.turboio.enhanced;
final class AgentPlatformConfigStore {
 private AgentPlatform platform=AgentPlatform.PASEO;private String session="";
 AgentPlatformConfigStore(android.content.Context c){}
 AgentPlatform platform(){return platform;}void selectPlatform(AgentPlatform value){platform=value;}
 String baseUrl(){return "https://managed.test";}String environmentId(){return "env";}String agentId(){return "agent";}String title(){return "RayNeo";}String version(){return "";}String selectedSession(){return session;}void selectSession(String id){session=id;}boolean hasApiKey(){return true;}
 void saveManaged(String baseUrl,String environmentId,String agentId,String title,String version,String apiKey){}void clearApiKey(){}
 ManagedAgentClient.Config readManaged(){return new ManagedAgentClient.Config(baseUrl(),"test-api-key",environmentId(),agentId(),title(),null);}
}
