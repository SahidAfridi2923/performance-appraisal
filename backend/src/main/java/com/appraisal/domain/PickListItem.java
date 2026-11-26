package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Options for the Achievement "Situation" and "Business impact" pick lists. */
@Entity
@Table(name = "pick_list_item")
@Getter @Setter
public class PickListItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Enums.PickListType listType;
    @Column(nullable = false, length = 200)
    private String label;
    private int sortOrder;
}
