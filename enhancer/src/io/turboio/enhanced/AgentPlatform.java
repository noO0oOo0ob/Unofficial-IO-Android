package io.turboio.enhanced;

enum AgentPlatform {
    PASEO("Paseo"),
    MANAGED_AGENT("Managed Agent");

    final String label;
    AgentPlatform(String label){this.label=label;}

    static AgentPlatform parse(String value){
        if(value==null||value.trim().isEmpty())return PASEO;
        try{return valueOf(value.trim().toUpperCase(java.util.Locale.US));}
        catch(IllegalArgumentException ignored){return PASEO;}
    }
}
