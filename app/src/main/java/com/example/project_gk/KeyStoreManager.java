package com.example.project_gk;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Log;
import android.util.Pair;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;

public class KeyStoreManager {
    private static final String KEY_ALIAS = "vault_master_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    /** OID của Android Key Attestation extension trong certificate. */
    private static final String ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17";

    /** Xóa khóa cũ để buộc sinh lại khóa mới (không dùng trong luồng thường). */
    public static synchronized void deleteKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS);
        }
    }

    /** Trạng thái bảo mật phần cứng của khóa để phục vụ báo cáo/demo. */
    public static class HardwareSecurityLevel {
        public final boolean insideSecureHardware;
        public final boolean strongBoxBacked;
        public final String securityLevelName;
        public final int osVersion;

        HardwareSecurityLevel(boolean insideSecureHardware, boolean strongBoxBacked,
                              String securityLevelName, int osVersion) {
            this.insideSecureHardware = insideSecureHardware;
            this.strongBoxBacked = strongBoxBacked;
            this.securityLevelName = securityLevelName;
            this.osVersion = osVersion;
        }
    }

    /**
     * Kiểm tra khóa có thực sự nằm trong vùng bảo mật phần cứng (TEE/StrongBox) hay không.
     * Dùng trong báo cáo "Hardware-backed Keystore".
     */
    public static HardwareSecurityLevel getHardwareSecurityLevel() {
        boolean insideSecureHardware = false;
        boolean strongBoxBacked = false;
        String levelName = "Unknown";
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                KeyStore.SecretKeyEntry entry =
                        (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
                SecretKeyFactory factory =
                        SecretKeyFactory.getInstance(entry.getSecretKey().getAlgorithm(),
                                ANDROID_KEYSTORE);
                KeyInfo info = (KeyInfo) factory.getKeySpec(entry.getSecretKey(), KeyInfo.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    int level = info.getSecurityLevel();
                    levelName = String.valueOf(level);
                    insideSecureHardware = level == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                            || level == KeyProperties.SECURITY_LEVEL_STRONGBOX;
                    strongBoxBacked = level == KeyProperties.SECURITY_LEVEL_STRONGBOX;
                } else {
                    insideSecureHardware = info.isInsideSecureHardware();
                    levelName = insideSecureHardware ? "TEE (hardware)" : "Software";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new HardwareSecurityLevel(insideSecureHardware, strongBoxBacked, levelName,
                Build.VERSION.SDK_INT);
    }

    /** Sinh cấu hình khóa: ưu tiên StrongBox, fallback TEE nếu thiết bị không hỗ trợ. */
    private static KeyGenParameterSpec buildKeySpec(boolean strongBox) {
        KeyGenParameterSpec.Builder builder =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256);
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Yêu cầu khóa phải nằm trong StrongBox (chip bảo mật chuyên dụng).
            builder.setIsStrongBoxBacked(true);
        }
        return builder.build();
    }

    private static SecretKey generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        try {
            // Ưu tiên StrongBox nếu phần cứng hỗ trợ.
            keyGenerator.init(buildKeySpec(true));
            return keyGenerator.generateKey();
        } catch (StrongBoxUnavailableException e) {
            // Thiết bị không có StrongBox -> quay về TEE / secure hardware mặc định.
            keyGenerator.init(buildKeySpec(false));
            return keyGenerator.generateKey();
        }
    }

    /**
     * B6 — Đọc và ghi log chuỗi chứng thư (certificate chain) của khóa trong AndroidKeyStore.
     * Chuỗi này do Google ký và chứa attestation extension, dùng để chứng minh khóa
     * thực sự được sinh trong vùng bảo mật phần cứng (TEE/StrongBox).
     * Lưu ý: chỉ ĐỌC, không sinh lại khóa nên không ảnh hưởng dữ liệu vault hiện có.
     */
    public static String logKeyAttestation() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                Log.d("KeyStoreManager", "Attestation: chưa có khóa '" + KEY_ALIAS + "'");
                return "NO_KEY";
            }

            Certificate[] chain = keyStore.getCertificateChain(KEY_ALIAS);
            if (chain == null || chain.length == 0) {
                Log.w("KeyStoreManager", "Attestation: Keystore không trả về chuỗi chứng thư.");
                return "NO_CHAIN";
            }

            X509Certificate leaf = (X509Certificate) chain[0];
            boolean hasAttestation = leaf.getExtensionValue(ATTESTATION_OID) != null;
            String summary = "Attestation: chain=" + chain.length
                    + " chứng thư | leaf=" + leaf.getSubjectDN().getName()
                    + " | attestationExtension=" + hasAttestation;
            Log.d("KeyStoreManager", summary);
            return summary;
        } catch (Exception e) {
            Log.w("KeyStoreManager", "Attestation: đọc chuỗi chứng thư thất bại", e);
            return "ERROR";
        }
    }

    private static SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry secretKeyEntry =
                    (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return secretKeyEntry.getSecretKey();
        } else {
            return generateKey();
        }
    }

    public static Pair<byte[], byte[]> encryptData(byte[] data) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(data);
        return new Pair<>(encrypted, iv);
    }

    public static byte[] decryptData(byte[] cipherText, byte[] iv) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return cipher.doFinal(cipherText);
    }
}