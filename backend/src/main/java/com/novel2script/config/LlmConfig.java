package com.novel2script.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "llm")
@Getter @Setter
public class LlmConfig {

    /** Maximum tokens for LLM responses */
    private int maxTokens = 8192;

    /** Temperature for LLM generation (0.0 - 1.0) */
    private double temperature = 0.7;
}
