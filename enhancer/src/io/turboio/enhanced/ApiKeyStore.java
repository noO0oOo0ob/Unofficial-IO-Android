package io.turboio.enhanced;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Ciphertext in noBackupFilesDir; key never leaves AndroidKeyStore. */
final class ApiKeyStore {
    private static final String ALIAS="turbo_minimax_key_v1";
    private final android.util.AtomicFile file;
    ApiKeyStore(Context c){file=new android.util.AtomicFile(new File(c.getNoBackupFilesDir(),"turbo-minimax-key"));}
    synchronized boolean hasKey(){return file.getBaseFile().isFile();}
    private SecretKey key() throws Exception {
        KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);
        if(!s.containsAlias(ALIAS)) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }
        return (SecretKey)s.getKey(ALIAS,null);
    }
    synchronized void save(String value)throws Exception {
        String v=value.trim();if(v.length()<16||v.length()>8192||!v.matches("[!-~]+"))throw new IllegalArgumentException("API Key 格式无效");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=c.doFinal(v.getBytes(StandardCharsets.UTF_8));
        FileOutputStream out=null;try {out=file.startWrite();out.write(c.getIV().length);out.write(c.getIV());out.write(encrypted);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    synchronized String read()throws Exception {
        byte[] bytes=file.readFully();if(bytes.length<30||bytes.length>16384||bytes[0]!=12)throw new IOException("key storage");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,bytes,1,12));
        return new String(c.doFinal(bytes,13,bytes.length-13),StandardCharsets.UTF_8);
    }
    synchronized void clear()throws Exception {file.delete();KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);s.deleteEntry(ALIAS);}
}
