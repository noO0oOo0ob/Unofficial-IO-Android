package io.turboio.enhanced;
import java.util.*;import java.util.concurrent.CopyOnWriteArrayList;
final class VendorTransport {
 interface Completion {void done(boolean ok);}
 static final List<Sent> sent=new CopyOnWriteArrayList<>();
 static boolean idle=true,online=true,autoAck=true;
 static class Sent {final int business;final byte[] bytes;final Completion callback;Sent(int b,byte[] p,Completion c){business=b;bytes=p;callback=c;}}
 final String device="glasses";
 VendorTransport(){}
 VendorTransport(boolean checkIdle){if(checkIdle)requireIdle();}
 String readiness(){return idle?"就绪":"等待：官方音频业务占用";}
 void prepareOrdinaryWake(){requireIdle();}
 void requireIdle(){if(!idle)throw new IllegalStateException("busy");if(!online)throw new IllegalStateException("offline");}
 boolean connected(){return online;}
 void send(int b,byte[] p,Completion c){sent.add(new Sent(b,p,c));if(autoAck)c.done(true);}
 Decoder decoder(){return new Decoder();}
 final class Decoder {byte[] decode(byte[] b){return b;}void close(){}}
 static void reset(){sent.clear();idle=online=autoAck=true;}
}
