package org.example.yunpan.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LEN = 128;
    private static final int GCM_NONCE_LEN = 12;

    /**
     * hex 字符串 → byte[]
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    /**
     * AES-256-GCM 加密（hex 密钥版）。
     * hexKey: 64 字符 hex 字符串 = 32 字节 = AES-256 密钥
     * 返回: nonce(12B) + ciphertext + GCM tag
     */
    public byte[] encryptBytesWithHexKey(String hexKey, byte[] plaintext) {
        try {
            SecretKeySpec key = new SecretKeySpec(hexToBytes(hexKey), AES);
            byte[] nonce = new byte[GCM_NONCE_LEN];
            new SecureRandom().nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[GCM_NONCE_LEN + ciphertext.length];
            System.arraycopy(nonce, 0, result, 0, GCM_NONCE_LEN);
            System.arraycopy(ciphertext, 0, result, GCM_NONCE_LEN, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * AES-256-GCM 解密（hex 密钥版）。
     * encrypted: nonce(12B) + ciphertext + GCM tag
     */
    public byte[] decryptBytesWithHexKey(String hexKey, byte[] encrypted) {
        try {
            SecretKeySpec key = new SecretKeySpec(hexToBytes(hexKey), AES);

            byte[] nonce = new byte[GCM_NONCE_LEN];
            byte[] ciphertext = new byte[encrypted.length - GCM_NONCE_LEN];
            System.arraycopy(encrypted, 0, nonce, 0, GCM_NONCE_LEN);
            System.arraycopy(encrypted, GCM_NONCE_LEN, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, nonce));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }

    /**
     * AES-256-GCM 加密：hexKey 加密明文，返回 Base64 密文。
     */
    public String encryptWithHexKey(String hexKey, byte[] plaintext) {
        byte[] encrypted = encryptBytesWithHexKey(hexKey, plaintext);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * AES-256-GCM 解密：hexKey 解 Base64 密文，返回明文字节。
     * 用于登录时从 encryptedKey 中解出文件密钥。
     * hexKey: SHA-256(password) 的 hex 字符串
     * base64Cipher: 客户端加密后的 Base64 字符串
     */
    public byte[] decryptBase64WithHexKey(String hexKey, String base64Cipher) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(base64Cipher);
            return decryptBytesWithHexKey(hexKey, encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败(密钥可能不匹配)", e);
        }
    }
}
