package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Single-row table with the block weights and reviewer weights (Scoring_Rules sheet). */
@Entity
@Table(name = "scoring_settings")
@Getter @Setter
public class ScoringSettings {
    @Id
    private Long id = 1L;
    private double commonWeight = 0.4;
    private double roleWeight = 0.6;
    private double selfWeight = 0.1;
    private double peerWeight = 0.2;
    private double tlWeight = 0.3;
    private double managerWeight = 0.4;
}
