package io.turboio.enhanced;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.net.ssl.SSLSocketFactory;

/** Small RFC 6455 text client, avoiding a dependency on the vendor app's private HTTP stack. */
final class PaseoWebSocket implements Closeable {
    private static final String MAGIC="258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final Socket socket;private final InputStream in;private final OutputStream out;private final SecureRandom random=new SecureRandom();
    private volatile boolean closed;
    private PaseoWebSocket(Socket s)throws IOException{socket=s;in=s.getInputStream();out=s.getOutputStream();}
    static PaseoWebSocket connect(String endpoint,String password,int timeoutMs)throws Exception{
        URI uri=new URI(endpoint);boolean tls="wss".equalsIgnoreCase(uri.getScheme());int port=uri.getPort()>0?uri.getPort():(tls?443:80);
        Socket raw=new Socket();raw.connect(new InetSocketAddress(uri.getHost(),port),timeoutMs);raw.setSoTimeout(0);
        Socket socket=raw;if(tls){socket=((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(raw,uri.getHost(),port,true);}
        PaseoWebSocket ws=new PaseoWebSocket(socket);ws.handshake(uri,password,timeoutMs);return ws;
    }
    private void handshake(URI uri,String password,int timeoutMs)throws Exception{
        socket.setSoTimeout(timeoutMs);byte[] nonce=new byte[16];random.nextBytes(nonce);String key=android.util.Base64.encodeToString(nonce,android.util.Base64.NO_WRAP);
        int port=uri.getPort();boolean standard=port<0||port==80&&"ws".equalsIgnoreCase(uri.getScheme())||port==443&&"wss".equalsIgnoreCase(uri.getScheme());
        String host=uri.getHost()+(standard?"":":"+port);String path=uri.getRawPath();if(path==null||path.isEmpty())path="/";if(uri.getRawQuery()!=null)path+="?"+uri.getRawQuery();
        StringBuilder h=new StringBuilder("GET ").append(path).append(" HTTP/1.1\r\nHost: ").append(host).append("\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: ").append(key).append("\r\nSec-WebSocket-Version: 13\r\n");
        String p=password==null?"":password.trim();if(!p.isEmpty()){if(!p.matches("[A-Za-z0-9.!#$%&'*+^_`|~-]+"))throw new IOException("Paseo 密码含 WebSocket 不支持的字符");h.append("Authorization: Bearer ").append(p).append("\r\nSec-WebSocket-Protocol: paseo.bearer.").append(p).append("\r\n");}
        h.append("\r\n");out.write(h.toString().getBytes(StandardCharsets.US_ASCII));out.flush();
        ByteArrayOutputStream response=new ByteArrayOutputStream();int state=0;while(response.size()<16384&&state<4){int b=in.read();if(b<0)throw new EOFException("WebSocket handshake closed");response.write(b);state=(state==0&&b=='\r')?1:(state==1&&b=='\n')?2:(state==2&&b=='\r')?3:(state==3&&b=='\n')?4:0;}
        String text=response.toString("ISO-8859-1");String[] lines=text.split("\r\n");if(lines.length==0||!lines[0].contains(" 101 "))throw new IOException("Paseo WebSocket rejected: "+(lines.length==0?"invalid response":lines[0]));
        String accept="";for(String line:lines)if(line.regionMatches(true,0,"Sec-WebSocket-Accept:",0,21))accept=line.substring(21).trim();
        MessageDigest sha1=MessageDigest.getInstance("SHA-1");String expected=android.util.Base64.encodeToString(sha1.digest((key+MAGIC).getBytes(StandardCharsets.US_ASCII)),android.util.Base64.NO_WRAP);if(!expected.equals(accept))throw new IOException("Invalid WebSocket accept");socket.setSoTimeout(0);
    }
    synchronized void sendText(String text)throws IOException{writeFrame(1,text.getBytes(StandardCharsets.UTF_8));}
    private synchronized void writeFrame(int opcode,byte[] body)throws IOException{if(closed)throw new EOFException("WebSocket closed");out.write(0x80|opcode);int n=body.length;if(n<126)out.write(0x80|n);else if(n<=65535){out.write(0x80|126);out.write(n>>>8);out.write(n);}else{out.write(0x80|127);for(int i=7;i>=0;i--)out.write((int)(((long)n>>>(8*i))&255));}byte[] mask=new byte[4];random.nextBytes(mask);out.write(mask);for(int i=0;i<n;i++)out.write(body[i]^mask[i&3]);out.flush();}
    String readText()throws IOException{ByteArrayOutputStream message=new ByteArrayOutputStream();int initial=-1;while(true){int a=in.read(),b=in.read();if((a|b)<0)throw new EOFException("WebSocket closed");boolean fin=(a&128)!=0;int opcode=a&15;boolean masked=(b&128)!=0;long n=b&127;if(n==126)n=((long)readByte()<<8)|readByte();else if(n==127){n=0;for(int i=0;i<8;i++)n=(n<<8)|readByte();}if(n>4*1024*1024)throw new IOException("WebSocket frame too large");byte[] mask=masked?readFully(4):null;byte[] body=readFully((int)n);if(masked)for(int i=0;i<body.length;i++)body[i]^=mask[i&3];if(opcode==8){closed=true;throw new EOFException("WebSocket close");}if(opcode==9){writeFrame(10,body);continue;}if(opcode==10)continue;if(opcode!=0&&opcode!=1)continue;if(opcode==1)initial=1;message.write(body,0,body.length);if(fin&&initial==1)return message.toString("UTF-8");}}
    private int readByte()throws IOException{int b=in.read();if(b<0)throw new EOFException();return b;}
    private byte[] readFully(int n)throws IOException{byte[] b=new byte[n];int p=0;while(p<n){int r=in.read(b,p,n-p);if(r<0)throw new EOFException();p+=r;}return b;}
    public synchronized void close(){if(closed)return;try{writeFrame(8,new byte[]{3,(byte)232});}catch(Exception ignored){}closed=true;try{socket.close();}catch(Exception ignored){}}
}
