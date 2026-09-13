package io.turboio.enhanced;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** OpenAI-compatible chat endpoint/model plus an optional dedicated encrypted key. */
final class ChatModelConfigStore {
    static final String DEFAULT_URL="https://api.minimax.cn/v1",DEFAULT_MODEL="MiniMax-M3";
    static final class Config {final String baseUrl,model,apiKey;Config(String u,String m,String k){baseUrl=u;model=m;apiKey=k;}String endpoint(){return baseUrl.endsWith("/chat/completions")?baseUrl:baseUrl+"/chat/completions";}}
    private static final String ALIAS="turbo_chat_model_key_v1",PREF="turbo-chat-model";
    private final Context context;private final android.util.AtomicFile secret;
    ChatModelConfigStore(Context c){context=c;secret=new android.util.AtomicFile(new File(c.getNoBackupFilesDir(),"turbo-chat-model-key"));}
    synchronized void save(String url,String model,String apiKey)throws Exception {String u=normalizeUrl(url),m=normalizeModel(model),k=apiKey==null?"":apiKey.trim();if(k.length()>8192||k.indexOf('\r')>=0||k.indexOf('\n')>=0)throw new IllegalArgumentException("聊天模型 API Key 格式无效");if(!k.isEmpty())writeSecret(k);context.getSharedPreferences(PREF,0).edit().putString("url",u).putString("model",m).apply();}
    synchronized Config read(String minimaxKey)throws Exception {String u=baseUrl(),m=model(),k=readSecret();if(k.isEmpty()&&isMinimax(u))k=minimaxKey;return new Config(normalizeUrl(u),normalizeModel(m),k);}
    synchronized String baseUrl(){return context.getSharedPreferences(PREF,0).getString("url",DEFAULT_URL);}
    synchronized String model(){return context.getSharedPreferences(PREF,0).getString("model",DEFAULT_MODEL);}
    synchronized boolean hasKey(){return secret.getBaseFile().isFile();}
    synchronized void clearKey()throws Exception{secret.delete();KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);s.deleteEntry(ALIAS);}
    static String normalizeUrl(String value){try{String v=value==null?"":value.trim();while(v.endsWith("/"))v=v.substring(0,v.length()-1);URI u=new URI(v);if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||u.getQuery()!=null)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException("模型地址格式应为 https://host/v1");}}
    private static String normalizeModel(String value){String v=value==null?"":value.trim();if(v.isEmpty()||v.length()>200||v.indexOf('\r')>=0||v.indexOf('\n')>=0)throw new IllegalArgumentException("模型名称格式无效");return v;}
    private static boolean isMinimax(String url){try{return"api.minimaxi.com".equalsIgnoreCase(new URI(url).getHost());}catch(Exception e){return false;}}
    private SecretKey key()throws Exception{KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);if(!s.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return(SecretKey)s.getKey(ALIAS,null);}
    private void writeSecret(String value)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=c.doFinal(value.getBytes(StandardCharsets.UTF_8));FileOutputStream out=null;try{out=secret.startWrite();out.write(c.getIV().length);out.write(c.getIV());out.write(encrypted);secret.finishWrite(out);}catch(Exception e){if(out!=null)secret.failWrite(out);throw e;}}
    private String readSecret()throws Exception{if(!secret.getBaseFile().isFile())return"";byte[] b=secret.readFully();if(b.length<30||b[0]!=12)throw new IOException("聊天模型 Key 存储损坏");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,b,1,12));return new String(c.doFinal(b,13,b.length-13),StandardCharsets.UTF_8);}
}
