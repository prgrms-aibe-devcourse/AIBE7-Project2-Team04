package org.example.project2.global.config.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {
    @Test
    void documentsKakaoOAuthLoginStartEndpoint() {
        OpenAPI openApi = new OpenAPI();

        new SwaggerConfig().kakaoOAuthLoginCustomizer().customise(openApi);

        assertThat(openApi.getPaths()).containsKey("/oauth2/authorization/kakao");
        assertThat(openApi.getPaths()
                .get("/oauth2/authorization/kakao")
                .getGet()
                .getSummary())
                .isEqualTo("카카오 로그인 시작");
    }
}
