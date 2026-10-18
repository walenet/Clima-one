package com.climaone.app.core;

import org.json.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** User-authenticated Midea SmartHome bootstrap for devices linked to that account.
 *  Login credentials are never written to disk; only per-device token/key should persist.
 *  Midea may change or disable these undocumented endpoints.
 */
public final class SmartHomeCloud {
    private static final String BASE="https://mp-prod.appsmb.com/mas/v5/app/proxy?alias=";
    private static final String HMAC_KEY="PROD_VnoClJI9aikS8dyy";
    private static final String LOGIN_KEY="ac21b9f9cbfe4ca5a88562ef25e2b768";
    private final String deviceId=UUID.randomUUID().toString().replace("-","").substring(0,16);
    private String accessToken="";
    private static String hash(String algorithm,String value)throws Exception{
        byte[] b=MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8));return MideaLan.toHex(b);
    }
    private static String hmac(String value)throws Exception{
        Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(HMAC_KEY.getBytes(StandardCharsets.US_ASCII),"HmacSHA256"));return MideaLan.toHex(m.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
    private JSONObject body()throws JSONException{
        JSONObject j=new JSONObject();j.put("appId","1010");j.put("src","1010");j.put("format",2);j.put("clientType",1);j.put("language","en_US");j.put("deviceId",deviceId);
        j.put("stamp",new SimpleDateFormat("yyyyMMddHHmmss",Locale.US).format(new Date()));j.put("reqId",UUID.randomUUID().toString().replace("-",""));return j;
    }
    private JSONObject request(String endpoint,JSONObject body)throws Exception{
        String contents=body.toString();String random=UUID.randomUUID().toString().replace("-","");
        String signature=hmac("meicloud"+contents+random);
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+endpoint).openConnection();
        try{
            c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("secretVersion","1");c.setRequestProperty("sign",signature);c.setRequestProperty("random",random);c.setRequestProperty("accessToken",accessToken);
            byte[] data=contents.getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=c.getOutputStream()){out.write(data);}
            if(c.getResponseCode()!=200)throw new IOException("SmartHome HTTP "+c.getResponseCode());
            ByteArrayOutputStream output=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){
                byte[] buff=new byte[2048];int n;while((n=in.read(buff))!=-1){output.write(buff,0,n);if(output.size()>200000)throw new IOException("Risposta troppo grande");}
            }
            JSONObject response=new JSONObject(output.toString("UTF-8"));int code=response.optInt("code",-1);
            if(code!=0)throw new IOException("SmartHome codice "+code+": "+response.optString("msg","Accesso non riuscito"));
            return response.getJSONObject("data");
        } finally {c.disconnect();}
    }
    public void login(String account,String password)throws Exception{
        if(account==null||account.isEmpty()||password==null||password.isEmpty())throw new IllegalArgumentException("Inserisci le credenziali SmartHome");
        JSONObject idBody=body();idBody.put("loginAccount",account);
        String loginId=request("/v1/user/login/id/get",idBody).getString("loginId");
        String pw=hash("SHA-256",loginId+hash("SHA-256",password)+LOGIN_KEY);
        String md5=hash("MD5",hash("MD5",password));
        String iampwd=hash("SHA-256",loginId+md5+LOGIN_KEY);
        JSONObject outer=new JSONObject();
        JSONObject data=new JSONObject();data.put("platform",2);data.put("deviceId",deviceId);outer.put("data",data);
        JSONObject iot=new JSONObject();iot.put("appId","1010");iot.put("src","1010");iot.put("clientType",1);
        iot.put("loginAccount",account);iot.put("iampwd",iampwd);iot.put("password",pw);
        iot.put("pushToken",UUID.randomUUID().toString().replace("-","").repeat(4));
        iot.put("stamp",new SimpleDateFormat("yyyyMMddHHmmss",Locale.US).format(new Date()));iot.put("reqId",UUID.randomUUID().toString().replace("-",""));outer.put("iotData",iot);
        accessToken=request("/mj/user/login",outer).getJSONObject("mdata").getString("accessToken");
    }
    public String[] tokenKey(long deviceId)throws Exception{
        // Try both endian layouts: OEM Wi-Fi chips can encode device identity differently.
        Exception last=null;
        for(boolean little:new boolean[]{true,false}){
            try{
                String udpid=MideaLan.udpid(deviceId,little);
                JSONObject b=body();b.put("udpid",udpid);b.put("applianceCodes",Long.toString(deviceId));
                JSONObject response=request("/v1/iot/secure/getToken",b);
                JSONArray tokens=response.getJSONArray("tokenlist");
                for(int i=0;i<tokens.length();i++){
                    JSONObject item=tokens.getJSONObject(i);
                    if(udpid.equalsIgnoreCase(item.optString("udpId")))return new String[]{item.getString("token"),item.getString("key")};
                }
            }catch(Exception ex){last=ex;}
        }
        throw new IOException("Token V3 non disponibile per questo account/dispositivo. Controlla che sia associato a SmartHome. "+(last==null?"":last.getMessage()));
    }
}
