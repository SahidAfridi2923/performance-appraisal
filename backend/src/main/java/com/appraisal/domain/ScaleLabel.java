package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Meaning of each answer number 1-10, shown to reviewers. */
@Entity
@Table(name = "scale_label")
@Getter @Setter
public class ScaleLabel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scale_value", nullable = false, unique = true)
    private int value;
    @Column(nullable = false, length = 200)
    private String label;
}
