package io.turboio.enhanced;

import java.util.*;
import java.util.regex.*;

/** Deterministic, ASR-tolerant command routing. Ordinary speech remains a query. */
final class VoiceCommandRouter {
    enum Kind { LIST, LIST_NEXT, LIST_PREVIOUS, SELECT_NUMBER, INSPECT_NUMBER, SELECT_TITLE,
        LIST_WORKSPACES, SELECT_WORKSPACE, CREATE_SESSION,
        CURRENT, LAST_REPLY, ANSWER_NEXT, ANSWER_PREVIOUS, STOP, CONFIRM_STOP, CANCEL, EXIT, CHAT, CODEX_QUERY, QUERY }
    static final class Match {
        final Kind kind; final int number; final String value; final double score;
        Match(Kind kind,int number,String value,double score){this.kind=kind;this.number=number;this.value=value;this.score=score;}
        static Match query(String text){return new Match(Kind.QUERY,0,text,0);}
    }
    private static final Map<Character,Character> DIGITS=new HashMap<>();
    static { String a="零一二三四五六七八九";for(int i=0;i<a.length();i++)DIGITS.put(a.charAt(i),(char)('0'+i));DIGITS.put('两','2'); }
    private static final Pattern NUMBER=Pattern.compile("(?:第)?([0-9一二三四五六七八九两十]+)(?:个|条|号)?");
    private static final String[] LIST={"列出会话","列出绘画","显示会话","显示绘画","会话列表","绘画列表","任务列表","有哪些会话","看看会话"};
    private static final String[] NEXT={"下一页","下页","后面几个","更多会话"};
    private static final String[] PREVIOUS={"上一页","上页","前面几个"};
    private static final String[] CURRENT={"当前会话","现在的会话","正在聊哪个","当前任务"};
    private static final String[] LAST={"最后回复","上一条回复","重新显示","再显示一次","看看回复"};
    private static final String[] ANSWER_NEXT={"下一段","后面一段","继续显示"};
    private static final String[] ANSWER_PREVIOUS={"上一段","前面一段"};
    private static final String[] STOP={"停止当前任务","停止任务","终止任务","取消当前任务"};
    private static final String[] CONFIRM={"确认停止","确定停止","确认终止"};
    private static final String[] CANCEL={"取消操作","不用了","算了","不停止"};
    private static final String[] EXIT={"退出","退下","关闭助手","退出助手","结束助手","关闭语音助手","结束语音助手","拜拜","再见"};

    Match route(String speech,List<String> titles,boolean awaitingStopConfirmation) {
        String raw=speech==null?"":speech.trim();String n=normalize(raw);
        if(n.isEmpty())return Match.query(raw);
        Match fixed=bestFixed(n,awaitingStopConfirmation);
        if(fixed!=null&&fixed.score>=0.72)return fixed;
        Match numbered=numbered(n);
        if(numbered!=null)return numbered;
        if(startsAny(n,"继续","选择","打开","切换","基于","进入")) {
            String target=n.replaceFirst("^(继续|选择|打开|切换到?|基于|进入)","").replaceFirst("(这个)?(会话|任务)(继续)?$","");
            Match title=bestTitle(target,titles);
            if(title!=null&&title.score>=0.58)return title;
        }
        return Match.query(raw);
    }

    boolean shouldUseModel(String speech,Match local) {
        if(local==null||local.kind!=Kind.QUERY)return false;
        String n=normalize(speech==null?"":speech);
        String[] hints={"列","显示","会话","绘画","任务","查看","详情","继续","选择","打开","切换","当前","回复","上一","下一","停止","取消","确认","退出","退下","关闭","结束","再见","拜拜"};
        for(String hint:hints)if(n.contains(hint))return true;
        return false;
    }

    private Match bestFixed(String n,boolean confirm) {
        List<Match> all=new ArrayList<>();
        if(confirm){add(all,n,CONFIRM,Kind.CONFIRM_STOP);add(all,n,CANCEL,Kind.CANCEL);}
        add(all,n,LIST,Kind.LIST);add(all,n,NEXT,Kind.LIST_NEXT);add(all,n,PREVIOUS,Kind.LIST_PREVIOUS);
        add(all,n,CURRENT,Kind.CURRENT);add(all,n,LAST,Kind.LAST_REPLY);add(all,n,ANSWER_NEXT,Kind.ANSWER_NEXT);
        add(all,n,ANSWER_PREVIOUS,Kind.ANSWER_PREVIOUS);add(all,n,STOP,Kind.STOP);add(all,n,EXIT,Kind.EXIT);
        return all.isEmpty()?null:Collections.max(all,Comparator.comparingDouble(x->x.score));
    }
    private void add(List<Match> out,String n,String[] aliases,Kind kind){for(String a:aliases){double s=similarity(n,normalize(a));if(n.contains(normalize(a)))s=Math.max(s,.96);out.add(new Match(kind,0,"",s));}}
    private Match numbered(String n) {
        Matcher m=NUMBER.matcher(n);if(!m.find())return null;int value=parseNumber(m.group(1));if(value<1)return null;
        if(startsAny(n,"查看","详情","看看"))return new Match(Kind.INSPECT_NUMBER,value,"",.96);
        if(startsAny(n,"第","选择","继续","打开","切换","基于","进入"))return new Match(Kind.SELECT_NUMBER,value,"",.94);
        return null;
    }
    private Match bestTitle(String target,List<String> titles){Match best=null;for(int i=0;i<titles.size();i++){double s=similarity(target,normalize(titles.get(i)));if(best==null||s>best.score)best=new Match(Kind.SELECT_TITLE,i+1,titles.get(i),s);}return best;}
    private static boolean startsAny(String n,String... values){for(String v:values)if(n.startsWith(v))return true;return false;}
    static String normalize(String value){StringBuilder b=new StringBuilder();for(int i=0;i<value.length();i++){char c=Character.toLowerCase(value.charAt(i));if(Character.isLetterOrDigit(c)||c>=0x4e00&&c<=0x9fff)b.append(DIGITS.getOrDefault(c,c));}return b.toString().replace("session","会话").replace("塞申","会话").replace("对话","会话");}
    static int parseNumber(String s){s=normalize(s);try{return Integer.parseInt(s);}catch(Exception ignored){}if(s.equals("十"))return 10;int ten=s.indexOf('十');if(ten>=0){int a=ten==0?1:digit(s.charAt(ten-1));int b=ten==s.length()-1?0:digit(s.charAt(ten+1));return a*10+b;}return s.length()==1?digit(s.charAt(0)):-1;}
    private static int digit(char c){if(c>='0'&&c<='9')return c-'0';Character d=DIGITS.get(c);return d==null?-1:d-'0';}
    static double similarity(String a,String b){if(a.equals(b))return 1;if(a.isEmpty()||b.isEmpty())return 0;if(a.contains(b)||b.contains(a))return (double)Math.min(a.length(),b.length())/Math.max(a.length(),b.length())*.15+.82;int[] prev=new int[b.length()+1],cur=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;for(int i=1;i<=a.length();i++){cur[0]=i;for(int j=1;j<=b.length();j++)cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));int[] t=prev;prev=cur;cur=t;}return 1-(double)prev[b.length()]/Math.max(a.length(),b.length());}
}
