package com.novel2script.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConfigurationProperties(prefix = "deepseek")
@Getter @Setter
public class DeepSeekConfig {

    private String apiKey;
    private String baseUrl;
    private String model;
    private int maxTokens;
    private double temperature;

    @Bean
    public RestClient deepSeekRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
