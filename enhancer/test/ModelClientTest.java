package io.turboio.enhanced;
import java.util.*;import org.json.*;
public final class ModelClientTest {
 private static int checks;private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  List<String> titles=Arrays.asList("代码排查","旅行计划");
  List<String> workspaces=Arrays.asList("main");
  check(MinimaxIntentRouter.parseContent("```json\n{\"intent\":\"CHAT\"}\n```","今天天气怎么样",titles,workspaces).kind==VoiceCommandRouter.Kind.CHAT,"chat intent");
  check(MinimaxIntentRouter.parseContent("<think>x</think>{\"intent\":\"CODEX_QUERY\"}","检查工程",titles,workspaces).kind==VoiceCommandRouter.Kind.CODEX_QUERY,"codex intent");
  check(MinimaxIntentRouter.parseContent("{\"intent\":\"SELECT_NUMBER\",\"number\":2}","继续第二个",titles,workspaces).number==2,"number intent");
  check(MinimaxIntentRouter.parseContent("{\"intent\":\"SELECT_WORKSPACE\",\"number\":1}","选择工作区",titles,workspaces).kind==VoiceCommandRouter.Kind.SELECT_WORKSPACE,"workspace intent");
  check(MinimaxIntentRouter.MODEL.equals("MiniMax-M3")&&MinimaxIntentRouter.ENDPOINT.endsWith("/v1/responses"),"M3 Responses intent route");
  check("{\"intent\":\"CHAT\"}".equals(MinimaxIntentRouter.responseText(new JSONObject("{\"status\":\"completed\",\"output_text\":\"{\\\"intent\\\":\\\"CHAT\\\"}\"}"))),"intent Responses output text");
  JSONObject delta=new JSONObject("{\"type\":\"response.output_text.delta\",\"delta\":\"你好\"}");check("你好".equals(MinimaxResponsesClient.deltaText(delta)),"responses delta");
  JSONObject completed=new JSONObject("{\"type\":\"response.completed\",\"response\":{\"output_text\":\"完成\"}}");check("完成".equals(MinimaxResponsesClient.finalText(completed)),"responses completed");
  check(MinimaxResponsesClient.ENDPOINT.equals("https://api.minimax.cn/v1/responses"),"China Responses endpoint");
  check(MinimaxResponsesClient.requiresFreshInformation("今天天气怎么样")&&!MinimaxResponsesClient.requiresFreshInformation("写一首诗"),"fresh-information routing hint");
  check(MinimaxResponsesClient.currentClock().matches(".*20[0-9]{2}-[0-9]{2}-[0-9]{2}.*"),"runtime clock injected");
  check(MinimaxResponsesClient.containsWebSearch(new JSONObject("{\"type\":\"response.output_item.added\",\"item\":{\"type\":\"web_search_call\"}}")),"web search evidence detection");
  System.out.println("PASS "+checks+" contextual routing/MiniMax Responses checks");
 }
}
