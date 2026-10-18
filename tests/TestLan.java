import com.climaone.app.core.MideaLan;
import java.net.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class TestLan {
 static void check(boolean b,String n){if(!b)throw new AssertionError(n);System.out.println("PASS "+n);}
 static byte[] cat(byte[]...parts)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();for(byte[] b:parts)o.write(b);return o.toByteArray();}
 static byte[] aes(boolean enc,byte[] key,byte[] data)throws Exception{Cipher c=Cipher.getInstance("AES/CBC/NoPadding");c.init(enc?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(new byte[16]));return c.doFinal(data);}
 static byte[] sha(byte[] d)throws Exception{return MessageDigest.getInstance("SHA-256").digest(d);}
 static byte[] v3Reply(byte[] packet,byte[] local)throws Exception{
  int pad=(16-(packet.length+2)%16)%16;byte[] payload=cat(new byte[2],packet,new byte[pad]);int length=packet.length+pad+32;
  byte[] header={(byte)0x83,0x70,(byte)(length>>8),(byte)length,0x20,(byte)((pad<<4)|3)};
  return cat(header,aes(true,local,payload),sha(cat(header,payload)));
 }
 static byte[] recv(InputStream in)throws Exception{byte[] h=in.readNBytes(6);if(h.length<6)throw new IOException("eof");boolean v3=(h[0]&255)==0x83;int len=v3?((h[2]&255)<<8)+(h[3]&255)+2:((h[4]&255)|((h[5]&255)<<8))-6;return cat(h,in.readNBytes(len));}
 public static void main(String[]args)throws Exception{
  byte[] frame=MideaLan.queryState();check(frame.length>20&&frame[0]==(byte)0xaa,"frame query real");
  byte[] packet=MideaLan.v2Packet(3452435212L,frame);byte[] unpack=MideaLan.decodeV2(packet);check(Arrays.equals(frame,unpack),"V2 encrypt decrypt, signatures");
  MideaLan.State input=new MideaLan.State();input.power=true;input.mode=2;input.fan=60;input.setpoint=24.5;input.eco=true;
  byte[] ctrl=MideaLan.setState(input);check(ctrl[9]==2&&ctrl[10]==0x40,"V2 real set-state frame");
  byte[] respBytes=new byte[25];respBytes[0]=(byte)0xc0;respBytes[1]=1;respBytes[2]=(byte)((24-16)|0x10|(2<<5));respBytes[3]=60;respBytes[11]=96;respBytes[12]=88;
  byte[] response=MideaLan.frame(4,respBytes);MideaLan.State state=MideaLan.stateFromFrame(response);
  check(state!=null && state.power&&state.setpoint==24.5&&state.fan==60&&state.indoor==23,"state decoder");
  // Validate device discovery, including wrapped V3 OEM device advertisements.
  byte[] key=MessageDigest.getInstance("MD5").digest("xhdiwjnchekd4d512chdjx5d8e4c394D2D7S".getBytes(StandardCharsets.US_ASCII));
  byte[] info=new byte[80];info[4]=(byte)0x2c;info[5]=(byte)0x19; // port 6444
  byte[] ssid="net_ac_TestUnit".getBytes(StandardCharsets.US_ASCII);info[40]=(byte)ssid.length;System.arraycopy(ssid,0,info,41,ssid.length);
  Cipher ecb=Cipher.getInstance("AES/ECB/PKCS5Padding");ecb.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"));byte[] encrypted=ecb.doFinal(info);
  byte[] ad=new byte[40];ad[0]=0x5a;ad[1]=0x5a;long testId=123456789012L;for(int i=0;i<6;i++)ad[20+i]=(byte)(testId>>(8*i));
  byte[] discovery=cat(ad,encrypted,new byte[16]);
  MideaLan.Device v2=MideaLan.decodeDiscovery("192.168.1.70",discovery);
  check(v2!=null&&v2.version==2&&v2.id==testId&&v2.port==6444&&v2.compatible(),"V2 discovery and OEM-type fingerprint");
  MideaLan.Device v3=MideaLan.decodeDiscovery("192.168.1.70",cat(new byte[]{(byte)0x83,0x70,0,0,0,0,0,0},discovery,new byte[16]));
  check(v3!=null&&v3.version==3&&v3.compatible(),"V3 discovery wrapper");
  // Real TCP exchange against an in-process appliance emulator (LAN transport exercised, not physical device).
  try(ServerSocket srv=new ServerSocket(0)){
   Thread t=new Thread(()->{try(Socket s=srv.accept()){byte[] request=recv(s.getInputStream());if(request.length<50)throw new IOException("short request");s.getOutputStream().write(MideaLan.v2Packet(3452435212L,response));}catch(Exception e){throw new RuntimeException(e);}});t.start();
   MideaLan.Device d=new MideaLan.Device();d.ip="127.0.0.1";d.port=srv.getLocalPort();d.id=3452435212L;d.version=2;
   check(Arrays.equals(response,MideaLan.exchange(d,frame)),"V2 TCP read and write");t.join();
  }
  // V3 auth and response cryptographic integrity against test-only emulator.
  byte[] cloudKey=new byte[32],token=new byte[64],challenge=new byte[32];new SecureRandom().nextBytes(cloudKey);new SecureRandom().nextBytes(token);new SecureRandom().nextBytes(challenge);
  byte[] local=new byte[32];for(int i=0;i<32;i++)local[i]=(byte)(cloudKey[i]^challenge[i]);
  try(ServerSocket srv=new ServerSocket(0)){
   Thread t=new Thread(()->{try(Socket s=srv.accept()){
       InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();byte[] hello=recv(in);if(hello[5]!=0)throw new IOException("handshake type");
       byte[] challengeReply=cat(aes(true,cloudKey,challenge),sha(challenge));
       out.write(cat(new byte[]{(byte)0x83,0x70,0,64,0x20,1},new byte[2],challengeReply));out.flush();
       byte[] request=recv(in);if((request[5]&15)!=6)throw new IOException("encrypted req type");
       out.write(v3Reply(MideaLan.v2Packet(3452435212L,response),local));out.flush();
      }catch(Exception e){throw new RuntimeException(e);}});t.start();
   MideaLan.Device d=new MideaLan.Device();d.ip="127.0.0.1";d.port=srv.getLocalPort();d.id=3452435212L;d.version=3;d.token=MideaLan.toHex(token);d.key=MideaLan.toHex(cloudKey);
   check(Arrays.equals(response,MideaLan.exchange(d,frame)),"V3 token/key handshake and authenticated TCP commands");t.join();
  }
  System.out.println("All local JVM tests passed.");
 }
}
