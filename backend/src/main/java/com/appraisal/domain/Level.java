package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Employee level, e.g. Junior / Senior / Team Lead. */
@Entity
@Table(name = "level")
@Getter @Setter
public class Level {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 60)
    private String name;
    private int sortOrder;
    /** True for Team Leads: they have no TL above them, so the TL column stays blank and the Manager answers. */
    private boolean skipTlReview;
    private boolean active = true;
}
