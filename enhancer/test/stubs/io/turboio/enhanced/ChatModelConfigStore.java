package io.turboio.enhanced;
final class ChatModelConfigStore {
 static final class Config {final String baseUrl,model,apiKey;Config(String u,String m,String k){baseUrl=u;model=m;apiKey=k;}String endpoint(){return baseUrl+"/chat/completions";}}
 ChatModelConfigStore(android.content.Context c){}
 Config read(String fallback){return new Config("https://example.invalid/v1","test-chat",fallback);}
 void save(String u,String m,String k){} void clearKey(){} String baseUrl(){return"https://example.invalid/v1";}String model(){return"test-chat";}boolean hasKey(){return false;}
}
