package io.turboio.enhanced;

/** This is the only new class the original method touches when mode is OFF. */
public final class DemoHooks {
    static volatile DemoController active;
    private DemoHooks(){}
    public static boolean onAssistantMessage(String device,byte[] payload) {
        DemoController controller=active;
        if(controller==null)return false;
        return controller.onMessage(device,payload);
    }
}
