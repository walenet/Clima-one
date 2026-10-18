package com.climaone.app.core;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

/** Encrypts saved V3 tokens with Android Keystore, using per-install hardware-backed key when supported. */
public final class PrivateKeys {
    private static final String ALIAS="clima_one_tokens_v1";
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());g.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS,null);
    }
    public static void save(Context ctx,String identity,String token,String secret)throws Exception{
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        byte[] enc=c.doFinal((token+":"+secret).getBytes(StandardCharsets.UTF_8));
        String value=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+"."+Base64.encodeToString(enc,Base64.NO_WRAP);
        ctx.getSharedPreferences("device_secrets",Context.MODE_PRIVATE).edit().putString(identity,value).apply();
    }
    public static String[] load(Context ctx,String identity)throws Exception{
        SharedPreferences prefs=ctx.getSharedPreferences("device_secrets",Context.MODE_PRIVATE);
        String value=prefs.getString(identity,"");if(value.isEmpty())return null;
        String[] parts=value.split("\\.");if(parts.length!=2)return null;
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.DEFAULT)));
        String decoded=new String(c.doFinal(Base64.decode(parts[1],Base64.DEFAULT)),StandardCharsets.UTF_8);
        return decoded.split(":",2);
    }
}
