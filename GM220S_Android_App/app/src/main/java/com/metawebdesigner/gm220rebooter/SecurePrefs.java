package com.metawebdesigner.gm220rebooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecurePrefs {
    private static final String ALIAS = "gm220_router_key";
    private final SharedPreferences prefs;

    public SecurePrefs(Context context) {
        prefs = context.getSharedPreferences("gm220_settings", Context.MODE_PRIVATE);
    }

    private SecretKey getKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            kg.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
    }

    public void putSecret(String key, String value) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = c.getIV();
            byte[] enc = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String payload = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(enc, Base64.NO_WRAP);
            prefs.edit().putString(key, payload).apply();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getSecret(String key, String def) {
        String payload = prefs.getString(key, null);
        if (payload == null) return def;
        try {
            String[] p = payload.split(":", 2);
            byte[] iv = Base64.decode(p[0], Base64.NO_WRAP);
            byte[] enc = Base64.decode(p[1], Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return def;
        }
    }

    public SharedPreferences raw() { return prefs; }
}
