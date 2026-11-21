package com.reimagined.winner;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Encrypted, app-private environment configuration for the native Android client. */
public final class EnvStore {
    private static final String FILE = "config.env.enc";
    private static final String OLD_FILE = ".env";
    private static final String KEY_ALIAS = "reimagined-winner-config-v1";
    private final Context context;

    public EnvStore(Context context) { this.context = context.getApplicationContext(); }

    public synchronized void save(InputStreamSource source) throws Exception {
        byte[] raw;
        try (java.io.InputStream in = source.open(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n, total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > 256 * 1024) throw new IllegalArgumentException("Configuration file must be 256 KB or smaller.");
                out.write(buf, 0, n);
            }
            raw = out.toByteArray();
        }
        Map<String,String> parsed = parse(new String(raw, StandardCharsets.UTF_8));
        if (parsed.isEmpty()) throw new IllegalArgumentException("No KEY=value entries were found in that file.");
        byte[] encrypted = encrypt(raw);
        File target = new File(context.getFilesDir(), FILE), tmp = new File(context.getFilesDir(), FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(encrypted); out.getFD().sync(); }
        if (target.exists() && !target.delete()) throw new IllegalStateException("Could not replace saved configuration.");
        if (!tmp.renameTo(target)) throw new IllegalStateException("Could not save configuration privately.");
        new File(context.getFilesDir(), OLD_FILE).delete();
    }

    public synchronized void saveText(String text) throws Exception {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) throw new IllegalArgumentException("Configuration text must be 256 KB or smaller.");
        Map<String,String> values=parse(text);if(values.isEmpty())throw new IllegalArgumentException("Add at least one valid KEY=value line.");
        byte[] raw=text.getBytes(StandardCharsets.UTF_8);byte[] encrypted=encrypt(raw);
        File target=new File(context.getFilesDir(),FILE),tmp=new File(context.getFilesDir(),FILE+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(encrypted);out.getFD().sync();}
        if(target.exists()&&!target.delete())throw new IllegalStateException("Could not replace saved configuration.");
        if(!tmp.renameTo(target))throw new IllegalStateException("Could not save configuration privately.");
        new File(context.getFilesDir(),OLD_FILE).delete();
    }

    public synchronized Map<String,String> load() {
        File encrypted = new File(context.getFilesDir(), FILE);
        if (encrypted.isFile()) {
            try (FileInputStream in = new FileInputStream(encrypted); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096]; int n;
                while ((n = in.read(buf)) != -1) { if (out.size() + n > 256 * 1024 + 32) return new LinkedHashMap<>(); out.write(buf, 0, n); }
                return parse(new String(decrypt(out.toByteArray()), StandardCharsets.UTF_8));
            } catch (Exception ignored) { return new LinkedHashMap<>(); }
        }
        // Upgrade a configuration saved by the previous APK, then remove the plaintext copy.
        File old = new File(context.getFilesDir(), OLD_FILE);
        if (old.isFile()) {
            try {
                byte[] raw;
                try (FileInputStream in = new FileInputStream(old); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) != -1) { if (out.size() + n > 256 * 1024) throw new IllegalArgumentException("Old configuration exceeds limit"); out.write(buf, 0, n); }
                    raw = out.toByteArray();
                }
                if (!parse(new String(raw, StandardCharsets.UTF_8)).isEmpty()) {
                    try (FileOutputStream out = new FileOutputStream(encrypted)) { out.write(encrypt(raw)); out.getFD().sync(); }
                    old.delete();
                    return parse(new String(raw, StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) { }
        }
        return new LinkedHashMap<>();
    }

    public static Map<String,String> parse(String text) {
        Map<String,String> values = new LinkedHashMap<>();
        if (text == null) return values;
        String normalized = text.replace("\uFEFF", "");
        for (String line : normalized.split("\\r?\\n")) {
            String s = line.trim(); if (s.isEmpty() || s.startsWith("#")) continue;
            if (s.startsWith("export ")) s = s.substring(7).trim();
            int eq = s.indexOf('='); if (eq < 1) continue;
            String key = s.substring(0, eq).trim(), value = s.substring(eq + 1).trim();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) { try { value=new JSONObject().put("v",new org.json.JSONTokener(value).nextValue()).optString("v",""); } catch(Exception ignored) { value=value.substring(1,value.length()-1); } }
            else if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) value = value.substring(1, value.length() - 1);
            else { int comment = value.indexOf(" #"); if (comment >= 0) value = value.substring(0, comment).trim(); }
            values.put(key, value);
        }
        return values;
    }

    private byte[] encrypt(byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = cipher.getIV(), ciphertext = cipher.doFinal(plain), result = new byte[1 + iv.length + ciphertext.length];
        result[0] = (byte)iv.length; System.arraycopy(iv, 0, result, 1, iv.length); System.arraycopy(ciphertext, 0, result, 1 + iv.length, ciphertext.length); return result;
    }
    private byte[] decrypt(byte[] input) throws Exception {
        int ivLength = input[0] & 255; if (ivLength != 12 || input.length < 1 + ivLength + 16) throw new IllegalArgumentException("Invalid saved config");
        byte[] iv = new byte[ivLength]; System.arraycopy(input, 1, iv, 0, ivLength);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(input, 1 + ivLength, input.length - 1 - ivLength);
    }
    private SecretKey getKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null); if (existing instanceof SecretKey) return (SecretKey)existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    public interface InputStreamSource { java.io.InputStream open() throws Exception; }
}
