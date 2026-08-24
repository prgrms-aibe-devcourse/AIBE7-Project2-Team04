package org.example.project2.global.config.swagger;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class SwaggerConfig {
    @Bean
    public OpenApiCustomizer kakaoOAuthLoginCustomizer() {
        return openApi -> openApi.path(
                "/oauth2/authorization/kakao",
                new PathItem().get(new Operation()
                        .addTagsItem("Authentication")
                        .summary("카카오 로그인 시작")
                        .description("브라우저를 카카오 인증 화면으로 이동시킵니다. Swagger에서 실행하면 카카오 로그인 화면으로 리다이렉트됩니다.")
                        .responses(new ApiResponses().addApiResponse(
                                "302",
                                new ApiResponse()
                                        .description("카카오 인증 화면으로 이동")
                                        .addHeaderObject(
                                                "Location",
                                                new Header()
                                                        .description("카카오 인증 화면 URL")
                                                        .schema(new StringSchema())
                                        )
                        )))
        );
    }
}
