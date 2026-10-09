package com.workorder.back.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j 文档配置（基于 springdoc OpenAPI 3）
 * 访问地址：http://localhost:8080/api/doc.html
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI blogOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Blog API")
                        .description("张文龙个人博客后端接口文档")
                        .version("1.0"));
    }
}
