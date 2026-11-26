package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** One employee in one cycle. */
@Entity
@Table(name = "appraisal", uniqueConstraints = @UniqueConstraint(columnNames = {"cycle_id", "employee_id"}))
@Getter @Setter
public class Appraisal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private Cycle cycle;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    private AppUser employee;
    @Column(nullable = false)
    private Long teamId;
    @Column(nullable = false, length = 120)
    private String teamName;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cycle_level_id")
    private CycleLevel level;
    /** Reason for any 9-10 or 1-2, key strengths and improvement areas. */
    @Column(columnDefinition = "text")
    private String leadComments;
}
