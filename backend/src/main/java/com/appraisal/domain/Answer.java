package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "answer", uniqueConstraints = @UniqueConstraint(columnNames = {"review_id", "cycle_question_id"}))
@Getter @Setter
public class Answer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private Review review;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_question_id")
    private CycleQuestion question;
    /** 1..10, or null when N/A. */
    @Column(name = "score_value")
    private Integer value;
    /** True = could not observe (N/A). */
    private boolean na;
    @Column(length = 1000)
    private String comment;
}
