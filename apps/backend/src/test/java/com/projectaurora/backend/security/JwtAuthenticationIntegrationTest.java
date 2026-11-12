
package com.projectaurora.backend.security;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // 1. Valid JWT should allow access to the student endpoint.
    @Test
    void validJwtCanAccessStudentEndpoint() throws Exception {

        String token = registerStudentAndGetToken();

        mockMvc.perform(
                get("/api/test/student")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // 2. Expired JWT should return HTTP 401.
    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {

        String token = createExpiredToken();

        mockMvc.perform(
                get("/api/test/student")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // 3. JWT signed with a different key should return HTTP 401.
    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {

        SecretKey differentKey =
                Keys.hmacShaKeyFor(new byte[32]);

        String token = Jwts.builder()
                .subject("unknown@example.com")
                .issuedAt(new Date())
                .expiration(Date.from(
                        Instant.now().plusSeconds(300)))
                .signWith(differentKey)
                .compact();

        mockMvc.perform(
                get("/api/test/student")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // 4. Malformed JWT should return HTTP 401.
    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {

        mockMvc.perform(
                get("/api/test/student")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    // 5. Empty Bearer token should return HTTP 401.
    @Test
    void emptyBearerTokenReturnsUnauthorized() throws Exception {

        mockMvc.perform(
                get("/api/test/student")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    // 6. Missing JWT should return HTTP 401.
    @Test
    void missingJwtReturnsUnauthorized() throws Exception {

        mockMvc.perform(
                get("/api/test/student"))
                .andExpect(status().isUnauthorized());
    }

    // 7. Student should not access the instructor endpoint.
    @Test
    void studentCannotAccessInstructorEndpointUsingValidJwt()
            throws Exception {

        String token = registerStudentAndGetToken();

        mockMvc.perform(
                get("/api/test/instructor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // Helper: register a student and return the generated JWT.
    private String registerStudentAndGetToken() throws Exception {

        String email =
                "jwt-test-" + UUID.randomUUID() + "@example.com";

        Map<String, String> request = Map.of(
                "firstName", "JWT",
                "lastName", "Tester",
                "email", email,
                "password", "TestPassword@123"
        );

        String response = mockMvc.perform(
                post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);

        return json.get("accessToken").asText();
    }

    // Helper: create an expired JWT using the configured signing key.
    private String createExpiredToken() {

        SecretKey signingKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtSecret));

        Instant now = Instant.now();

        return Jwts.builder()
                .subject("expired@example.com")
                .issuedAt(Date.from(now.minusSeconds(300)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(signingKey)
                .compact();
    }
}
