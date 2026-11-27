package com.appraisal.dto;

import com.appraisal.domain.Enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All request / response bodies of the REST API. */
public final class Dtos {
    private Dtos() {}

    // ---------- auth ----------
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, MeDto user) {}

    public record MeDto(Long id, String username, String fullName, String email, Set<Role> roles,
                        Long teamId, String teamName, Long levelId, String levelName, Set<Long> leadTeamIds) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {}

    // ---------- question bank / configuration ----------
    public record TeamDto(Long id, @NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 120) String name,
                          int sortOrder, boolean active) {}

    public record LevelDto(Long id, @NotBlank @Size(max = 60) String name, int sortOrder, boolean skipTlReview, boolean active) {}

    public record QuestionDto(Long id, @NotNull Long categoryId, @NotBlank @Size(max = 1000) String text,
                              @NotNull @PositiveOrZero Double weight, @NotNull ReviewedBy reviewedBy, String guidance,
                              int sortOrder, boolean active, Set<Long> excludedLevelIds) {}

    public record CategoryDto(Long id, @NotBlank @Size(max = 200) String name, @NotNull Block block, Long teamId,
                              int sortOrder, boolean active, Map<Long, Double> levelPoints, List<QuestionDto> questions) {}

    /** Points share of the final score per category and level (read-only helper like Scoring_Rules rows 29-38). */
    public record QuestionBankDto(Long teamId, List<LevelDto> levels, List<CategoryDto> common, List<CategoryDto> role,
                                  SettingsDto settings) {}

    public record OrderRequest(@NotNull List<Long> ids) {}

    public record SettingsDto(@NotNull @PositiveOrZero Double commonWeight, @NotNull @PositiveOrZero Double roleWeight,
                              @NotNull @PositiveOrZero Double selfWeight, @NotNull @PositiveOrZero Double peerWeight,
                              @NotNull @PositiveOrZero Double tlWeight, @NotNull @PositiveOrZero Double managerWeight) {}

    public record RatingBandDto(Long id, @NotBlank String label, @NotNull Double minScore) {}

    public record ScaleLabelDto(@Min(1) @Max(10) int value, @NotBlank String label) {}

    public record PickListDto(@NotNull List<@NotBlank String> items) {}

    public record PublicConfigDto(List<TeamDto> teams, List<LevelDto> levels, List<RatingBandDto> ratingBands,
                                  List<ScaleLabelDto> scaleLabels, Map<PickListType, List<String>> pickLists) {}

    // ---------- users ----------
    public record UserDto(Long id, String username, String fullName, String email, boolean active, boolean appraised,
                          Set<Role> roles, Long teamId, String teamName, Long levelId, String levelName, Set<Long> managedTeamIds) {}

    public record UserSaveRequest(@NotBlank @Size(max = 80) String username, @NotBlank @Size(max = 150) String fullName,
                                  @Email String email, boolean active, boolean appraised, @NotEmpty Set<Role> roles,
                                  Long teamId, Long levelId, Set<Long> managedTeamIds, @Size(min = 8) String password) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 8) String password) {}

    // ---------- cycles ----------
    public record CycleDto(Long id, String name, CycleStatus status, Instant createdAt, Instant openedAt, Instant closedAt,
                           Instant snapshotAt, long appraisalCount, SettingsDto weights) {}

    public record CycleSaveRequest(@NotBlank @Size(max = 120) String name) {}

    public record SyncReport(boolean applied, int questionsAdded, int questionsUpdated, int questionsRemoved,
                             int answersRemoved, int categoriesAdded, int categoriesUpdated, int categoriesRemoved,
                             int levelsAdded, boolean settingsChanged, List<String> notes) {}

    /** teamId / levelId are optional; when given they update the person (open cycles follow the Users page). */
    public record ParticipantRequest(@NotNull Long userId, Long teamId, Long levelId) {}

    public record ParticipantDto(Long appraisalId, Long userId, String fullName, String teamName, String levelName,
                                 Map<ReviewerRole, Integer> submitted, Map<ReviewerRole, Integer> drafts) {}

    public record ReviewAdminDto(Long id, Long appraisalId, String employeeName, String teamName, String reviewerName,
                                 ReviewerRole role, ReviewStatus status, Instant updatedAt, Instant submittedAt) {}

    // ---------- reviewing ----------
    public record RoleTaskDto(ReviewerRole role, ReviewStatus status, Long reviewId) {}

    public record TaskDto(Long appraisalId, Long employeeId, String employeeName, String teamName, String levelName,
                          List<RoleTaskDto> roles, boolean canLead, boolean canViewResults) {}

    public record CycleTasksDto(CycleDto cycle, List<TaskDto> tasks, Long myAppraisalId) {}

    public record FormQuestionDto(Long id, int number, String text, String guidance, ReviewedBy reviewedBy,
                                  Integer value, boolean na, String comment) {}

    public record FormCategoryDto(Long id, String name, Block block, List<FormQuestionDto> questions) {}

    public record ReviewFormDto(Long appraisalId, Long reviewId, String employeeName, String teamName, String levelName,
                                ReviewerRole role, ReviewStatus status, CycleStatus cycleStatus, boolean editable,
                                boolean commentsRequiredForExtremes, List<FormCategoryDto> categories) {}

    public record AnswerInput(@NotNull Long questionId, @Min(1) @Max(10) Integer value, boolean na, @Size(max = 1000) String comment) {}

    public record SaveAnswersRequest(@NotNull List<@Valid AnswerInput> answers) {}

    // ---------- appraisal detail / results ----------
    public record IdName(Long id, String name) {}

    public record QuestionResultDto(Long id, int number, String text, ReviewedBy reviewedBy, double weight, boolean applicable,
                                    Map<ReviewerRole, Double> roleAverages, Map<ReviewerRole, Integer> roleCounts,
                                    Double score, List<String> leadComments) {}

    public record CategoryResultDto(Long id, String name, Block block, double points, Double score,
                                    List<QuestionResultDto> questions) {}

    public record ResultDto(String status, int leadAnswered, int needed, Double commonScore, Double roleScore,
                            Double finalScore, String rating, int selfGapCount, List<CategoryResultDto> categories) {}

    public record ReviewInfoDto(Long id, String reviewerName, ReviewerRole role, ReviewStatus status, Instant submittedAt) {}

    public record AchievementDto(Long id, @NotBlank @Size(max = 1000) String text, String situation, String businessImpact,
                                 boolean verified) {}

    public record GoalDto(Long id, @NotBlank @Size(max = 1000) String text, @Size(max = 1000) String successCriteria) {}

    public record AppraisalDetailDto(Long id, CycleDto cycle, IdName employee, String teamName, IdName level,
                                     List<IdName> levels, String leadComments, boolean canLead, boolean isSelf,
                                     boolean editable, boolean canViewResults, ResultDto result,
                                     List<ReviewInfoDto> reviews, List<AchievementDto> achievements, List<GoalDto> goals) {}

    public record SetLevelRequest(Long cycleLevelId) {}

    public record TextRequest(@Size(max = 10000) String text) {}

    public record MyAppraisalDto(Long appraisalId, CycleDto cycle, String teamName, String levelName,
                                 String status, Double finalScore, String rating) {}

    // ---------- summary ----------
    public record SummaryRowDto(Long appraisalId, String teamName, String employeeName, String levelName,
                                int leadAnswered, int needed, String status, Double commonScore, Double roleScore,
                                Double finalScore, String rating, long verifiedAchievements, long goals,
                                int selfGapCount, Map<ReviewerRole, Integer> submittedReviews) {}

    public record TeamSummaryDto(String teamName, int resources, int scored, Double average) {}

    public record SummaryDto(CycleDto cycle, List<TeamSummaryDto> teams, List<SummaryRowDto> rows) {}
}
