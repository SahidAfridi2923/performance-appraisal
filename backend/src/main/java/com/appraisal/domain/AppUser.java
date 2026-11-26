package com.appraisal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
@Getter @Setter
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String username;
    @Column(nullable = false, length = 150)
    private String fullName;
    @Column(length = 200)
    private String email;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    private boolean active = true;
    /** True when the person is appraised (gets an appraisal when a cycle opens). */
    private boolean appraised = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private Set<Enums.Role> roles = new HashSet<>();

    /** Home team (the team the person is appraised in). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "level_id")
    private Level level;

    /** Extra teams a TL / Manager leads (besides the home team). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "app_user_managed_team",
            joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "team_id"))
    private Set<Team> managedTeams = new HashSet<>();

    public boolean hasRole(Enums.Role r) {
        return roles.contains(r);
    }

    /** Teams this user can act on as TL / Manager: home team plus managed teams. */
    public Set<Long> leadTeamIds() {
        Set<Long> ids = new HashSet<>();
        if (team != null) ids.add(team.getId());
        managedTeams.forEach(t -> ids.add(t.getId()));
        return ids;
    }
}
