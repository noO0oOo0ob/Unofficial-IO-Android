package io.turboio.enhanced;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
final class MinimaxResponsesClient {
 interface Listener {void onText(String cumulative);}
 static volatile int calls;static volatile String lastQuery="";static volatile boolean cancelled,block;
 static volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
 static void reset(){calls=0;lastQuery="";cancelled=false;block=false;entered=new CountDownLatch(1);release=new CountDownLatch(1);}
 void cancel(){cancelled=true;release.countDown();}
 String summarizeForNotification(String key,String source){return"摘要纯文本";}
 String complete(String key,String query,String context,Listener listener)throws Exception{calls++;lastQuery=query;listener.onText("这是流式");entered.countDown();if(block)release.await();if(cancelled)throw new InterruptedIOException();listener.onText("这是流式 MiniMax 回复");return"这是流式 MiniMax 回复";}
}
