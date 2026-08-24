package org.example.project2.global.security.handler;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHandlerTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void unauthenticatedRequestReturnsCommonUnauthorizedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);

        entryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new InsufficientAuthenticationException("test")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("error").get("code").asString()).isEqualTo("AUTH_001");
    }

    @Test
    void forbiddenRequestReturnsCommonAccessDeniedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);

        handler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("test")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("error").get("code").asString()).isEqualTo("AUTH_002");
    }
}
