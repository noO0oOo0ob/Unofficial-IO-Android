package io.turboio.enhanced;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayDeque;

/** Metadata-only, bounded, atomically replaced journal. Never restores mode/session state. */
final class TraceJournal {
    private static final int MAX_LINES=80, MAX_LINE=512, MAX_FILE=65536;
    private final File file;
    private final ArrayDeque<String> lines=new ArrayDeque<>();
    TraceJournal(File file)throws IOException {
        this.file=file;
        if(file.isFile()&&file.length()<=MAX_FILE) {
            for(String line:new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8).split("\n")) {
                if(!line.isEmpty())add(line);
            }
        }
    }
    private void add(String line) {
        String clean=line.replace('\n',' ').replace('\r',' ');
        if(clean.length()>MAX_LINE)clean=clean.substring(0,MAX_LINE);
        lines.addLast(clean);while(lines.size()>MAX_LINES)lines.removeFirst();
    }
    synchronized void append(String event)throws IOException {
        add(event);
        byte[] data=snapshot().getBytes(StandardCharsets.UTF_8);
        while(data.length>MAX_FILE) {
            lines.removeFirst();
            data=snapshot().getBytes(StandardCharsets.UTF_8);
        }
        File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("journal directory");
        Path temp=new File(parent,file.getName()+".tmp").toPath();
        Files.write(temp,data);
        try {Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
    synchronized String snapshot(){return String.join("\n",lines);}
}
