package com.climaone.app.core;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only Midea B5/B1 capability discovery and vendor-defined B0 feature
 * commands. Never sends custom inverter/fan RPM or firmware writes.
 * Reference: midea-lan/midea-local AC B5/B1/B0 implementation (MIT).
 */
public final class AdvancedMidea {
    private AdvancedMidea() {}
    public static final int BREEZELESS = 0x0018;
    public static final int OUTDOOR_SILENT = 0x00CD;
    public static final int GEAR = 0x0048;
    private static final int WIND_SPEED_B5 = 0x0210;
    private static final int TURBO_B5 = 0x021A;

    public static class Snapshot {
        public boolean b5Responded;
        public Boolean fanCustom, turboCool, turboHeat;
        public Boolean breezeless, outdoorSilent, gearAvailable;
        public int gearLevels = 0;
        public Integer gearValue, breezeValue, outdoorSilentValue;
        public String note="";
        public long checkedAt;

        public boolean canBreeze() { return breezeless != null; }
        public boolean canOutdoorSilent() { return outdoorSilent != null; }
        public boolean canGear50() {
            return Boolean.TRUE.equals(gearAvailable) &&
                   gearValue != null && (gearLevels & 1) != 0;
        }
    }

    static int u(byte b) { return b & 255; }

    /** New-protocol B5 4-byte TLV and B1 5-byte TLV. Null means not reported. */
    public static Map<Integer, byte[]> decodeTags(byte[] fullFrame, int bodyType) throws IOException {
        if(fullFrame == null || fullFrame.length < 14 ||
           u(fullFrame[0]) != 0xaa || u(fullFrame[2]) != 0xac ||
           u(fullFrame[10]) != bodyType)
            throw new IOException("Risposta B5/B1 non riconosciuta");
        int count = u(fullFrame[11]), pos=12;
        if(count > 64)throw new IOException("Conteggio tag non valido");
        LinkedHashMap<Integer,byte[]> tags=new LinkedHashMap<>();
        for(int i=0;i<count;i++){
            int extra=bodyType==0xb5?0:1;
            if(pos+3+extra>fullFrame.length-1)break;
            int tag=u(fullFrame[pos]) | u(fullFrame[pos+1])<<8;
            pos+=2;
            if(extra>0){ if(u(fullFrame[pos])!=0)break;pos++; }
            int length=u(fullFrame[pos++]);
            if(length<=0 || pos+length>fullFrame.length-1)break;
            tags.put(tag,Arrays.copyOfRange(fullFrame,pos,pos+length));
            pos+=length;
        }
        return tags;
    }

    public static byte[] b1Query(int tag) {
        return MideaLan.frame(3,new byte[]{
          (byte)0xb1,1,(byte)(tag&255),(byte)(tag>>8)
        });
    }
    public static byte[] b5Query(boolean additional) {
        return MideaLan.frame(3,additional?
            new byte[]{(byte)0xb5,1,1,1}:
            new byte[]{(byte)0xb5,1,0});
    }
    public static byte[] b0Set(int tag,int value) {
        if(tag!=BREEZELESS && tag!=OUTDOOR_SILENT && tag!=GEAR)
            throw new IllegalArgumentException("Proprieta vendor non autorizzata");
        if(tag==BREEZELESS && value!=0 && value!=1)
            throw new IllegalArgumentException("Breeze 0/1");
        if(tag==OUTDOOR_SILENT && value!=0 && value!=3)
            throw new IllegalArgumentException("Outdoor silent 0/3");
        if(tag==GEAR && value!=50 && value!=75 && value!=100)
            throw new IllegalArgumentException("GEAR 50/75/100");
        return MideaLan.frame(2,new byte[]{
          (byte)0xb0,1,(byte)(tag&255),(byte)(tag>>8),1,(byte)value
        });
    }
    private static Integer value(Map<Integer,byte[]> map,int tag){
        byte[] d=map.get(tag);
        return d!=null && d.length>0 ? u(d[0]) : null;
    }
    public static Integer readFeature(MideaLan.Device device,int tag) throws Exception {
        byte[] reply=MideaLan.exchange(device,b1Query(tag));
        return value(decodeTags(reply,0xb1),tag);
    }

    /** Missing/failed B5 or B1 are unknown, not automatically unsupported. */
    public static Snapshot inspect(MideaLan.Device device) throws Exception {
        if(device==null || device.version==3 &&
           (device.token==null||device.token.isEmpty()||
            device.key==null||device.key.isEmpty()))
            throw new IOException("Collega prima il modulo V3 con chiavi valide");
        Snapshot result=new Snapshot();
        result.checkedAt=System.currentTimeMillis();
        Map<Integer,byte[]> caps=new LinkedHashMap<>();
        for(boolean add:new boolean[]{false,true}){
            try{
                Map<Integer,byte[]> chunk=decodeTags(MideaLan.exchange(device,b5Query(add)),0xb5);
                if(!chunk.isEmpty()){caps.putAll(chunk);result.b5Responded=true;}
            }catch(Exception ignored){/* optional query; missing is not a capability */ }
        }
        Integer speed=value(caps,WIND_SPEED_B5);
        if(speed!=null)result.fanCustom=speed==1;
        Integer wind=value(caps,TURBO_B5);
        if(wind!=null){
            result.turboCool=wind<2;
            result.turboHeat=wind==1||wind==3;
        }
        Integer rateFlags=value(caps,GEAR);
        if(rateFlags!=null){
            result.gearAvailable=rateFlags>0;
            result.gearLevels=rateFlags;
        }
        try{
            Integer b=readFeature(device,BREEZELESS);
            if(b!=null){result.breezeless=b==1;result.breezeValue=b;}
        }catch(Exception ignored){}
        try{
            Integer b=readFeature(device,OUTDOOR_SILENT);
            if(b!=null){result.outdoorSilent=b==3;result.outdoorSilentValue=b;}
        }catch(Exception ignored){}
        // Rate is queried only when B5 has confirmed support; otherwise
        // sending B1 rate_select can break queries on incompatible devices.
        if(Boolean.TRUE.equals(result.gearAvailable)){
            try{
                Integer b=readFeature(device,GEAR);
                if(b!=null)result.gearValue=b;
            }catch(Exception ignored){}
        }
        return result;
    }

    /**
     * Vendor B0 command with B1 readback. Does not manipulate outdoor fan
     * speed or compressor Hz; target firmware may ignore unsupported commands.
     */
    public static Integer applyVerified(MideaLan.Device device,int tag,int wanted)
            throws Exception {
        MideaLan.exchange(device,b0Set(tag,wanted));
        Integer actual=readFeature(device,tag);
        if(actual==null)
            throw new IOException("La macchina non ha confermato il comando B0/B1.");
        if(!actual.equals(wanted))
            throw new IOException("Richiesto "+wanted+"; firmware riporta "+actual+". Nessuna conferma.");
        return actual;
    }
}
