package com.careerq.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI careerqOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("职问桥 API")
                .version("v1")
                .description("前后端接口契约实现，详见 docs/接口契约-职问桥.md"));
    }
}
