package ru.mfa.signature;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "signature")
public record SignatureProperties(
        @DefaultValue("classpath:signature/signing.jks") String keyStorePath,
        @DefaultValue("JKS") String keyStoreType,
        @DefaultValue("admin11") String keyStorePassword,
        @DefaultValue("ticket-signing") String keyAlias,
        String keyPassword,
        @DefaultValue("SHA256withRSA") String algorithm,
        @DefaultValue("") String publicKeyBase64) {

    // Возвращает пароль ключа или пароль хранилища.
    public String resolvedKeyPassword() {
        if (keyPassword == null || keyPassword.isBlank()) {
            return keyStorePassword;
        }
        return keyPassword;
    }
}
