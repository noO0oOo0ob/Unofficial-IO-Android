package io.turboio.enhanced;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.*;

/** Diagnostic events only. No audio, transcript, key, device ID or vendor payload is passed here. */
final class DemoTrace {
    private static volatile TraceJournal journal;
    private static volatile boolean storageFailed;
    private static final ExecutorService writer=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),new ThreadPoolExecutor.DiscardOldestPolicy());
    static synchronized void init(Context context) {
        if(journal!=null)return;
        try {journal=new TraceJournal(new File(context.getNoBackupFilesDir(),"turbo-diagnostics.log"));}
        catch(Exception e){storageFailed=true;}
    }
    static void record(String event) {
        Log.i("TurboIODemo",event);
        TraceJournal target=journal;if(target==null)return;
        String line=new SimpleDateFormat("MM-dd HH:mm:ss.SSS",Locale.ROOT).format(new Date())+" "+event;
        writer.execute(()->{try{target.append(line);}catch(Exception e){storageFailed=true;}});
    }
    static String recent() {
        TraceJournal target=journal;
        String text=target==null?"":target.snapshot();
        if(text.isEmpty())text="暂无记录";
        return storageFailed?"日志保存失败，部分记录可能缺失\n"+text:text;
    }
}
