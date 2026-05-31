package org.example.cun;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

@Service
public class CryptoService {
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private final byte[] masterKeyBytes;

    public CryptoService(@org.springframework.beans.factory.annotation.Value("${yunpan.security.master-key}") String masterKeyHex) {
        this.masterKeyBytes = hexToBytes(masterKeyHex);
    }

    public Cipher createEncryptCipher(byte[] nonce) {
        try {
            Cipher c = Cipher.getInstance(AES_GCM);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKeyBytes, "AES"), new GCMParameterSpec(128, nonce));
            return c;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public Cipher createDecryptCipher(byte[] nonce) {
        try {
            Cipher c = Cipher.getInstance(AES_GCM);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKeyBytes, "AES"), new GCMParameterSpec(128, nonce));
            return c;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public byte[] generateNonce() { byte[] n = new byte[12]; new SecureRandom().nextBytes(n); return n; }

    private static byte[] hexToBytes(String h) {
        byte[] b = new byte[h.length()/2];
        for(int i=0;i<h.length();i+=2) b[i/2]=(byte)((Character.digit(h.charAt(i),16)<<4)+Character.digit(h.charAt(i+1),16));
        return b;
    }
}
