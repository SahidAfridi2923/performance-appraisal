package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cycle_level")
@Getter @Setter
public class CycleLevel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private Cycle cycle;
    private Long sourceLevelId;
    @Column(nullable = false, length = 60)
    private String name;
    private int sortOrder;
    private boolean skipTlReview;
}
