package io.turboio.enhanced;

public final class NotificationResultFormatterTest {
    private static int checks;private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        String shortText=NotificationResultFormatter.format("**结论**\n- 已完成",source->{throw new AssertionError("short result must not call MiniMax");});
        check("结论\n已完成".equals(shortText),"short Markdown is converted to plain text");
        String longText=repeat("原始内容",80);String summarized=NotificationResultFormatter.format(longText,source->"## 结论\n- **构建成功**\n- [查看](https://example.com)无异常");
        check("结论\n构建成功\n查看无异常".equals(summarized),"summary is forced to plain text");
        String fallback=NotificationResultFormatter.format(longText,source->{throw new java.io.IOException("offline");});
        check(fallback.codePointCount(0,fallback.length())<=NotificationResultFormatter.MAX_CODE_POINTS&&fallback.endsWith("…"),"summary failure uses bounded local fallback");
        String oversizedSummary=NotificationResultFormatter.format(longText,source->repeat("摘要",200));
        check(oversizedSummary.codePointCount(0,oversizedSummary.length())<=NotificationResultFormatter.MAX_CODE_POINTS,"model output has hard length limit");
        check(NotificationResultFormatter.formatLocally("## 标题\n"+longText).codePointCount(0,NotificationResultFormatter.formatLocally("## 标题\n"+longText).length())<=NotificationResultFormatter.MAX_CODE_POINTS,"local-only path is plain and bounded");
        System.out.println("PASS "+checks+" notification result formatting checks");
    }
    private static String repeat(String value,int count){StringBuilder out=new StringBuilder();for(int i=0;i<count;i++)out.append(value);return out.toString();}
}
