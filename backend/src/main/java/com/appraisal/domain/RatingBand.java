package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Overall rating band: a final score >= minScore (and below the next band) gets this label. */
@Entity
@Table(name = "rating_band")
@Getter @Setter
public class RatingBand {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String label;
    private double minScore;
}
