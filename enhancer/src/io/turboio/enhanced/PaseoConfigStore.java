package io.turboio.enhanced;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Paseo endpoint metadata plus an AndroidKeyStore-encrypted optional daemon password. */
final class PaseoConfigStore {
    static final class Config {final String url,password,clientId;Config(String u,String p,String c){url=u;password=p;clientId=c;}}
    private static final String ALIAS="turbo_paseo_password_v1",PREF="turbo-paseo";
    private final Context context;private final android.util.AtomicFile secret;
    PaseoConfigStore(Context c){context=c;secret=new android.util.AtomicFile(new File(c.getNoBackupFilesDir(),"turbo-paseo-password"));}
    synchronized void save(String url,String password)throws Exception {String u=normalizeUrl(url);String p=password==null?"":password.trim();if(p.length()>4096||p.indexOf('\r')>=0||p.indexOf('\n')>=0)throw new IllegalArgumentException("Paseo 密码格式无效");if(!p.isEmpty())writeSecret(p);context.getSharedPreferences(PREF,0).edit().putString("url",u).apply();}
    synchronized void clearPassword(){secret.delete();}
    synchronized Config read()throws Exception {String u=context.getSharedPreferences(PREF,0).getString("url","");if(u.isEmpty())throw new IllegalStateException("请先保存 Paseo WebSocket 地址");return new Config(normalizeUrl(u),readSecret(),clientId());}
    synchronized String url(){return context.getSharedPreferences(PREF,0).getString("url","");}
    synchronized String selectedAgent(){return context.getSharedPreferences(PREF,0).getString("agent","");}
    synchronized void selectAgent(String id){context.getSharedPreferences(PREF,0).edit().putString("agent",id==null?"":id).apply();}
    synchronized String selectedWorkspace(){return context.getSharedPreferences(PREF,0).getString("workspace","");}
    synchronized void selectWorkspace(String id){context.getSharedPreferences(PREF,0).edit().putString("workspace",id==null?"":id).apply();}
    synchronized boolean hasPassword(){return secret.getBaseFile().isFile();}
    private String clientId(){android.content.SharedPreferences p=context.getSharedPreferences(PREF,0);String id=p.getString("clientId","");if(id.isEmpty()){id="rayneo-"+UUID.randomUUID();p.edit().putString("clientId",id).apply();}return id;}
    static String normalizeUrl(String value){try{String v=value==null?"":value.trim();URI u=new URI(v);if(!("ws".equalsIgnoreCase(u.getScheme())||"wss".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null)throw new Exception();String path=u.getRawPath();if(path==null||path.isEmpty()||"/".equals(path))return new URI(u.getScheme(),null,u.getHost(),u.getPort(),"/ws",u.getRawQuery(),null).toString();return u.toString();}catch(Exception e){throw new IllegalArgumentException("地址格式应为 ws://电脑IP:6767/ws");}}
    private SecretKey key()throws Exception{KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);if(!s.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return(SecretKey)s.getKey(ALIAS,null);}
    private void writeSecret(String p)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=c.doFinal(p.getBytes(StandardCharsets.UTF_8));FileOutputStream out=null;try{out=secret.startWrite();out.write(c.getIV().length);out.write(c.getIV());out.write(encrypted);secret.finishWrite(out);}catch(Exception e){if(out!=null)secret.failWrite(out);throw e;}}
    private String readSecret()throws Exception{if(!secret.getBaseFile().isFile())return"";byte[] b=secret.readFully();if(b.length<30||b[0]!=12)throw new IOException("Paseo 密码存储损坏");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,b,1,12));return new String(c.doFinal(b,13,b.length-13),StandardCharsets.UTF_8);}
}
