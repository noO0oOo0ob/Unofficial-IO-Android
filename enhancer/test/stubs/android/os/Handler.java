package android.os;
import java.util.*;
/** Deterministic scheduler, no Android runtime emulation claimed. */
public class Handler {
    public static long now;private static long sequence;
    private static class Entry {long at,id;Runnable r;Entry(long a,Runnable r){at=a;id=sequence++;this.r=r;}}
    private static final PriorityQueue<Entry> q=new PriorityQueue<>((a,b)->a.at==b.at?Long.compare(a.id,b.id):Long.compare(a.at,b.at));
    public Handler(Looper l){}
    public boolean post(Runnable r){return postDelayed(r,0);}
    public boolean postDelayed(Runnable r,long d){synchronized(q){q.add(new Entry(now+d,r));}return true;}
    public static void advance(long millis){long target=now+millis;for(;;){Entry e;synchronized(q){if(q.isEmpty()||q.peek().at>target){now=target;return;}e=q.remove();now=e.at;}e.r.run();}}
    public void removeCallbacks(Runnable r){synchronized(q){q.removeIf(e->e.r==r);}}
    public static int pending(){synchronized(q){return q.size();}}
    public static void reset(){synchronized(q){q.clear();now=0;}}
}
