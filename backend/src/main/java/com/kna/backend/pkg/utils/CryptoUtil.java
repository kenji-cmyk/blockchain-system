package com.kna.backend.pkg.utils;

import com.kna.backend.entity.Wallet;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoUtil {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    public static Wallet generateWallet() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            return new Wallet(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPublic().getEncoded()),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPrivate().getEncoded())
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate wallet", exception);
        }
    }

    public static String sign(String privateKey, String data) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(toPrivateKey(privateKey));
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not sign transaction", exception);
        }
    }

    public static boolean verify(String publicKey, String data, String signatureText) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(toPublicKey(publicKey));
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(decodeBase64(signatureText));
        } catch (Exception exception) {
            return false;
        }
    }

    private static PrivateKey toPrivateKey(String privateKey) throws Exception {
        byte[] keyBytes = decodeBase64(privateKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(keySpec);
    }

    private static PublicKey toPublicKey(String publicKey) throws Exception {
        byte[] keyBytes = decodeBase64(publicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(keySpec);
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getUrlDecoder().decode(padBase64(value));
        } catch (IllegalArgumentException exception) {
            return Base64.getDecoder().decode(value);
        }
    }

    private static String padBase64(String value) {
        int padding = (4 - value.length() % 4) % 4;
        return value + "=".repeat(padding);
    }
}
