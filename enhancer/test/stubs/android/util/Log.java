package android.util;
public final class Log {
 public static final java.util.List<String> events=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
 public static int i(String tag,String message){events.add(tag+" "+message);return 0;}
}
