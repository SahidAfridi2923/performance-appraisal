package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * A group of questions. COMMON categories are shared by every team; ROLE categories belong to one team.
 * Points per level weight the category inside its block (0 = the category does not apply to that level).
 */
@Entity
@Table(name = "category")
@Getter @Setter
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.Block block;
    /** Null for COMMON categories. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;
    private int sortOrder;
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_level_points", joinColumns = @JoinColumn(name = "category_id"))
    @MapKeyColumn(name = "level_id")
    @Column(name = "points", nullable = false)
    private Map<Long, Double> levelPoints = new HashMap<>();
}
