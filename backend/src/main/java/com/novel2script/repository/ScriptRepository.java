package com.novel2script.repository;

import com.novel2script.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptRepository extends JpaRepository<Script, Long> {
    List<Script> findByProjectIdOrderByVersionDesc(Long projectId);
    Optional<Script> findTopByProjectIdOrderByVersionDesc(Long projectId);
}
