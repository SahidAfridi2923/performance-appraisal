package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One reviewer's answers for one appraisal in one role (Self / Peer / TL / Manager). */
@Entity
@Table(name = "review", uniqueConstraints = @UniqueConstraint(columnNames = {"appraisal_id", "reviewer_id", "role"}))
@Getter @Setter
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "appraisal_id")
    private Appraisal appraisal;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewer_id")
    private AppUser reviewer;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.ReviewerRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.ReviewStatus status = Enums.ReviewStatus.DRAFT;
    private Instant updatedAt = Instant.now();
    private Instant submittedAt;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Answer> answers = new ArrayList<>();
}
