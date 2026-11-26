package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "achievement")
@Getter @Setter
public class Achievement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "appraisal_id")
    private Appraisal appraisal;
    private int sortOrder;
    @Column(nullable = false, length = 1000)
    private String text;
    @Column(length = 200)
    private String situation;
    @Column(length = 200)
    private String businessImpact;
    /** Counts only when the lead verifies it. */
    private boolean verified;
}
