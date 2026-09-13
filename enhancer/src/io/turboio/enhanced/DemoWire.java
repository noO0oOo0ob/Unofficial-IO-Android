package io.turboio.enhanced;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** AssistantMsg, version 1. All parsing is bounded; unknown fields are skipped. */
final class DemoWire {
    final int type;
    final String json;
    final byte[] audio;
    DemoWire(int type, String json, byte[] audio) { this.type=type; this.json=json; this.audio=audio; }
    static DemoWire parse(byte[] bytes) {
        if (bytes == null || bytes.length > 65536) throw new IllegalArgumentException("packet size");
        int[] p={0}; int type=0, version=0; String json=""; byte[] audio=new byte[0];
        while(p[0]<bytes.length) {
            int tag=read(bytes,p), field=tag>>>3, wire=tag&7;
            if(field==0) throw new IllegalArgumentException("tag");
            if(wire==0) { int v=read(bytes,p); if(field==1)version=v; if(field==2)type=v; }
            else if(wire==2) {
                int len=read(bytes,p); if(len<0||len>bytes.length-p[0])throw new IllegalArgumentException("length");
                if(field==3) json=new String(bytes,p[0],len,StandardCharsets.UTF_8);
                if(field==4) audio=Arrays.copyOfRange(bytes,p[0],p[0]+len);
                p[0]+=len;
            } else if(wire==1||wire==5) { p[0]+=wire==1?8:4; if(p[0]>bytes.length)throw new IllegalArgumentException("fixed"); }
            else throw new IllegalArgumentException("wire");
        }
        if(version!=1)throw new IllegalArgumentException("version");
        return new DemoWire(type,json,audio);
    }
    private static int read(byte[] b,int[] p) {
        int v=0;
        for(int n=0;n<5;n++) { if(p[0]>=b.length)throw new IllegalArgumentException("truncated"); int x=b[p[0]++]&255;
            if(n==4&&(x&240)!=0)throw new IllegalArgumentException("overflow"); v|=(x&127)<<(n*7); if((x&128)==0)return v; }
        throw new IllegalArgumentException("varint");
    }
    static byte[] control(int type,int rc) {
        byte[] body=("{\"rc\":"+rc+"}").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream o=new ByteArrayOutputStream(); o.write(8);o.write(1);o.write(16);o.write(type);o.write(26);o.write(body.length);o.write(body,0,body.length);return o.toByteArray();
    }
    static byte[] wav(byte[] pcm) {
        if(pcm.length==0||(pcm.length&1)!=0||pcm.length>960000)throw new IllegalArgumentException("PCM size");
        ByteArrayOutputStream o=new ByteArrayOutputStream(pcm.length+44);
        ascii(o,"RIFF"); le(o,36+pcm.length,4);ascii(o,"WAVEfmt ");le(o,16,4);le(o,1,2);le(o,1,2);
        le(o,16000,4);le(o,32000,4);le(o,2,2);le(o,16,2);ascii(o,"data");le(o,pcm.length,4);o.write(pcm,0,pcm.length);return o.toByteArray();
    }
    private static void ascii(ByteArrayOutputStream o,String s) { byte[] b=s.getBytes(StandardCharsets.US_ASCII);o.write(b,0,b.length); }
    private static void le(ByteArrayOutputStream o,int n,int count) { for(int i=0;i<count;i++)o.write(n>>>(i*8)); }
}
