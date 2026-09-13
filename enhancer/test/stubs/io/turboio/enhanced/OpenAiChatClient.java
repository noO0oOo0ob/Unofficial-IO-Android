package io.turboio.enhanced;
final class OpenAiChatClient {
 static volatile int calls;static volatile String lastQuery="";static void reset(){calls=0;lastQuery="";}
 void cancel(){} String complete(ChatModelConfigStore.Config c,String query){calls++;lastQuery=query;return"这是普通聊天模型回复";}
}
