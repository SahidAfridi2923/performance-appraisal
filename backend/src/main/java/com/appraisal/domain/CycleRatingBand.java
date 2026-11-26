package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cycle_rating_band")
@Getter @Setter
public class CycleRatingBand {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private Cycle cycle;
    @Column(nullable = false, length = 100)
    private String label;
    private double minScore;
}
