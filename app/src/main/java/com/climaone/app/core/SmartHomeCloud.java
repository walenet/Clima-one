package com.climaone.app.core;

import org.json.*;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Experimental one-time Midea SmartHome cloud bootstrap. Local V3 LAN commands
 * continue without this class once token/key are obtained and validated.
 *
 * Formats aligned with midea-lan/midea-local (October 2026) including regional
 * account routing, required auth headers, full login payload and applianceCodes.
 * These are private undocumented APIs; success is NOT guaranteed.
 */
public final class SmartHomeCloud {
    private static final String DEFAULT_BASE = "https://mp-prod.appsmb.com/mas/v5/app/proxy?alias=";
    private static final String APP_ID = "1010";
    private static final String APP_KEY = "ac21b9f9cbfe4ca5a88562ef25e2b768";
    private static final String IOT_KEY = "meicloud";
    private static final String HMAC_KEY = "PROD_VnoClJI9aikS8dyy";
    private String base = DEFAULT_BASE;
    private String deviceId = "";
    private String accessToken = "";
    private String uid = null;
    private String stage = "init";

    public static final class CloudStepException extends IOException {
        public final String stage;
        public final int code;
        CloudStepException(String stage, int code, String detail) {
            super("Fase " + stage + ": " + (code >= 0 ? "Midea codice " + code + ". " : "") + detail);
            this.stage = stage;
            this.code = code;
        }
    }

