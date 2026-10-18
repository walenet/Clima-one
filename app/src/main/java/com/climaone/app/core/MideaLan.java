package com.climaone.app.core;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Standalone implementation of the Midea LAN transport; no PC, MQTT or Home Assistant.
 *  Refer to the accompanying protocol notes and upstream MIT implementation.
 */
public final class MideaLan {
    private MideaLan() {}
    private static final byte[] SIGN = "xhdiwjnchekd4d512chdjx5d8e4c394D2D7S".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DISCOVERY = hex("5a5a01114800920000000000000000000000000000000000000000000000000000000000000000007f75bd6b3e4f8b762e849c6e578d6590036e9d4342a50f1f569eb8ec918e92e5");
    public static final class Device {
        public String ip, name;
        public int port = 6444, version, type;
        public long id;
        public String token="", key="";
        public boolean compatible() { return type == 0xAC || type == 0xCC; }
        @Override public String toString() {return name + "  ·  " + ip + "  ·  V" + version + (compatible()?"":"  (non AC)");}
    }
    public static final class State {
        public boolean power, eco, turbo, filterAlert;
        public double setpoint=24, indoor=Double.NaN, outdoor=Double.NaN;
        public int mode=2, fan=102, swing=0;
        public Double watts=null, kwh=null, compressorHz=null, compressorTargetHz=null, fanRpm=null;
        public State copy() { State s=new State(); s.power=power;s.eco=eco;s.turbo=turbo;s.filterAlert=filterAlert;s.setpoint=setpoint;s.indoor=indoor;s.outdoor=outdoor;s.mode=mode;s.fan=fan;s.swing=swing;s.watts=watts;s.kwh=kwh;s.compressorHz=compressorHz;s.compressorTargetHz=compressorTargetHz;s.fanRpm=fanRpm; return s; }
    }
    public static byte[] hex(String v) {
        v=v.replaceAll("\\s","");
        if(v.length()%2!=0) throw new IllegalArgumentException("Hex non valido");
        byte[] b=new byte[v.length()/2];for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(v.substring(i*2,i*2+2),16);return b;
    }
    public static String toHex(byte[] b) {StringBuilder sb=new StringBuilder();for(byte x:b)sb.append(String.format(Locale.US,"%02x",x&255));return sb.toString();}
    private static int u(byte b){return b&255;}
    private static byte[] digest(String algo, byte[] b)throws Exception{return MessageDigest.getInstance(algo).digest(b);}
    private static byte[] cat(byte[]... parts) {ByteArrayOutputStream o=new ByteArrayOutputStream(); for(byte[] b:parts)try{o.write(b);}catch(IOException e){throw new AssertionError(e);}return o.toByteArray();}
    private static byte[] part(byte[] b,int a,int z){return Arrays.copyOfRange(b,a,z);}
    private static byte[] aes(String transform, int mode, byte[] key, byte[] value)throws Exception{
        Cipher c=Cipher.getInstance(transform);SecretKeySpec k=new SecretKeySpec(key,"AES");
        if(transform.contains("CBC"))c.init(mode,k,new IvParameterSpec(new byte[16]));else c.init(mode,k);
        return c.doFinal(value);
    }
    private static byte[] decryptDiscovery(byte[] v)throws Exception {return aes("AES/ECB/PKCS5Padding",Cipher.DECRYPT_MODE,digest("MD5",SIGN),v);}
    private static long littleLong(byte[] b,int start,int n) {long v=0;for(int i=0;i<n;i++)v |= ((long)u(b[start+i]))<<(8*i);return v;}
    public static Device decodeDiscovery(String senderIp,byte[] packet)throws Exception {
        if(packet.length<2) return null;
        Device d=new Device();d.ip=senderIp;
        byte[] raw;
        if(u(packet[0])==0x83 && u(packet[1])==0x70){d.version=3;if(packet.length<85)return null; raw=part(packet,8,packet.length-16);}
        else if(u(packet[0])==0x5a && u(packet[1])==0x5a){d.version=2;raw=packet;}
        else return null;
        if(raw.length<57 || u(raw[0])!=0x5a || u(raw[1])!=0x5a)return null;
        d.id=littleLong(raw,20,6);
        byte[] dec=decryptDiscovery(part(raw,40,raw.length-16));
        if(dec.length<42)return null;
        d.port=(int)littleLong(dec,4,2);if(d.port<=0||d.port>65535)d.port=6444;
        int nameLength=u(dec[40]);if(nameLength<=0||nameLength>dec.length-41)return null;
        d.name=new String(dec,41,nameLength,StandardCharsets.US_ASCII);
        String[] fields=d.name.split("_");if(fields.length<2)return null;
        try{d.type=Integer.parseInt(fields[1],16);}catch(NumberFormatException e){return null;}
        return d;
    }
    /** Broadcast discovery on the phone's connected Wi-Fi. The caller needs multicast lock. */
    public static List<Device> discover() throws IOException {return discoverTarget("255.255.255.255");}
    public static List<Device> discoverTarget(String target) throws IOException {
        if(!target.equals("255.255.255.255") && !target.matches("^(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2[0-9]|3[01])\\.\\d{1,3}\\.\\d{1,3})$"))throw new IllegalArgumentException("Inserisci un IP locale privato, es. 192.168.1.50");
        LinkedHashMap<String,Device> devices=new LinkedHashMap<>();
        try(DatagramSocket sock=new DatagramSocket()){
            sock.setBroadcast(true);sock.setSoTimeout(750);
            InetAddress bcast=InetAddress.getByName(target);
            for(int port:new int[]{6445,20086})for(int j=0;j<2;j++)sock.send(new DatagramPacket(DISCOVERY,DISCOVERY.length,bcast,port));
            long till=System.currentTimeMillis()+4500;
            while(System.currentTimeMillis()<till){
                try {byte[] b=new byte[2048];DatagramPacket p=new DatagramPacket(b,b.length);sock.receive(p);
                    Device d=decodeDiscovery(p.getAddress().getHostAddress(),Arrays.copyOf(p.getData(),p.getLength()));
                    if(d!=null && d.compatible()) devices.put(d.ip,d);
                }catch(SocketTimeoutException ignored){}catch(Exception ignored){} // Other LAN devices are expected
            }
        }
        return new ArrayList<>(devices.values());
    }
    private static byte[] timeStamp(){LocalDateTime t=LocalDateTime.now(ZoneOffset.UTC);return new byte[]{(byte)(t.getNano()/10000000),(byte)t.getSecond(),(byte)t.getMinute(),(byte)t.getHour(),(byte)t.getDayOfMonth(),(byte)t.getMonthValue(),(byte)(t.getYear()%100),(byte)(t.getYear()/100)};}
    private static byte[] le(long n,int size){byte[] b=new byte[size];for(int i=0;i<size;i++)b[i]=(byte)(n>>(8*i));return b;}
    public static byte[] v2Packet(long deviceId, byte[] frame)throws Exception {
        byte[] enc=aes("AES/ECB/PKCS5Padding",Cipher.ENCRYPT_MODE,digest("MD5",SIGN),frame);
        int length=40+enc.length+16;
        byte[] header=cat(hex("5a5a0111"),le(length,2),hex("2000"),new byte[4],timeStamp(),le(deviceId,8),new byte[12]);
        byte[] content=cat(header,enc);
        return cat(content,digest("MD5",cat(content,SIGN)));
    }
    public static byte[] decodeV2(byte[] raw)throws Exception {
        if(raw.length<56 || u(raw[0])!=0x5a || u(raw[1])!=0x5a)throw new IOException("Risposta Midea V2 non riconosciuta");
        int len=(int)littleLong(raw,4,2);
        if(len<56||len>raw.length)throw new IOException("Risposta V2 incompleta");
        byte[] signed=part(raw,0,len-16), actual=part(raw,len-16,len);
        if(!MessageDigest.isEqual(actual,digest("MD5",cat(signed,SIGN))))throw new SecurityException("Firma pacchetto non valida");
        byte[] decoded=aes("AES/ECB/PKCS5Padding",Cipher.DECRYPT_MODE,digest("MD5",SIGN),part(raw,40,len-16));
        if(decoded.length<13||u(decoded[0])!=0xaa||u(decoded[2])!=0xac)throw new IOException("Frame climatizzatore non valido");
        return decoded;
    }
    private static int crc8(byte[] b){ // polynomial 0x31, init 0; Midea reflected implementation (0x8C)
        int crc=0; for(byte x:b){int val=u(x);for(int j=0;j<8;j++){int mix=(crc^val)&1;crc>>=1;if(mix!=0)crc^=0x8c;val>>=1;}}return crc;
    }
    private static int msgId=0;
    public static byte[] frame(int frameType,byte[] payload){
        byte[] body=cat(payload,new byte[]{(byte)((++msgId)&255)});
        byte[] withCrc=cat(body,new byte[]{(byte)crc8(body)});
        byte[] head=new byte[10];head[0]=(byte)0xaa;head[1]=(byte)(10+withCrc.length);head[2]=(byte)0xac;head[9]=(byte)frameType;
        byte[] data=cat(head,withCrc);int sum=0;for(int i=1;i<data.length;i++)sum+=u(data[i]);return cat(data,new byte[]{(byte)(-sum)});
    }
    public static byte[] queryState(){return frame(3,hex("418100ff03ff000200000000000000000000000003"));}
    public static byte[] queryGroup(int group){if(group<1||group>11)throw new IllegalArgumentException();byte[] p=new byte[20];p[0]=0x41;p[1]=0x21;p[2]=1;p[3]=(byte)(0x40|group);return frame(3,p);}
    public static byte[] queryCaps(){return frame(3,hex("b50100"));}
    /** Conservative standard control; never sends raw RPM, compressor Hz, or firmware writes. */
    public static byte[] setState(State s){
        if(s.setpoint<17||s.setpoint>30||Math.abs(s.setpoint*2-Math.rint(s.setpoint*2))>0.001)throw new IllegalArgumentException("Temperatura 17-30 °C, passi 0,5");
        if(s.mode<1||s.mode>5)throw new IllegalArgumentException("Modalità non valida");
        if(!(s.fan==102||(s.fan>=1&&s.fan<=100)))throw new IllegalArgumentException("Velocità non valida");
        int ip=(int)s.setpoint;
        int t=(ip-16)|((s.setpoint-ip>=0.5)?0x10:0)|((s.mode&7)<<5);
        byte[] p=new byte[25];p[0]=0x40;p[1]=(byte)(2|(s.power?1:0));p[2]=(byte)t;p[3]=(byte)s.fan;
        p[4]=0x7f;p[5]=0x7f;p[7]=(byte)(0x30|(s.swing&0x0f));p[8]=(byte)(s.turbo?0x20:0);
        p[9]=(byte)(s.eco?0x80:0);p[10]=(byte)(s.turbo?2:0);p[19]=40;
        return frame(2,p);
    }
    public static State stateFromFrame(byte[] frame){
        if(frame==null||frame.length<34||u(frame[0])!=0xaa||u(frame[2])!=0xac||u(frame[10])!=0xc0)return null;
        int o=10;State s=new State();s.power=(u(frame[o+1])&1)!=0;
        s.setpoint=16+(u(frame[o+2])&15)+((u(frame[o+2])&0x10)!=0?0.5:0);
        s.mode=(u(frame[o+2])>>5)&7;s.fan=u(frame[o+3])&0x7f;s.swing=u(frame[o+7])&15;
        s.turbo=(u(frame[o+8])&0x20)!=0||(u(frame[o+10])&2)!=0;s.eco=(u(frame[o+9])&0x10)!=0;
        s.indoor=temperature(u(frame[o+11]));s.outdoor=temperature(u(frame[o+12]));s.filterAlert=(u(frame[o+13])&0x20)!=0;
        if((u(frame[o+13])&31)!=0)s.setpoint=12+(u(frame[o+13])&31)+((u(frame[o+2])&0x10)!=0?0.5:0);
        return s;
    }
    private static double temperature(int raw){return raw==255?Double.NaN:(raw-50)/2.0;}
    public static void parseExtra(State s,byte[] frame){
        if(frame==null||frame.length<31||u(frame[10])!=0xc1)return;
        int o=10, group=u(frame[13])&15;
        if(group==1&&frame.length>o+15){s.compressorHz=(double)u(frame[o+4]);s.compressorTargetHz=(double)u(frame[o+5]);}
        if(group==2&&frame.length>o+8)s.fanRpm=(double)(u(frame[o+5])*8);
        if(group==7&&frame.length>o+11)s.watts=(double)(u(frame[o+10])+256*u(frame[o+11]));
        if(group==4&&frame.length>o+18){
            boolean good=true;for(int j=12;j<19;j++){int z=u(frame[o+j]);if((z>>4)>9 || (z&15)>9)good=false;}
            if(good){
                double consumption=10000*bcd(u(frame[o+12]))+100*bcd(u(frame[o+13]))+bcd(u(frame[o+14]))+.01*bcd(u(frame[o+15]));
                if(consumption>0)s.kwh=consumption;
                if(s.watts==null){int a=u(frame[o+16]),b=u(frame[o+17]),c=u(frame[o+18]);double power=1000*bcd(a)+10*bcd(b)+.1*bcd(c);if(power>0)s.watts=power;}
            }
        }
    }
    private static int bcd(int value){return (value>>4)*10+(value&15);}
    /** Receives at most 4096 bytes per response. */
    private static byte[] recv(InputStream in,boolean v3)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] ch=new byte[1024];int expected=-1;
        for(int rounds=0;rounds<16;rounds++){
            int r=in.read(ch);if(r<0)break;b.write(ch,0,r);byte[] data=b.toByteArray();
            if(data.length>=6){if(v3){if(u(data[0])!=0x83||u(data[1])!=0x70)throw new IOException("Risposta V3 non valida");expected=(u(data[2])<<8)+u(data[3])+8;}
               else {if(u(data[0])!=0x5a||u(data[1])!=0x5a)throw new IOException("Risposta V2 non valida");expected=u(data[4])+(u(data[5])<<8);}}
            if(expected>4096||data.length>4096)throw new IOException("Pacchetto troppo grande");
            if(expected>=0&&data.length>=expected)return part(data,0,expected);
        }
        throw new IOException("Risposta incompleta");
    }
    private static byte[] xor(byte[] a,byte[] b){if(a.length!=b.length)throw new IllegalArgumentException();byte[] c=new byte[a.length];for(int i=0;i<a.length;i++)c[i]=(byte)(a[i]^b[i]);return c;}
    private static byte[] encodeV3(byte[] v2,byte[] localKey)throws Exception{
        int pad=(16-(v2.length+2)%16)%16;int length=v2.length+pad+32;
        byte[] header=new byte[]{(byte)0x83,0x70,(byte)(length>>8),(byte)length,0x20,(byte)((pad<<4)|6)};
        byte[] padding=new byte[pad];new SecureRandom().nextBytes(padding);
        byte[] body=cat(new byte[2],v2,padding);
        return cat(header,aes("AES/CBC/NoPadding",Cipher.ENCRYPT_MODE,localKey,body),digest("SHA-256",cat(header,body)));
    }
    private static byte[] decodeV3(byte[] p,byte[] localKey)throws Exception{
        if(p.length<40||(u(p[5])&15)!=3)throw new IOException("Risposta cifrata V3 assente");
        byte[] plain=aes("AES/CBC/NoPadding",Cipher.DECRYPT_MODE,localKey,part(p,6,p.length-32));
        if(!MessageDigest.isEqual(part(p,p.length-32,p.length),digest("SHA-256",cat(part(p,0,6),plain))))throw new SecurityException("Firma SHA256 V3 non valida");
        int pad=u(p[5])>>4;if(plain.length<2+pad)throw new IOException("Padding non valido");
        return part(plain,2,plain.length-pad);
    }
    private static byte[] handshake(InputStream in,OutputStream out,String token,String key)throws Exception {
        byte[] t=hex(token),k=hex(key);if(t.length!=64||k.length!=32)throw new IllegalArgumentException("Token (128 cifre hex) o key (64 cifre hex) non validi");
        byte[] req=cat(new byte[]{(byte)0x83,0x70,(byte)(t.length>>8),(byte)t.length,0x20,0},new byte[2],t);
        out.write(req);out.flush();byte[] reply=recv(in,true);
        if((u(reply[5])&15)!=1)throw new IOException("Handshake V3 rifiutato");
        byte[] resp=part(reply,8,reply.length);if(resp.length!=64)throw new IOException("Lunghezza handshake V3 errata");
        byte[] clear=aes("AES/CBC/NoPadding",Cipher.DECRYPT_MODE,k,part(resp,0,32));
        if(!MessageDigest.isEqual(digest("SHA-256",clear),part(resp,32,64)))throw new SecurityException("Handshake V3: verifica crittografica fallita");
        return xor(clear,k);
    }
    /** Actual TCP read/write using the appliance's LAN version. Never writes firmware. */
    public static byte[] exchange(Device dev,byte[] frame)throws Exception {
        try(Socket sock=new Socket()){
            sock.connect(new InetSocketAddress(dev.ip,dev.port),4500);sock.setSoTimeout(4500);
            InputStream in=sock.getInputStream();OutputStream out=sock.getOutputStream();
            byte[] inner=v2Packet(dev.id,frame);
            if(dev.version==3){
                byte[] localKey=handshake(in,out,dev.token,dev.key);
                out.write(encodeV3(inner,localKey));out.flush();return decodeV2(decodeV3(recv(in,true),localKey));
            }
            out.write(inner);out.flush();return decodeV2(recv(in,false));
        }
    }
    /** Query before write, then read back the result: no optimistic fake success. */
    public static State readState(Device d)throws Exception{
        State s=stateFromFrame(exchange(d,queryState()));
        if(s==null)throw new IOException("Il climatizzatore non ha restituito lo stato C0");
        for(int group:new int[]{1,2,7,4}){
            try{parseExtra(s,exchange(d,queryGroup(group)));}catch(Exception ignored){}
        }
        return s;
    }
    public static State apply(Device d,State requested)throws Exception{
        exchange(d,setState(requested));return readState(d);
    }
    public static String udpid(long id,boolean little)throws Exception{
        byte[] b=new byte[6];for(int i=0;i<6;i++)b[i]=(byte)(id>>8*(little?i:(5-i)));
        byte[] hash=digest("SHA-256",b);return toHex(xor(part(hash,0,16),part(hash,16,32)));
    }
}
