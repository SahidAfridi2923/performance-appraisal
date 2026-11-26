package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "goal")
@Getter @Setter
public class Goal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "appraisal_id")
    private Appraisal appraisal;
    private int sortOrder;
    @Column(nullable = false, length = 1000)
    private String text;
    @Column(length = 1000)
    private String successCriteria;
}
