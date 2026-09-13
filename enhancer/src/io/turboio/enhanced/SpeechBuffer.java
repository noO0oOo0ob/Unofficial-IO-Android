package io.turboio.enhanced;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** 16 kHz mono PCM. Conservative energy endpoint; hard caps apply even without silence. */
final class SpeechBuffer {
    private final byte[] pcm=new byte[960000];
    private int size, voicedSamples, quietSamples, windowSamples;
    private long energy;
    boolean add(byte[] b) {
        if(b==null||b.length==0||(b.length&1)!=0||b.length>3840||size+b.length>pcm.length)throw new IllegalArgumentException("invalid audio frame");
        System.arraycopy(b,0,pcm,size,b.length); size+=b.length;
        boolean ended=false;
        for(int i=0;i<b.length;i+=2) {
            int s=(short)((b[i]&255)|(b[i+1]<<8));energy+=(long)s*s;windowSamples++;
            if(windowSamples==320) {
                if(energy/320>500L*500) { voicedSamples+=320;quietSamples=0; } else quietSamples+=320;
                energy=0;windowSamples=0;
                if(voicedSamples>=4800&&quietSamples>=16000)ended=true;
            }
        }
        return ended||size>=640000||(!hasSpeech()&&size>=160000);
    }
    boolean hasSpeech(){return voicedSamples>=4800;}
    byte[] takeWav(){byte[] copy=Arrays.copyOf(pcm,size);try{return DemoWire.wav(copy);}finally{Arrays.fill(copy,(byte)0);}}
    void clear(){Arrays.fill(pcm,(byte)0);size=voicedSamples=quietSamples=windowSamples=0;energy=0;}
}
