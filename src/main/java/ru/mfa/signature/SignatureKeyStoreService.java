package ru.mfa.signature;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Service
public class SignatureKeyStoreService {
    private final SignatureProperties properties;
    private volatile KeyMaterial cachedKeyMaterial;

    public SignatureKeyStoreService(SignatureProperties properties) {
        this.properties = properties;
    }

    // Возвращает приватный ключ для подписи.
    public PrivateKey getPrivateKey() {
        return loadKeyMaterial().privateKey();
    }

    // Возвращает публичный ключ для проверки.
    public PublicKey getPublicKey() {
        return loadKeyMaterial().publicKey();
    }

    // Возвращает сертификат из keystore.
    public X509Certificate getCertificate() {
        return loadKeyMaterial().certificate();
    }

    // Возвращает публичный ключ в Base64.
    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(getPublicKey().getEncoded());
    }

    // Загружает ключи один раз и кэширует их.
    private KeyMaterial loadKeyMaterial() {
        KeyMaterial localCopy = cachedKeyMaterial;
        if (localCopy != null) {
            return localCopy;
        }
        synchronized (this) {
            if (cachedKeyMaterial == null) {
                cachedKeyMaterial = readKeyMaterial();
            }
            return cachedKeyMaterial;
        }
    }

    // Читает приватный ключ и сертификат из хранилища.
    private KeyMaterial readKeyMaterial() {
        try (InputStream inputStream = openKeyStore()) {
            KeyStore keyStore = KeyStore.getInstance(properties.keyStoreType());
            keyStore.load(inputStream, properties.keyStorePassword().toCharArray());

            KeyStore.Entry entry = keyStore.getEntry(
                    properties.keyAlias(),
                    new KeyStore.PasswordProtection(properties.resolvedKeyPassword().toCharArray()));
            if (!(entry instanceof KeyStore.PrivateKeyEntry privateKeyEntry)) {
                throw new IllegalStateException("Signature key alias not found: " + properties.keyAlias());
            }

            Certificate certificate = privateKeyEntry.getCertificate();
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new IllegalStateException("Signature certificate is missing or invalid");
            }

            return new KeyMaterial(privateKeyEntry.getPrivateKey(), x509Certificate.getPublicKey(), x509Certificate);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load signature keystore", ex);
        }
    }

    // Открывает keystore по classpath, file URL или обычному пути.
    private InputStream openKeyStore() throws IOException {
        String path = properties.keyStorePath();
        if (path.startsWith("classpath:")) {
            String classpathLocation = path.substring("classpath:".length());
            return new ClassPathResource(classpathLocation).getInputStream();
        }
        if (path.startsWith("file:")) {
            Path filePath = Paths.get(path.substring("file:".length()));
            return Files.newInputStream(filePath);
        }
        return Files.newInputStream(Paths.get(path));
    }

    private record KeyMaterial(PrivateKey privateKey, PublicKey publicKey, X509Certificate certificate) {
    }
}
