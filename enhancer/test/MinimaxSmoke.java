package io.turboio.enhanced;
import java.nio.file.*;
/** Optional live test: use a user-approved synthetic WAV, key only from environment. */
public final class MinimaxSmoke {
 public static void main(String[] args)throws Exception {
  String key=System.getenv("MINIMAX_API_KEY");if(key==null||key.isEmpty())throw new IllegalStateException("MINIMAX_API_KEY missing");
  byte[] wav=Files.readAllBytes(Paths.get(args[0]));long start=System.nanoTime();
  String result=new MinimaxAsr().transcribe(key,wav);
  System.out.println("MiniMax production client OK; characters="+result.length()+"; elapsedMs="+(System.nanoTime()-start)/1000000);
 }
}
