package ru.mfa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import ru.mfa.airline.dto.LicenseResponse;
import ru.mfa.airline.dto.TokenPairResponse;
import ru.mfa.airline.dto.UserResponse;
import ru.mfa.airline.model.LicenseType;
import ru.mfa.airline.model.Product;
import ru.mfa.airline.repository.LicenseTypeRepository;
import ru.mfa.airline.repository.ProductRepository;
import ru.mfa.signature.JsonCanonicalizer;
import ru.mfa.signature.SignatureKeyStoreService;

import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LicenseSignatureIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LicenseTypeRepository licenseTypeRepository;

    @Autowired
    private JsonCanonicalizer jsonCanonicalizer;

    @Autowired
    private SignatureKeyStoreService signatureKeyStoreService;

    @Autowired
    private ObjectMapper objectMapper;

    // Возвращает базовый URL тестового приложения.
    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // Генерирует уникальный логин.
    private String uniqueUsername(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // Регистрирует пользователя и возвращает его данные.
    private UserResponse register(String username, String password, String role) {
        Map<String, Object> payload = Map.of(
                "username", username,
                "password", password,
                "role", role);

        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register",
                payload,
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    // Выполняет логин и возвращает пару токенов.
    private TokenPairResponse login(String username, String password) {
        Map<String, Object> payload = Map.of(
                "username", username,
                "password", password);

        ResponseEntity<TokenPairResponse> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                payload,
                TokenPairResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    // Собирает HTTP-заголовки с токеном.
    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    // Возвращает id продукта Antivirus.
    private Long productId() {
        return productRepository.findAll().stream()
                .filter(product -> "Antivirus".equals(product.getName()))
                .map(Product::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Product Antivirus not found"));
    }

    // Возвращает id типа лицензии MONTH.
    private Long monthTypeId() {
        return licenseTypeRepository.findAll().stream()
                .filter(type -> "MONTH".equals(type.getName()))
                .map(LicenseType::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("License type MONTH not found"));
    }

    // Проверяет подпись тикета публичным ключом.
    private boolean verifyTicket(JsonNode ticketNode, String signatureValue) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(signatureKeyStoreService.getPublicKey());
        signature.update(jsonCanonicalizer.canonicalize(ticketNode));
        return signature.verify(Base64.getDecoder().decode(signatureValue));
    }

    // Создаёт копию JSON-тикета с изменённым флагом блокировки.
    private JsonNode tamperTicket(JsonNode source) {
        ObjectNode ticket = source.deepCopy();
        ticket.put("blocked", !source.path("blocked").asBoolean());
        return ticket;
    }

    // Проверяет подпись на ответах activate и check.
    @Test
    void ticketSignatureIsValidForActivateAndCheckResponses() throws Exception {
        String password = "User@1234";
        String adminUsername = uniqueUsername("admin");
        String userUsername = uniqueUsername("owner");

        register(adminUsername, password, "ADMIN");
        UserResponse owner = register(userUsername, password, "USER");

        TokenPairResponse adminTokens = login(adminUsername, password);
        TokenPairResponse userTokens = login(userUsername, password);

        Map<String, Object> createPayload = Map.of(
                "productId", productId(),
                "typeId", monthTypeId(),
                "ownerId", owner.getId(),
                "deviceCount", 1,
                "description", "signature-test");

        ResponseEntity<LicenseResponse> createResponse = restTemplate.exchange(
                baseUrl() + "/api/licenses",
                HttpMethod.POST,
                new HttpEntity<>(createPayload, authHeaders(adminTokens.getAccessToken())),
                LicenseResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();

        Map<String, Object> activatePayload = Map.of(
                "activationKey", createResponse.getBody().getCode(),
                "deviceName", "Laptop",
                "deviceMac", "AA-BB-CC-DD-EE-11");

        ResponseEntity<String> activateResponse = restTemplate.exchange(
                baseUrl() + "/api/licenses/activate",
                HttpMethod.POST,
                new HttpEntity<>(activatePayload, authHeaders(userTokens.getAccessToken())),
                String.class);

        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activateResponse.getBody()).isNotNull();
        JsonNode activateBody = objectMapper.readTree(activateResponse.getBody());
        String activateSignature = activateBody.path("signature").asText();
        JsonNode activateTicket = activateBody.path("ticket");

        assertThat(activateSignature).isNotBlank();
        assertThat(verifyTicket(
                activateTicket,
                activateSignature)).isTrue();
        assertThat(verifyTicket(
                tamperTicket(activateTicket),
                activateSignature)).isFalse();

        Map<String, Object> checkPayload = Map.of(
                "productId", productId(),
                "deviceMac", "AA-BB-CC-DD-EE-11");

        ResponseEntity<String> checkResponse = restTemplate.exchange(
                baseUrl() + "/api/licenses/check",
                HttpMethod.POST,
                new HttpEntity<>(checkPayload, authHeaders(userTokens.getAccessToken())),
                String.class);

        assertThat(checkResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkResponse.getBody()).isNotNull();
        JsonNode checkBody = objectMapper.readTree(checkResponse.getBody());
        String checkSignature = checkBody.path("signature").asText();
        JsonNode checkTicket = checkBody.path("ticket");

        assertThat(checkSignature).isNotBlank();
        assertThat(verifyTicket(
                checkTicket,
                checkSignature)).isTrue();
    }

    // Сверяет значение public key из variables с реальным ключом.
    @Test
    void configuredPublicKeyVariableMatchesKeystoreWhenPresent() {
        String configuredPublicKey = System.getenv("SIGNATURE_PUBLIC_KEY_BASE64");
        if (configuredPublicKey == null || configuredPublicKey.isBlank()) {
            return;
        }

        assertThat(signatureKeyStoreService.getPublicKeyBase64()).isEqualTo(configuredPublicKey);
    }
}
