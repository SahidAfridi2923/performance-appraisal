package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cycle_question")
@Getter @Setter
public class CycleQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "cycle_category_id")
    private CycleCategory category;
    private Long sourceQuestionId;
    @Column(nullable = false, length = 1000)
    private String text;
    private double weight;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.ReviewedBy reviewedBy;
    @Column(columnDefinition = "text")
    private String guidance;
    private int sortOrder;

    /** cycle_level ids this question is switched off for. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cycle_question_excluded_level", joinColumns = @JoinColumn(name = "cycle_question_id"))
    @Column(name = "cycle_level_id")
    private Set<Long> excludedLevelIds = new HashSet<>();
}
