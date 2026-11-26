package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * An appraisal period. When opened, the live question bank and scoring settings are copied into
 * cycle_* tables, so later edits to the bank never change this cycle's results.
 */
@Entity
@Table(name = "cycle")
@Getter @Setter
public class Cycle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.CycleStatus status = Enums.CycleStatus.DRAFT;
    private Instant createdAt = Instant.now();
    private Instant openedAt;
    private Instant closedAt;
    private Instant snapshotAt;

    // snapshot of scoring settings
    private double commonWeight;
    private double roleWeight;
    private double selfWeight;
    private double peerWeight;
    private double tlWeight;
    private double managerWeight;
}
