package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "cycle_category")
@Getter @Setter
public class CycleCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private Cycle cycle;
    private Long sourceCategoryId;
    /** Source team id for ROLE categories; null for COMMON. */
    private Long teamId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.Block block;
    @Column(nullable = false, length = 200)
    private String name;
    private int sortOrder;

    /** Points keyed by cycle_level id. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cycle_category_level_points", joinColumns = @JoinColumn(name = "cycle_category_id"))
    @MapKeyColumn(name = "cycle_level_id")
    @Column(name = "points", nullable = false)
    private Map<Long, Double> levelPoints = new HashMap<>();
}
