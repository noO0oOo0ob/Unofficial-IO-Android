package io.turboio.enhanced;

final class AgentPlatformAssembler {
    private AgentPlatformAssembler(){}

    static AgentSessionClient assemble(AgentPlatform platform,PaseoConfigStore.Config paseo,ManagedAgentClient.Config managed){
        AgentPlatform selected=platform==null?AgentPlatform.PASEO:platform;
        switch(selected){
            case MANAGED_AGENT:
                if(managed==null)throw new IllegalStateException("请先配置 Managed Agent");
                return new ManagedAgentClient(managed);
            case PASEO:
            default:
                if(paseo==null)throw new IllegalStateException("请先配置 Paseo 源");
                return new PaseoAgentSessionClient(paseo);
        }
    }
}
