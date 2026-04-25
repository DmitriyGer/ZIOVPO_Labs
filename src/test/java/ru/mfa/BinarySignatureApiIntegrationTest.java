package ru.mfa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import ru.mfa.airline.dto.TokenPairResponse;
import ru.mfa.airline.dto.UserResponse;
import ru.mfa.signature.SignatureKeyStoreService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BinarySignatureApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignatureKeyStoreService signatureKeyStoreService;

    // Возвращает базовый URL тестового приложения.
    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // Генерирует уникальный логин.
    private String uniqueUsername(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // Регистрирует пользователя и проверяет код ответа.
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

    // Логинит пользователя и возвращает токены.
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

    // Собирает заголовки с bearer-токеном.
    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    // Создает сигнатуру и возвращает JSON ответа.
    private JsonNode createSignature(String accessToken, String threatName, String firstBytesHex, String hashHex)
            throws Exception {
        Map<String, Object> payload = Map.of(
                "threatName", threatName,
                "firstBytesHex", firstBytesHex,
                "remainderHashHex", hashHex,
                "remainderLength", 128,
                "fileType", "exe",
                "offsetStart", 0,
                "offsetEnd", 64);
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/signatures",
                HttpMethod.POST,
                new HttpEntity<>(payload, authHeaders(accessToken)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody());
    }

    // Проверяет full binary выгрузку и подпись манифеста.
    @Test
    void fullBinaryExportReturnsSignedMultipartWithManifestAndData() throws Exception {
        String password = "Admin@1234";
        String adminUsername = uniqueUsername("bin-admin");
        String userUsername = uniqueUsername("bin-user");

        register(adminUsername, password, "ADMIN");
        register(userUsername, password, "USER");

        TokenPairResponse adminTokens = login(adminUsername, password);
        TokenPairResponse userTokens = login(userUsername, password);

        JsonNode created = createSignature(
                adminTokens.getAccessToken(),
                "Win.Binary.Full",
                "A1B2C3D4",
                "ABCD1234EF567890");

        UUID createdId = UUID.fromString(created.path("id").asText());
        byte[] recordSignatureBytes = Base64.getDecoder().decode(created.path("digitalSignatureBase64").asText());

        ResponseEntity<byte[]> response = restTemplate.exchange(
                baseUrl() + "/api/binary/signatures/full",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(userTokens.getAccessToken())),
                byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).startsWith("multipart/mixed;");
        assertThat(response.getBody()).isNotNull();

        List<MultipartPart> parts = parseMultipart(response);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).headers().get("content-disposition")).contains("manifest.bin");
        assertThat(parts.get(1).headers().get("content-disposition")).contains("data.bin");

        DataFileParsed dataFile = parseData(parts.get(1).body());
        assertThat(dataFile.magic()).startsWith("DB-");
        assertThat(dataFile.version()).isEqualTo(1);
        assertThat(dataFile.recordCount()).isGreaterThanOrEqualTo(1);
        assertThat(dataFile.records()).hasSize(dataFile.recordCount());

        ManifestParsed manifest = parseManifest(parts.get(0).body());
        assertThat(manifest.magic()).startsWith("MF-");
        assertThat(manifest.version()).isEqualTo(1);
        assertThat(manifest.exportType()).isEqualTo(1);
        assertThat(manifest.sinceEpochMillis()).isEqualTo(-1L);
        assertThat(manifest.recordCount()).isGreaterThanOrEqualTo(1);
        assertThat(manifest.entries()).hasSize(manifest.recordCount());
        assertThat(manifest.recordCount()).isEqualTo(dataFile.recordCount());
        ManifestEntryParsed createdEntry = manifest.entries().stream()
                .filter(entry -> entry.id().equals(createdId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Created signature is missing in full binary manifest"));
        assertThat(createdEntry.statusCode()).isEqualTo(1);
        assertThat(createdEntry.recordSignatureBytes()).isEqualTo(recordSignatureBytes);
        assertThat(manifest.dataSha256()).isEqualTo(sha256(parts.get(1).body()));

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(signatureKeyStoreService.getPublicKey());
        verifier.update(manifest.unsignedBytes());
        assertThat(verifier.verify(manifest.manifestSignatureBytes())).isTrue();
    }

    // Проверяет increment, by-ids и ошибки валидации.
    @Test
    void incrementAndByIdsBinaryExportFollowRequiredScenarios() throws Exception {
        String password = "Admin@1234";
        String adminUsername = uniqueUsername("bin2-admin");
        String userUsername = uniqueUsername("bin2-user");

        register(adminUsername, password, "ADMIN");
        register(userUsername, password, "USER");

        TokenPairResponse adminTokens = login(adminUsername, password);
        TokenPairResponse userTokens = login(userUsername, password);

        JsonNode first = createSignature(
                adminTokens.getAccessToken(),
                "Win.Binary.One",
                "AA11BB22",
                "0011223344556677");
        JsonNode second = createSignature(
                adminTokens.getAccessToken(),
                "Win.Binary.Two",
                "CC33DD44",
                "8899AABBCCDDEEFF");

        String firstId = first.path("id").asText();
        String secondId = second.path("id").asText();
        Instant since = Instant.parse(first.path("updatedAt").asText()).minusSeconds(1);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl() + "/api/signatures/" + secondId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(adminTokens.getAccessToken())),
                Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> missingSinceResponse = restTemplate.exchange(
                baseUrl() + "/api/binary/signatures/increment",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(userTokens.getAccessToken())),
                String.class);
        assertThat(missingSinceResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<byte[]> incrementResponse = restTemplate.exchange(
                baseUrl() + "/api/binary/signatures/increment?since=" + since,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(userTokens.getAccessToken())),
                byte[].class);
        assertThat(incrementResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<MultipartPart> incrementParts = parseMultipart(incrementResponse);
        ManifestParsed incrementManifest = parseManifest(incrementParts.get(0).body());
        assertThat(incrementManifest.exportType()).isEqualTo(2);
        assertThat(incrementManifest.sinceEpochMillis()).isEqualTo(since.toEpochMilli());
        assertThat(incrementManifest.recordCount()).isGreaterThanOrEqualTo(2);
        assertThat(incrementManifest.entries().stream().map(ManifestEntryParsed::id).map(UUID::toString))
                .contains(firstId, secondId);
        assertThat(incrementManifest.entries().stream().map(ManifestEntryParsed::statusCode))
                .contains(1, 2);

        Map<String, Object> byIdsPayload = Map.of("ids", List.of(firstId, secondId));
        ResponseEntity<byte[]> byIdsResponse = restTemplate.exchange(
                baseUrl() + "/api/binary/signatures/by-ids",
                HttpMethod.POST,
                new HttpEntity<>(byIdsPayload, authHeaders(userTokens.getAccessToken())),
                byte[].class);
        assertThat(byIdsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ManifestParsed byIdsManifest = parseManifest(parseMultipart(byIdsResponse).get(0).body());
        assertThat(byIdsManifest.exportType()).isEqualTo(3);
        assertThat(byIdsManifest.recordCount()).isEqualTo(2);

        ResponseEntity<String> emptyIdsResponse = restTemplate.exchange(
                baseUrl() + "/api/binary/signatures/by-ids",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("ids", List.of()), authHeaders(userTokens.getAccessToken())),
                String.class);
        assertThat(emptyIdsResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Разбирает multipart/mixed ответ в список частей.
    private List<MultipartPart> parseMultipart(ResponseEntity<byte[]> response) {
        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        String boundary = Arrays.stream(Objects.requireNonNull(contentType).split(";"))
                .map(String::trim)
                .filter(item -> item.startsWith("boundary="))
                .map(item -> item.substring("boundary=".length()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Boundary is missing in Content-Type"));

        String marker = "--" + boundary;
        String raw = new String(Objects.requireNonNull(response.getBody()), StandardCharsets.ISO_8859_1);
        String[] chunks = raw.split(Pattern.quote(marker));

        List<MultipartPart> result = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            if (chunk.startsWith("--")) {
                continue;
            }
            String normalized = chunk.startsWith("\r\n") ? chunk.substring(2) : chunk;
            int headerEnd = normalized.indexOf("\r\n\r\n");
            if (headerEnd <= 0) {
                continue;
            }
            String headersBlock = normalized.substring(0, headerEnd);
            String bodyBlock = normalized.substring(headerEnd + 4);
            if (bodyBlock.endsWith("\r\n")) {
                bodyBlock = bodyBlock.substring(0, bodyBlock.length() - 2);
            }

            Map<String, String> headers = new LinkedHashMap<>();
            for (String line : headersBlock.split("\r\n")) {
                int separator = line.indexOf(':');
                if (separator > 0) {
                    String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(separator + 1).trim();
                    headers.put(name, value);
                }
            }
            result.add(new MultipartPart(headers, bodyBlock.getBytes(StandardCharsets.ISO_8859_1)));
        }
        return result;
    }

    // Разбирает data.bin по протоколу Lab 5.
    private DataFileParsed parseData(byte[] dataBytes) {
        BinaryReader reader = new BinaryReader(dataBytes);
        String magic = reader.readUtf8();
        int version = reader.readUInt16();
        long recordCount = reader.readUInt32();
        List<DataRecordParsed> records = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            int start = reader.position();
            String threatName = reader.readUtf8();
            byte[] firstBytes = reader.readByteArray();
            byte[] remainderHash = reader.readByteArray();
            long remainderLength = reader.readUInt64();
            int fileTypeCode = reader.readUInt8();
            long offsetStart = reader.readUInt64();
            long offsetEnd = reader.readUInt64();
            int length = reader.position() - start;
            records.add(new DataRecordParsed(threatName, firstBytes, remainderHash, remainderLength, fileTypeCode,
                    offsetStart, offsetEnd, length));
        }
        reader.assertExhausted();
        return new DataFileParsed(magic, version, (int) recordCount, records);
    }

    // Разбирает manifest.bin по протоколу Lab 5.
    private ManifestParsed parseManifest(byte[] manifestBytes) {
        BinaryReader reader = new BinaryReader(manifestBytes);
        String magic = reader.readUtf8();
        int version = reader.readUInt16();
        int exportType = reader.readUInt8();
        long generatedAtEpochMillis = reader.readInt64();
        long sinceEpochMillis = reader.readInt64();
        long recordCount = reader.readUInt32();
        byte[] dataSha256 = reader.readFixedBytes(32);
        List<ManifestEntryParsed> entries = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            UUID id = reader.readUuid();
            int statusCode = reader.readUInt8();
            long updatedAtEpochMillis = reader.readInt64();
            long dataOffset = reader.readUInt64();
            long dataLength = reader.readUInt32();
            long recordSignatureLength = reader.readUInt32();
            byte[] recordSignatureBytes = reader.readFixedBytes((int) recordSignatureLength);
            entries.add(new ManifestEntryParsed(id, statusCode, updatedAtEpochMillis, dataOffset, dataLength,
                    recordSignatureBytes));
        }
        int unsignedLength = reader.position();
        long manifestSignatureLength = reader.readUInt32();
        byte[] manifestSignatureBytes = reader.readFixedBytes((int) manifestSignatureLength);
        reader.assertExhausted();
        return new ManifestParsed(
                magic,
                version,
                exportType,
                generatedAtEpochMillis,
                sinceEpochMillis,
                (int) recordCount,
                dataSha256,
                entries,
                Arrays.copyOf(manifestBytes, unsignedLength),
                manifestSignatureBytes);
    }

    // Возвращает SHA-256 от массива байт.
    private byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not calculate SHA-256", ex);
        }
    }

    private record MultipartPart(Map<String, String> headers, byte[] body) {
    }

    private record DataRecordParsed(
            String threatName,
            byte[] firstBytes,
            byte[] remainderHash,
            long remainderLength,
            int fileTypeCode,
            long offsetStart,
            long offsetEnd,
            int rawLength) {
    }

    private record DataFileParsed(
            String magic,
            int version,
            int recordCount,
            List<DataRecordParsed> records) {
    }

    private record ManifestEntryParsed(
            UUID id,
            int statusCode,
            long updatedAtEpochMillis,
            long dataOffset,
            long dataLength,
            byte[] recordSignatureBytes) {
    }

    private record ManifestParsed(
            String magic,
            int version,
            int exportType,
            long generatedAtEpochMillis,
            long sinceEpochMillis,
            int recordCount,
            byte[] dataSha256,
            List<ManifestEntryParsed> entries,
            byte[] unsignedBytes,
            byte[] manifestSignatureBytes) {
    }

    private static final class BinaryReader {
        private final byte[] data;
        private int position;

        private BinaryReader(byte[] data) {
            this.data = Objects.requireNonNull(data);
        }

        // Возвращает текущую позицию чтения.
        private int position() {
            return position;
        }

        // Проверяет, что поток полностью прочитан.
        private void assertExhausted() {
            if (position != data.length) {
                throw new IllegalStateException("Binary payload has unread bytes");
            }
        }

        // Читает uint8.
        private int readUInt8() {
            ensureAvailable(1);
            return data[position++] & 0xFF;
        }

        // Читает uint16 BigEndian.
        private int readUInt16() {
            ensureAvailable(2);
            int value = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
            position += 2;
            return value;
        }

        // Читает uint32 BigEndian.
        private long readUInt32() {
            ensureAvailable(4);
            long value = ((long) (data[position] & 0xFF) << 24)
                    | ((long) (data[position + 1] & 0xFF) << 16)
                    | ((long) (data[position + 2] & 0xFF) << 8)
                    | (data[position + 3] & 0xFFL);
            position += 4;
            return value;
        }

        // Читает int64 BigEndian.
        private long readInt64() {
            ensureAvailable(8);
            long value = ((long) (data[position] & 0xFF) << 56)
                    | ((long) (data[position + 1] & 0xFF) << 48)
                    | ((long) (data[position + 2] & 0xFF) << 40)
                    | ((long) (data[position + 3] & 0xFF) << 32)
                    | ((long) (data[position + 4] & 0xFF) << 24)
                    | ((long) (data[position + 5] & 0xFF) << 16)
                    | ((long) (data[position + 6] & 0xFF) << 8)
                    | (data[position + 7] & 0xFFL);
            position += 8;
            return value;
        }

        // Читает uint64 как long.
        private long readUInt64() {
            long value = readInt64();
            if (value < 0) {
                throw new IllegalStateException("uint64 value is out of signed long range");
            }
            return value;
        }

        // Читает UUID из двух int64.
        private UUID readUuid() {
            long msb = readInt64();
            long lsb = readInt64();
            return new UUID(msb, lsb);
        }

        // Читает строку UTF-8: uint32 длина + байты.
        private String readUtf8() {
            int length = Math.toIntExact(readUInt32());
            ensureAvailable(length);
            String value = new String(data, position, length, StandardCharsets.UTF_8);
            position += length;
            return value;
        }

        // Читает массив байт: uint32 длина + байты.
        private byte[] readByteArray() {
            int length = Math.toIntExact(readUInt32());
            return readFixedBytes(length);
        }

        // Читает фиксированное количество байт.
        private byte[] readFixedBytes(int length) {
            ensureAvailable(length);
            byte[] out = Arrays.copyOfRange(data, position, position + length);
            position += length;
            return out;
        }

        // Проверяет, что осталось достаточное количество байт.
        private void ensureAvailable(int length) {
            if (length < 0 || position + length > data.length) {
                throw new IllegalStateException("Binary payload is truncated");
            }
        }
    }
}
