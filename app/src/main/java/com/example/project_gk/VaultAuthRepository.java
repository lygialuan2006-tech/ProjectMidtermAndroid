package com.example.project_gk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Pair;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.security.SecureRandom;

public class VaultAuthRepository {
    private static final String KEY_CIPHER_TEXT = "encrypted_db_key";
    private static final String KEY_IV = "initialization_vector";

    private final SharedPreferences securePrefs;

    public VaultAuthRepository(Context context) {
        SharedPreferences prefs = null;
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            prefs = EncryptedSharedPreferences.create(
                    "vault_auth_prefs",
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.securePrefs = prefs;
    }

    public byte[] getOrCreateDatabasePassphrase() {
        try {
            String storedEncryptedKey = securePrefs.getString(KEY_CIPHER_TEXT, null);
            String storedIv = securePrefs.getString(KEY_IV, null);

            if (storedEncryptedKey != null && storedIv != null) {
                byte[] cipherText = Base64.decode(storedEncryptedKey, Base64.DEFAULT);
                byte[] iv = Base64.decode(storedIv, Base64.DEFAULT);
                return KeyStoreManager.decryptData(cipherText, iv);
            } else {
                byte[] randomKey = new byte[32];
                new SecureRandom().nextBytes(randomKey);

                Pair<byte[], byte[]> encryptedResult = KeyStoreManager.encryptData(randomKey);

                securePrefs.edit()
                        .putString(KEY_CIPHER_TEXT, Base64.encodeToString(encryptedResult.first, Base64.DEFAULT))
                        .putString(KEY_IV, Base64.encodeToString(encryptedResult.second, Base64.DEFAULT))
                        .apply();

                return randomKey;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}