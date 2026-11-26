package com.appraisal.service;

import com.appraisal.domain.*;
import com.appraisal.dto.Dtos.*;

import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Entity -> DTO conversions. */
public final class Mapper {
    private Mapper() {}

    public static TeamDto team(Team t) {
        return new TeamDto(t.getId(), t.getCode(), t.getName(), t.getSortOrder(), t.isActive());
    }

    public static LevelDto level(Level l) {
        return new LevelDto(l.getId(), l.getName(), l.getSortOrder(), l.isSkipTlReview(), l.isActive());
    }

    public static QuestionDto question(Question q) {
        return new QuestionDto(q.getId(), q.getCategory().getId(), q.getText(), q.getWeight(), q.getReviewedBy(),
                q.getGuidance(), q.getSortOrder(), q.isActive(), new HashSet<>(q.getExcludedLevelIds()));
    }

    public static CategoryDto category(Category c, List<QuestionDto> questions) {
        return new CategoryDto(c.getId(), c.getName(), c.getBlock(), c.getTeam() == null ? null : c.getTeam().getId(),
                c.getSortOrder(), c.isActive(), new TreeMap<>(c.getLevelPoints()), questions);
    }

    public static SettingsDto settings(ScoringSettings s) {
        return new SettingsDto(s.getCommonWeight(), s.getRoleWeight(), s.getSelfWeight(), s.getPeerWeight(),
                s.getTlWeight(), s.getManagerWeight());
    }

    public static CycleDto cycle(Cycle c, long appraisalCount) {
        SettingsDto w = c.getSnapshotAt() == null ? null : new SettingsDto(c.getCommonWeight(), c.getRoleWeight(),
                c.getSelfWeight(), c.getPeerWeight(), c.getTlWeight(), c.getManagerWeight());
        return new CycleDto(c.getId(), c.getName(), c.getStatus(), c.getCreatedAt(), c.getOpenedAt(), c.getClosedAt(),
                c.getSnapshotAt(), appraisalCount, w);
    }

    public static UserDto user(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.isActive(), u.isAppraised(),
                u.getRoles(), u.getTeam() == null ? null : u.getTeam().getId(), u.getTeam() == null ? null : u.getTeam().getName(),
                u.getLevel() == null ? null : u.getLevel().getId(), u.getLevel() == null ? null : u.getLevel().getName(),
                u.getManagedTeams().stream().map(Team::getId).collect(Collectors.toSet()));
    }

    public static MeDto me(AppUser u) {
        return new MeDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRoles(),
                u.getTeam() == null ? null : u.getTeam().getId(), u.getTeam() == null ? null : u.getTeam().getName(),
                u.getLevel() == null ? null : u.getLevel().getId(), u.getLevel() == null ? null : u.getLevel().getName(),
                u.leadTeamIds());
    }

    public static AchievementDto achievement(Achievement a) {
        return new AchievementDto(a.getId(), a.getText(), a.getSituation(), a.getBusinessImpact(), a.isVerified());
    }

    public static GoalDto goal(Goal g) {
        return new GoalDto(g.getId(), g.getText(), g.getSuccessCriteria());
    }
}
