package com.novel2script.repository;

import com.novel2script.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    Optional<Analysis> findByProjectId(Long projectId);
    void deleteByProjectId(Long projectId);
}
