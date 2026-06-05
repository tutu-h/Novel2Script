package com.novel2script.repository;

import com.novel2script.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByProjectIdOrderByChapterNumberAsc(Long projectId);
    Optional<Chapter> findByProjectIdAndChapterNumber(Long projectId, Integer chapterNumber);
    long countByProjectId(Long projectId);
}
