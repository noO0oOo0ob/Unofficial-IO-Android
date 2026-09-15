package io.turboio.enhanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Persists the active Agent platform and MA-only settings; the MA API key is encrypted. */
final class AgentPlatformConfigStore {
    private static final String PREF="turbo-agent-platform-v1",ALIAS="turbo_managed_agent_key_v1";
    private final SharedPreferences prefs;private final AtomicFile apiKeyFile;
    AgentPlatformConfigStore(Context context){prefs=context.getSharedPreferences(PREF,0);apiKeyFile=new AtomicFile(new File(context.getNoBackupFilesDir(),"turbo-managed-agent-key"));}

    synchronized AgentPlatform platform(){return AgentPlatform.parse(prefs.getString("platform",null));}
    synchronized void selectPlatform(AgentPlatform value){prefs.edit().putString("platform",(value==null?AgentPlatform.PASEO:value).name()).apply();}
    synchronized String baseUrl(){return prefs.getString("ma_base_url","");}
    synchronized String environmentId(){return prefs.getString("ma_environment_id","");}
    synchronized String agentId(){return prefs.getString("ma_agent_id","");}
    synchronized String title(){return prefs.getString("ma_title","RayNeo 眼镜会话");}
    synchronized String version(){return prefs.getString("ma_agent_version","");}
    synchronized String selectedSession(){return prefs.getString("ma_session","");}
    synchronized void selectSession(String id){prefs.edit().putString("ma_session",clean(id)).apply();}
    synchronized boolean hasApiKey(){return apiKeyFile.getBaseFile().isFile();}

    synchronized void saveManaged(String baseUrl,String environmentId,String agentId,String title,String version,String apiKey)throws Exception{
        String normalized=ManagedAgentClient.normalizeBaseUrl(baseUrl);String env=required(environmentId,"请输入 Environment ID"),agent=required(agentId,"请输入 Agent ID");
        String v=clean(version);if(!v.isEmpty()){try{if(Integer.parseInt(v)<1)throw new Exception();}catch(Exception e){throw new IllegalArgumentException("Agent Version 必须为正整数");}}
        SharedPreferences.Editor edit=prefs.edit().putString("ma_base_url",normalized).putString("ma_environment_id",env).putString("ma_agent_id",agent).putString("ma_title",clean(title)).putString("ma_agent_version",v);
        if(!agent.equals(agentId())||!env.equals(environmentId()))edit.remove("ma_session");edit.apply();if(!clean(apiKey).isEmpty())writeApiKey(apiKey);
    }

    synchronized ManagedAgentClient.Config readManaged()throws Exception{
        String v=version();Integer parsed=v.isEmpty()?null:Integer.valueOf(v);return new ManagedAgentClient.Config(baseUrl(),readApiKey(),environmentId(),agentId(),title(),parsed);
    }

    synchronized void clearApiKey()throws Exception{apiKeyFile.delete();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}
    private void writeApiKey(String value)throws Exception{String clean=required(value,"请输入 Managed Agent API Key");if(clean.length()>8192||clean.indexOf('\r')>=0||clean.indexOf('\n')>=0)throw new IllegalArgumentException("Managed Agent API Key 格式无效");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(clean.getBytes(StandardCharsets.UTF_8));FileOutputStream out=null;try{out=apiKeyFile.startWrite();out.write(cipher.getIV().length);out.write(cipher.getIV());out.write(encrypted);apiKeyFile.finishWrite(out);}catch(Exception e){if(out!=null)apiKeyFile.failWrite(out);throw e;}}
    private String readApiKey()throws Exception{if(!hasApiKey())throw new IllegalStateException("请先保存 Managed Agent API Key");byte[] bytes=apiKeyFile.readFully();if(bytes.length<30||bytes[0]!=12)throw new IOException("Managed Agent API Key 存储损坏");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,bytes,1,12));return new String(cipher.doFinal(bytes,13,bytes.length-13),StandardCharsets.UTF_8);}
    private SecretKey key()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(!store.containsAlias(ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}return(SecretKey)store.getKey(ALIAS,null);}
    private static String required(String value,String message){String clean=clean(value);if(clean.isEmpty())throw new IllegalArgumentException(message);return clean;}
    private static String clean(String value){return value==null?"":value.trim();}
}
