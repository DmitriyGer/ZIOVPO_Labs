package ru.mfa;

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
import ru.mfa.airline.dto.TokenPairResponse;
import ru.mfa.airline.dto.UserResponse;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityStarterTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String uniqueUsername(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void register(String username, String password, String role) {
        Map<String, Object> payload = Map.of(
                "username", username,
                "password", password,
                "role", role);

        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register",
                payload,
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

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

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Test
    void authenticatedUserCanReadOwnProfile() {
        String username = uniqueUsername("user");
        String password = "User@1234";
        register(username, password, "USER");
        TokenPairResponse tokens = login(username, password);

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                baseUrl() + "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokens.getAccessToken())),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUsername()).isEqualTo(username);
        assertThat(response.getBody().getRole()).isEqualTo("ROLE_USER");
    }

    @Test
    void refreshTokenRotationInvalidatesOldRefreshToken() {
        String username = uniqueUsername("refresh");
        String password = "User@1234";
        register(username, password, "USER");
        TokenPairResponse firstPair = login(username, password);

        ResponseEntity<TokenPairResponse> refreshResponse = restTemplate.postForEntity(
                baseUrl() + "/api/auth/refresh",
                Map.of("refreshToken", firstPair.getRefreshToken()),
                TokenPairResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().getRefreshToken()).isNotEqualTo(firstPair.getRefreshToken());

        ResponseEntity<String> secondRefreshResponse = restTemplate.postForEntity(
                baseUrl() + "/api/auth/refresh",
                Map.of("refreshToken", firstPair.getRefreshToken()),
                String.class);

        assertThat(secondRefreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void roleRulesSeparateUserAndAdminEndpoints() {
        String adminUsername = uniqueUsername("admin");
        String userUsername = uniqueUsername("member");
        String password = "Admin@1234";

        register(adminUsername, password, "ADMIN");
        register(userUsername, password, "USER");

        TokenPairResponse adminTokens = login(adminUsername, password);
        TokenPairResponse userTokens = login(userUsername, password);

        ResponseEntity<String> adminEndpointForAdmin = restTemplate.exchange(
                baseUrl() + "/api/system/admin",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminTokens.getAccessToken())),
                String.class);
        assertThat(adminEndpointForAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> adminEndpointForUser = restTemplate.exchange(
                baseUrl() + "/api/system/admin",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(userTokens.getAccessToken())),
                String.class);
        assertThat(adminEndpointForUser.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<UserResponse[]> usersForAdmin = restTemplate.exchange(
                baseUrl() + "/api/users",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminTokens.getAccessToken())),
                UserResponse[].class);
        assertThat(usersForAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usersForAdmin.getBody()).isNotNull();
        assertThat(usersForAdmin.getBody().length).isGreaterThanOrEqualTo(2);
    }
}