    private static byte[] digest(String algorithm, byte[] data) throws Exception {
        return MessageDigest.getInstance(algorithm).digest(data);
    }
    private static String hash(String algorithm, String text) throws Exception {
        return MideaLan.toHex(digest(algorithm, text.getBytes(StandardCharsets.UTF_8)));
    }
    private static String hmac(String text) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_KEY.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
        return MideaLan.toHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }
    private static String stamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
    private static String randomHex() {
        byte[] value = new byte[16];
        new SecureRandom().nextBytes(value);
        return MideaLan.toHex(value);
    }
    private JSONObject general() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("src", APP_ID);
        data.put("format", "2");
        data.put("stamp", stamp());
        data.put("platformId", "1");
        data.put("deviceId", deviceId);
        data.put("reqId", randomHex());
        if (uid != null) data.put("uid", uid);
        data.put("clientType", "1");
        data.put("appId", APP_ID);
        data.put("language", "en_US");
        return data;
    }

    private static String validatedBase(String url) throws Exception {
        if (url == null || url.length() > 400) throw new IOException("Indirizzo MAS non valido");
        URI uri = new URI(url);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null)
            throw new IOException("Il server regionale non usa HTTPS");
        host = host.toLowerCase(Locale.US);
        if (!(host.equals("appsmb.com") || host.endsWith(".appsmb.com")
           || host.equals("smartmidea.net") || host.endsWith(".smartmidea.net")))
            throw new IOException("Server regionale Midea non riconosciuto; autenticazione interrotta");
        if (uri.getRawUserInfo() != null || uri.getPort() != -1)
            throw new IOException("Server regionale non consentito");
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (!path.contains("/mas/") || !url.contains("alias="))
            throw new IOException("Indirizzo regionale MAS non riconosciuto");
        return url;
    }

    private JSONObject request(String endpoint, JSONObject body) throws Exception {
        final String phase = endpoint.contains("route") || endpoint.contains("/router/")
                ? "routing regionale"
                : endpoint.endsWith("/id/get") ? "identificazione account"
                : endpoint.endsWith("/mj/user/login") ? "login account"
                : endpoint.contains("getToken") ? "recupero token V3" : "richiesta Midea";
        stage = phase;
        String contents = body.toString();
        String random = String.valueOf(System.currentTimeMillis() / 1000L);
        String signature = hmac(IOT_KEY + contents + random);
        String authorization = "Basic " + Base64.encodeToString(
                (APP_KEY + ":" + IOT_KEY).getBytes(StandardCharsets.US_ASCII), Base64.NO_WRAP);
        URL target = new URL(base + endpoint);
        HttpURLConnection c = (HttpURLConnection)target.openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("secretVersion", "1");
            c.setRequestProperty("sign", signature);
            c.setRequestProperty("random", random);
            c.setRequestProperty("x-recipe-app", APP_ID);
            c.setRequestProperty("Authorization", authorization);
            if (uid != null) c.setRequestProperty("uid", uid);
            if (!accessToken.isEmpty()) c.setRequestProperty("accessToken", accessToken);
            byte[] payload = contents.getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) {
                out.write(payload);
            }
            int http = c.getResponseCode();
            if (http != 200) throw new CloudStepException(phase, http,
                    "Il server ha risposto con HTTP " + http + ".");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] block = new byte[4096];
                int n;
                while ((n = in.read(block)) >= 0) {
                    bytes.write(block, 0, n);
                    if (bytes.size() > 200000)
                        throw new CloudStepException(phase, -1, "Risposta troppo grande.");
                }
                JSONObject response = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                int code = response.optInt("code", -1);
                if (code != 0) {
                    String explanation = code == 3004
                            ? "Richiesta rifiutata per parametri o compatibilita API; NON significa automaticamente password errata."
                            : code == 3102
                            ? "Account non trovato sul server regionale selezionato."
                            : "Il servizio Midea ha rifiutato la richiesta.";
                    throw new CloudStepException(phase, code, explanation);
                }
                JSONObject data = response.optJSONObject("data");
                if (data == null)
                    throw new CloudStepException(phase, -1, "Il servizio non ha restituito i dati attesi.");
                return data;
            }
        } catch (CloudStepException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new CloudStepException(phase, -1,
                "Problema di comunicazione HTTPS: " + ex.getClass().getSimpleName());
        } finally {
            c.disconnect();
        }
    }

    private static String encryptAccount(String account) throws Exception {
        String keyDigest = hash("SHA-256", APP_KEY);
        byte[] aesKey = keyDigest.substring(0,16).getBytes(StandardCharsets.US_ASCII);
        byte[] aesIv = keyDigest.substring(16,32).getBytes(StandardCharsets.US_ASCII);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(aesIv));
        return MideaLan.toHex(cipher.doFinal(account.getBytes(StandardCharsets.UTF_8)));
    }

    private void route(String account) throws Exception {
        String regional = null;
        try {
            JSONObject data = general();
            data.put("clientType", 8);
            data.put("clientSrc", 8);
            data.put("userType", "0");
            data.put("userName", encryptAccount(account));
            regional = request("/v1/unitcenter/router/user/name", data).optString("masUrl", "");
        } catch (CloudStepException ex) {
            // Route is optional; do not confuse an unsupported router with invalid credentials.
        }
        if (regional == null || regional.isEmpty()) {
            try {
                JSONObject data = general();
                data.put("userType", "0");
                data.put("userName", account);
                regional = request("/v1/multicloud/platform/user/route", data).optString("masUrl", "");
            } catch (CloudStepException ignored) {
                // Default endpoint remains available for accounts not mapped by routers.
            }
        }
        if (regional != null && !regional.isEmpty())
            base = validatedBase(regional);
    }

    public void login(String account, String password) throws Exception {
        if (account == null || account.trim().isEmpty() ||
            password == null || password.isEmpty())
            throw new IllegalArgumentException("Inserisci email e password del tuo account Midea.");
        account = account.trim();
        // Mirrors the official community client's per-account device ID.
        deviceId = hash("SHA-256", "Hello, " + account + "!").substring(0,16);
        route(account);
        JSONObject idRequest = general();
        idRequest.put("loginAccount", account);
        String loginId = request("/v1/user/login/id/get", idRequest).getString("loginId");
        String encryptedPassword = hash("SHA-256", loginId + hash("SHA-256", password) + APP_KEY);
        String md5Twice = hash("MD5", hash("MD5", password));
        String iamPassword = hash("SHA-256", loginId + md5Twice + APP_KEY);

        JSONObject iotData = general();
        iotData.remove("uid");
        iotData.put("iampwd", iamPassword);
        iotData.put("loginAccount", account);
        iotData.put("password", encryptedPassword);
        String loginStamp = stamp();
        iotData.put("stamp", loginStamp);

        JSONObject info = new JSONObject();
        info.put("appKey", APP_KEY);
        info.put("deviceId", deviceId);
        info.put("platform", "2");

        JSONObject request = new JSONObject();
        request.put("iotData", iotData);
        request.put("data", info);
        request.put("stamp", loginStamp);

        JSONObject response = request("/mj/user/login", request);
        JSONObject mdata = response.optJSONObject("mdata");
        if (mdata == null || mdata.optString("accessToken", "").isEmpty())
            throw new CloudStepException("login account", -1, "Login senza accessToken.");
        accessToken = mdata.getString("accessToken");
        uid = response.optString("uid", null);
    }

    public String[] tokenKey(long applianceId) throws Exception {
        if (accessToken.isEmpty()) throw new CloudStepException("recupero token V3", -1, "Prima accedi all'account.");
        String error = null;
        for (boolean little : new boolean[]{false, true}) {
            String udpId = MideaLan.udpid(applianceId, little);
            JSONObject payload = general();
            payload.put("udpid", udpId);
            payload.put("applianceCodes", Long.toString(applianceId));
            try {
                JSONObject response = request("/v1/iot/secure/getToken", payload);
                JSONArray list = response.optJSONArray("tokenlist");
                if (list == null) continue;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    if (udpId.equalsIgnoreCase(item.optString("udpId", ""))) {
                        String token = item.optString("token", "");
                        String key = item.optString("key", "");
                        if (MideaLan.hex(token).length == 64 && MideaLan.hex(key).length == 32)
                            return new String[]{token, key};
                    }
                }
            } catch (CloudStepException ex) {
                error = ex.getMessage();
                if (ex.code == 3004) break; // schema/server refusal; don't spam the endpoint
            }
        }
        throw new CloudStepException("recupero token V3", -1,
            "Nessuna chiave valida ricevuta. " +
            (error != null ? error : "Account senza token compatibile o dispositivo non associato."));
    }
}
