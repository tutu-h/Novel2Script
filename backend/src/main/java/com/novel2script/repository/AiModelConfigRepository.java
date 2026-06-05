package com.novel2script.repository;

import com.novel2script.entity.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {
    List<AiModelConfig> findAllByOrderByCreatedAtDesc();
    List<AiModelConfig> findByEnabledTrue();
    Optional<AiModelConfig> findByActiveTrue();
}
