package io.turboio.enhanced;

public final class AgentPlatformAssemblerTest {
    private static int checks;private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        check(AgentPlatform.parse(null)==AgentPlatform.PASEO,"legacy default remains Paseo");
        check(AgentPlatform.parse("managed_agent")==AgentPlatform.MANAGED_AGENT,"managed enum parsed");
        AgentSessionClient paseo=AgentPlatformAssembler.assemble(AgentPlatform.PASEO,new PaseoConfigStore.Config("ws://test/ws","","client"),null);
        check(paseo.platform()==AgentPlatform.PASEO,"Paseo adapter assembled");paseo.close();
        AgentSessionClient managed=AgentPlatformAssembler.assemble(AgentPlatform.MANAGED_AGENT,null,new ManagedAgentClient.Config("https://api.catpaw.sankuai.com","key","env","agent","RayNeo",null));
        check(managed.platform()==AgentPlatform.MANAGED_AGENT,"Managed Agent adapter assembled");managed.close();
        boolean missing=false;try{AgentPlatformAssembler.assemble(AgentPlatform.MANAGED_AGENT,null,null);}catch(IllegalStateException expected){missing=true;}check(missing,"missing platform config rejected");
        System.out.println("PASS "+checks+" Agent platform assembly checks");
    }
}
