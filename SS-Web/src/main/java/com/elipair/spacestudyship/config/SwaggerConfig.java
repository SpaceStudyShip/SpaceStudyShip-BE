package com.elipair.spacestudyship.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        SecurityRequirement securityRequirement = new SecurityRequirement().addList("JWT");

        SecurityScheme securityScheme = new SecurityScheme()
                .name("JWT")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .description("JWT Access Token을 입력하세요. 'Bearer ' 접두사는 자동으로 추가됩니다.");

        Info info = new Info()
                .title("우주공부선 API")
                .version("0.0.3")
                .description("우주공부선 백엔드 API 문서입니다.");

        return new OpenAPI()
                .info(info)
                .addSecurityItem(securityRequirement)
                .components(new Components().addSecuritySchemes("JWT", securityScheme));
    }
}
