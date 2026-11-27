package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An admin's explicit "add to / remove from this cycle" decision for one person. */
@Entity
@Table(name = "cycle_membership", uniqueConstraints = @UniqueConstraint(columnNames = {"cycle_id", "user_id"}))
@Getter @Setter
public class CycleMembership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    private boolean included;
}
