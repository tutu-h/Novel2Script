package com.novel2script.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "analyses")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    @JsonIgnore
    private Project project;

    @Column(columnDefinition = "CLOB")
    private String charactersJson = "[]";

    @Column(columnDefinition = "CLOB")
    private String locationsJson = "[]";

    @Column(columnDefinition = "CLOB")
    private String eventsJson = "[]";

    @Column(columnDefinition = "CLOB")
    private String chapterSummariesJson = "[]";

    @Column(columnDefinition = "CLOB")
    private String analyzedChaptersJson = "[]";

    @Column(columnDefinition = "CLOB")
    private String perChapterAnalysisJson = "[]";

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
