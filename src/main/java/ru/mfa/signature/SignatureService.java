package ru.mfa.signature;

import org.springframework.stereotype.Service;

import java.security.Signature;
import java.util.Base64;

@Service
public class SignatureService {
    private final JsonCanonicalizer jsonCanonicalizer;
    private final SignatureKeyStoreService keyStoreService;
    private final SignatureProperties properties;

    public SignatureService(JsonCanonicalizer jsonCanonicalizer,
            SignatureKeyStoreService keyStoreService,
            SignatureProperties properties) {
        this.jsonCanonicalizer = jsonCanonicalizer;
        this.keyStoreService = keyStoreService;
        this.properties = properties;
    }

    // Подписывает payload и возвращает Base64.
    public String sign(Object payload) {
        try {
            byte[] canonicalBytes = jsonCanonicalizer.canonicalize(payload);
            Signature signature = Signature.getInstance(properties.algorithm());
            signature.initSign(keyStoreService.getPrivateKey());
            signature.update(canonicalBytes);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign payload", ex);
        }
    }

    // Проверяет подпись payload публичным ключом.
    public boolean verify(Object payload, String signatureValue) {
        try {
            byte[] canonicalBytes = jsonCanonicalizer.canonicalize(payload);
            Signature signature = Signature.getInstance(properties.algorithm());
            signature.initVerify(keyStoreService.getPublicKey());
            signature.update(canonicalBytes);
            byte[] decodedSignature = Base64.getDecoder().decode(signatureValue);
            return signature.verify(decodedSignature);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not verify payload signature", ex);
        }
    }

    // Возвращает публичный ключ в Base64.
    public String getPublicKeyBase64() {
        return keyStoreService.getPublicKeyBase64();
    }
}
