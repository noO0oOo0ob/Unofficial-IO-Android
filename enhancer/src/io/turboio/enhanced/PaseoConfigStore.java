package io.turboio.enhanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

/** Named Paseo endpoints with per-source state and AndroidKeyStore-encrypted passwords. */
final class PaseoConfigStore {
    static final class Config {final String url,password,clientId;Config(String u,String p,String c){url=u;password=p;clientId=c;}}
    static final class Source {final String id,name,url;final boolean hasPassword;Source(String i,String n,String u,boolean p){id=i;name=n;url=u;hasPassword=p;}}
    private static final String ALIAS="turbo_paseo_password_v1",PREF="turbo-paseo",SOURCES="sources_json",ACTIVE="active_source",LEGACY="legacy";
    private final Context context;private final SharedPreferences prefs;
    PaseoConfigStore(Context c){context=c;prefs=c.getSharedPreferences(PREF,0);migrateLegacy();}

    synchronized List<Source> sources(){JSONArray all=load();List<Source> result=new ArrayList<>();for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o!=null)result.add(toSource(o));}return result;}
    synchronized Source activeSource(){JSONObject o=find(activeSourceId());return o==null?null:toSource(o);}
    synchronized String activeSourceId(){migrateLegacy();String id=prefs.getString(ACTIVE,"");if(!id.isEmpty()&&find(id)!=null)return id;JSONArray all=load();return all.length()==0?"":all.optJSONObject(0).optString("id","");}
    synchronized String saveSource(String id,String name,String url,String password)throws Exception {
        if(name==null||name.trim().isEmpty())throw new IllegalArgumentException("请输入 Paseo 源名称");String cleanName=name.trim();if(cleanName.length()>40)throw new IllegalArgumentException("Paseo 源名称不能超过 40 个字符");
        String cleanUrl=normalizeUrl(url),cleanPassword=password==null?"":password.trim();validatePassword(cleanPassword);
        String previousActive=activeSourceId();JSONArray all=load();JSONObject item=id==null||id.isEmpty()?null:findIn(all,id);boolean created=item==null;
        if(created){id=UUID.randomUUID().toString();item=new JSONObject();item.put("id",id);item.put("clientId","rayneo-"+UUID.randomUUID());item.put("agent","");item.put("workspace","");all.put(item);}
        item.put("name",cleanName);item.put("url",cleanUrl);if(!cleanPassword.isEmpty())writeSecret(id,cleanPassword);save(all,created?id:previousActive);return id;
    }
    synchronized void selectSource(String id){if(find(id)==null)throw new IllegalArgumentException("Paseo 源不存在");prefs.edit().putString(ACTIVE,id).apply();}
    synchronized void deleteSource(String id){JSONArray all=load(),next=new JSONArray();for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o!=null&&!id.equals(o.optString("id")))next.put(o);}secret(id).delete();String active=prefs.getString(ACTIVE,"");if(id.equals(active))active=next.length()==0?"":next.optJSONObject(0).optString("id","");save(next,active);}
    synchronized Config read()throws Exception {String id=activeSourceId();JSONObject o=find(id);if(o==null)throw new IllegalStateException("请先添加 Paseo 源");return new Config(normalizeUrl(o.optString("url")),readSecret(id),clientId(o));}
    synchronized Config readSource(String id)throws Exception {JSONObject o=find(id);if(o==null)throw new IllegalArgumentException("Paseo 源不存在");return new Config(normalizeUrl(o.optString("url")),readSecret(id),clientId(o));}
    synchronized void save(String url,String password)throws Exception {Source source=activeSource();saveSource(source==null?null:source.id,source==null?"我的电脑":source.name,url,password);}
    synchronized void clearPassword(){clearPassword(activeSourceId());}
    synchronized void clearPassword(String id){if(id!=null&&!id.isEmpty())secret(id).delete();}
    synchronized String url(){Source source=activeSource();return source==null?"":source.url;}
    synchronized String selectedAgent(){return field("agent");}
    synchronized void selectAgent(String id){setField("agent",id);}
    synchronized String selectedWorkspace(){return field("workspace");}
    synchronized void selectWorkspace(String id){setField("workspace",id);}
    synchronized boolean hasPassword(){String id=activeSourceId();return !id.isEmpty()&&secret(id).getBaseFile().isFile();}

    private void migrateLegacy(){if(prefs.contains(SOURCES))return;JSONArray all=new JSONArray();String url=prefs.getString("url","");String active="";if(!url.isEmpty()){JSONObject o=new JSONObject();try{o.put("id",LEGACY);o.put("name","我的电脑");o.put("url",url);o.put("clientId",prefs.getString("clientId",""));o.put("agent",prefs.getString("agent",""));o.put("workspace",prefs.getString("workspace",""));all.put(o);active=LEGACY;}catch(Exception ignored){}}save(all,active);}
    private JSONArray load(){migrateLegacy();try{return new JSONArray(prefs.getString(SOURCES,"[]"));}catch(Exception e){throw new IllegalStateException("Paseo 源配置损坏");}}
    private JSONObject find(String id){return findIn(load(),id);}
    private static JSONObject findIn(JSONArray all,String id){for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o!=null&&id.equals(o.optString("id")))return o;}return null;}
    private Source toSource(JSONObject o){String id=o.optString("id","");return new Source(id,o.optString("name","未命名电脑"),o.optString("url",""),secret(id).getBaseFile().isFile());}
    private void save(JSONArray all,String active){prefs.edit().putString(SOURCES,all.toString()).putString(ACTIVE,active==null?"":active).apply();}
    private String field(String name){JSONObject o=find(activeSourceId());return o==null?"":o.optString(name,"");}
    private void setField(String name,String value){JSONArray all=load();String active=activeSourceId();JSONObject o=findIn(all,active);if(o==null)return;try{o.put(name,value==null?"":value);}catch(Exception ignored){}save(all,active);}
    private String clientId(JSONObject o){String id=o.optString("clientId","");if(id.isEmpty()){id="rayneo-"+UUID.randomUUID();JSONArray all=load();JSONObject saved=findIn(all,o.optString("id"));if(saved!=null)try{saved.put("clientId",id);}catch(Exception ignored){}save(all,activeSourceId());}return id;}
    static String normalizeUrl(String value){try{String v=value==null?"":value.trim();URI u=new URI(v);if(!("ws".equalsIgnoreCase(u.getScheme())||"wss".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null)throw new Exception();String path=u.getRawPath();if(path==null||path.isEmpty()||"/".equals(path))return new URI(u.getScheme(),null,u.getHost(),u.getPort(),"/ws",u.getRawQuery(),null).toString();return u.toString();}catch(Exception e){throw new IllegalArgumentException("地址格式应为 ws://电脑IP:6767/ws");}}
    private static void validatePassword(String p){if(p.length()>4096||p.indexOf('\r')>=0||p.indexOf('\n')>=0)throw new IllegalArgumentException("Paseo 密码格式无效");}
    private AtomicFile secret(String id){String suffix=LEGACY.equals(id)?"":"-"+id.replaceAll("[^A-Za-z0-9_-]","");return new AtomicFile(new File(context.getNoBackupFilesDir(),"turbo-paseo-password"+suffix));}
    private SecretKey key()throws Exception{KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);if(!s.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return(SecretKey)s.getKey(ALIAS,null);}
    private void writeSecret(String id,String p)throws Exception{AtomicFile f=secret(id);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=c.doFinal(p.getBytes(StandardCharsets.UTF_8));FileOutputStream out=null;try{out=f.startWrite();out.write(c.getIV().length);out.write(c.getIV());out.write(encrypted);f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}}
    private String readSecret(String id)throws Exception{AtomicFile f=secret(id);if(!f.getBaseFile().isFile())return"";byte[] b=f.readFully();if(b.length<30||b[0]!=12)throw new IOException("Paseo 密码存储损坏");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,b,1,12));return new String(c.doFinal(b,13,b.length-13),StandardCharsets.UTF_8);}
}
