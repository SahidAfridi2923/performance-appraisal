package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "team")
@Getter @Setter
public class Team {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 120)
    private String name;
    private int sortOrder;
    private boolean active = true;
}
