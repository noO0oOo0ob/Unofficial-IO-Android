package io.turboio.enhanced;

import java.util.regex.Pattern;

/** Produces one short, plain-text lens notification while preserving the full Agent result elsewhere. */
final class NotificationResultFormatter {
    static final int MAX_CODE_POINTS=240;
    interface Summarizer {String summarize(String source)throws Exception;}
    private static final Pattern LINK=Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    private NotificationResultFormatter(){}

    static String format(String apiKey,String source){return format(source,value->new MinimaxResponsesClient().summarizeForNotification(apiKey,value));}
    static String formatLocally(String source){return bound(plainText(source),MAX_CODE_POINTS);}
    static String format(String source,Summarizer summarizer){String plain=plainText(source);if(codePoints(plain)<=MAX_CODE_POINTS)return plain;try{String summarized=plainText(summarizer.summarize(source));if(!summarized.isEmpty())return bound(summarized,MAX_CODE_POINTS);}catch(Exception ignored){}return bound(plain,MAX_CODE_POINTS);}

    static String plainText(String value){String text=value==null?"":value.replace("```","").replace('`',' ').replace("**","").replace("__","");text=LINK.matcher(text).replaceAll("$1");StringBuilder out=new StringBuilder();for(String raw:text.split("\\r?\\n")){String line=raw.trim().replaceFirst("^#{1,6}\\s*","").replaceFirst("^[-*+]\\s+","").replaceFirst("^(\\d+)[.)]\\s+","$1、").trim();if(line.isEmpty())continue;if(out.length()>0)out.append('\n');out.append(line);}return out.toString().trim();}
    static String bound(String value,int max){String clean=value==null?"":value.trim();if(codePoints(clean)<=max)return clean;int cut=clean.offsetByCodePoints(0,max-1);String prefix=clean.substring(0,cut);int natural=Math.max(prefix.lastIndexOf('。'),Math.max(prefix.lastIndexOf('！'),Math.max(prefix.lastIndexOf('？'),prefix.lastIndexOf('\n'))));if(natural>max/2)prefix=prefix.substring(0,natural+1);return prefix.trim()+"…";}
    private static int codePoints(String value){return value.codePointCount(0,value.length());}
}
