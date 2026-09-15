package io.turboio.enhanced;
import java.nio.file.*;
public final class TraceJournalTest {
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory("turbo-journal-test-");Path file=dir.resolve("trace.log");
  try {
   TraceJournal journal=new TraceJournal(file.toFile());journal.append("wake rc=1");
   if(!new TraceJournal(file.toFile()).snapshot().equals("wake rc=1"))throw new AssertionError("reopen lost trace");
   for(int i=0;i<120;i++)journal.append("event="+i);
   String restored=new TraceJournal(file.toFile()).snapshot();
   if(restored.split("\n").length!=80||!restored.startsWith("event=40\n")||!restored.endsWith("event=119"))throw new AssertionError("retention");
   journal.append("wake\nforged row\rtest");if(!journal.snapshot().endsWith("wake forged row test"))throw new AssertionError("newline sanitation");
   StringBuilder longLine=new StringBuilder();for(int i=0;i<1000;i++)longLine.append('x');journal.append(longLine.toString());
   String last=journal.snapshot().substring(journal.snapshot().lastIndexOf('\n')+1);if(last.length()!=512)throw new AssertionError("line bound");
   String unicode=longLine.toString().replace('x','\u667a');
   for(int i=0;i<100;i++)journal.append(unicode);
   if(!new TraceJournal(file.toFile()).snapshot().equals(journal.snapshot()))throw new AssertionError("unicode reopen");
   if(Files.size(file)>65536||Files.exists(dir.resolve("trace.log.tmp")))throw new AssertionError("size/atomic replacement");
   journal.clear();if(!journal.snapshot().isEmpty()||Files.exists(file))throw new AssertionError("clear");
   System.out.println("PASS journal reopen, retention, sanitation, size, atomic replacement and clear checks");
  }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir.resolve("trace.log.tmp"));Files.delete(dir);}
 }
}
