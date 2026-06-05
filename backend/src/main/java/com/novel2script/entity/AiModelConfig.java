package com.novel2script.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_model_configs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AiModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider; // deepseek, qwen, openai, zhipu, moonshot, etc.

    @Column(nullable = false)
    private String modelName; // e.g. deepseek-chat, qwen-turbo

    @Column(nullable = false, length = 500)
    private String apiKey;

    @Column(length = 500)
    private String baseUrl;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(nullable = false)
    private Boolean active = false; // the one selected for generation

    @Column(length = 50)
    private String lastTestStatus; // success, fail, unknown

    private LocalDateTime lastTestAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
