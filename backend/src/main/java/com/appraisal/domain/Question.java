package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "question")
@Getter @Setter
public class Question {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    @Column(nullable = false, length = 1000)
    private String text;
    @Column(nullable = false)
    private double weight = 1.0;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.ReviewedBy reviewedBy = Enums.ReviewedBy.ALL;
    @Column(columnDefinition = "text")
    private String guidance;
    private int sortOrder;
    /** False = archived (soft-deleted). Archived questions are not copied into new cycles. */
    private boolean active = true;

    /** Levels this question is switched off for (in addition to the category's 0 points). Empty = all levels. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_excluded_level", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "level_id")
    private Set<Long> excludedLevelIds = new HashSet<>();
}
