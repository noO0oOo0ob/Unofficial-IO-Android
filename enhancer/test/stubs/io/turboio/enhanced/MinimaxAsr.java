package io.turboio.enhanced;
import java.util.concurrent.CountDownLatch;
final class MinimaxAsr {
 static volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
 static volatile int calls;
 static volatile boolean cancelled;
 static volatile String response="测试语音";
 void cancel(){cancelled=true;}
 String transcribe(String key,byte[] wav)throws Exception {calls++;entered.countDown();release.await();return response;}
 static void reset(){entered=new CountDownLatch(1);release=new CountDownLatch(1);calls=0;cancelled=false;response="测试语音";}
}
