package io.turboio.enhanced;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Arrays;
import org.json.JSONObject;

/** One utterance, one HTTPS request; cancellation never retries a paid request. */
final class MinimaxAsr {
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;
    void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    String transcribe(String key,byte[] wav)throws Exception {
        if(cancelled)throw new InterruptedIOException();
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.minimax.cn/v1/speech_to_text").openConnection();connection=c;
        try {
            if(cancelled)throw new InterruptedIOException();
            String boundary="TurboIO"+UUID.randomUUID().toString().replace("-","");
            c.setRequestMethod("POST");c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setDoOutput(true);
            c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
            String prefix=field(boundary,"model","asr-1.0")+field(boundary,"response_format","json")+field(boundary,"stream","false")+"--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"utterance.wav\"\r\nContent-Type: audio/wav\r\n\r\n";
            byte[] head=prefix.getBytes(StandardCharsets.UTF_8), tail=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(head.length+wav.length+tail.length);
            try(OutputStream out=c.getOutputStream()){if(cancelled)throw new InterruptedIOException();out.write(head);out.write(wav);out.write(tail);}
            int status=c.getResponseCode();if(status!=200)throw new IOException("ASR HTTP "+status);
            ByteArrayOutputStream response=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(cancelled)throw new InterruptedIOException();if(response.size()+n>65536)throw new IOException("ASR response too large");response.write(b,0,n);}}
            JSONObject json=new JSONObject(new String(response.toByteArray(),StandardCharsets.UTF_8));
            if(json.has("error")||!json.has("text"))throw new IOException("ASR response invalid");
            String text=json.getString("text").trim();if(text.isEmpty())throw new IOException("ASR empty transcript");return text;
        } finally {c.disconnect();connection=null;Arrays.fill(wav,(byte)0);}
    }
    private static String field(String b,String k,String v){return "--"+b+"\r\nContent-Disposition: form-data; name=\""+k+"\"\r\n\r\n"+v+"\r\n";}
}
