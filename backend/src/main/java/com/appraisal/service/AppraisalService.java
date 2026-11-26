package com.appraisal.service;

import com.appraisal.config.ApiException;
import com.appraisal.domain.*;
import com.appraisal.domain.Enums.*;
import com.appraisal.dto.Dtos.*;
import com.appraisal.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class AppraisalService {
    private final AppraisalRepo appraisals;
    private final CycleRepo cycles;
    private final CycleLevelRepo cycleLevels;
    private final ReviewRepo reviews;
    private final AnswerRepo answers;
    private final AchievementRepo achievements;
    private final GoalRepo goals;
    private final AccessService access;
    private final ScoreService scores;

    public AppraisalService(AppraisalRepo appraisals, CycleRepo cycles, CycleLevelRepo cycleLevels, ReviewRepo reviews,
                            AnswerRepo answers, AchievementRepo achievements, GoalRepo goals, AccessService access, ScoreService scores) {
        this.appraisals = appraisals;
        this.cycles = cycles;
        this.cycleLevels = cycleLevels;
        this.reviews = reviews;
        this.answers = answers;
        this.achievements = achievements;
        this.goals = goals;
        this.access = access;
        this.scores = scores;
    }

    private Appraisal get(Long id) {
        return appraisals.findById(id).orElseThrow(() -> ApiException.notFound("Appraisal"));
    }

    private Appraisal viewable(Long id, AppUser me) {
        Appraisal a = get(id);
        if (!access.canView(me, a)) throw ApiException.forbidden("You cannot open this appraisal");
        return a;
    }

    private Appraisal leadable(Long id, AppUser me) {
        Appraisal a = get(id);
        if (!access.canLead(me, a)) throw ApiException.forbidden("Only the team's TL / Manager or an admin can do this");
        requireOpen(a);
        return a;
    }

    private static void requireOpen(Appraisal a) {
        if (a.getCycle().getStatus() != CycleStatus.OPEN) throw ApiException.bad("The cycle is not open");
    }

    // ------------------------------------------------------------------ detail

    @Transactional(readOnly = true)
    public AppraisalDetailDto detail(Long id, AppUser me) {
        Appraisal a = viewable(id, me);
        boolean lead = access.canLead(me, a);
        boolean results = access.canViewResults(me, a);
        ResultDto result = null;
        if (results && a.getCycle().getSnapshotAt() != null) {
            var ctx = scores.context(a.getCycle());
            var r = scores.score(ctx, a, scores.submittedAnswers(List.of(a.getId())).get(a.getId()));
            result = toResult(ctx, a, r, lead);
        }
        List<ReviewInfoDto> rv = lead ? reviews.findByAppraisalId(id).stream()
                .sorted(Comparator.comparing(Review::getRole))
                .map(r -> new ReviewInfoDto(r.getId(), r.getReviewer().getFullName(), r.getRole(), r.getStatus(), r.getSubmittedAt()))
                .toList() : List.of();
        List<IdName> levels = cycleLevels.findByCycleIdOrderBySortOrderAsc(a.getCycle().getId()).stream()
                .map(l -> new IdName(l.getId(), l.getName())).toList();
        return new AppraisalDetailDto(a.getId(), Mapper.cycle(a.getCycle(), 0),
                new IdName(a.getEmployee().getId(), a.getEmployee().getFullName()), a.getTeamName(),
                a.getLevel() == null ? null : new IdName(a.getLevel().getId(), a.getLevel().getName()), levels,
                lead || results ? a.getLeadComments() : null, lead, access.isSelf(me, a),
                a.getCycle().getStatus() == CycleStatus.OPEN, results, result, rv,
                achievements.findByAppraisalIdOrderBySortOrderAscIdAsc(id).stream().map(Mapper::achievement).toList(),
                goals.findByAppraisalIdOrderBySortOrderAscIdAsc(id).stream().map(Mapper::goal).toList());
    }

    private ResultDto toResult(ScoreService.CycleContext ctx, Appraisal a, ScoringEngine.Result r, boolean includeComments) {
        Map<Long, ScoringEngine.QuestionScore> qs = new HashMap<>();
        r.questions().forEach(q -> qs.put(q.questionId(), q));
        Map<Long, List<String>> comments = new HashMap<>();
        if (includeComments) {
            List<Review> submitted = reviews.findByAppraisalId(a.getId()).stream()
                    .filter(rv -> rv.getStatus() == ReviewStatus.SUBMITTED).toList();
            Map<Long, Review> byId = new HashMap<>();
            submitted.forEach(rv -> byId.put(rv.getId(), rv));
            if (!byId.isEmpty()) {
                for (Answer ans : answers.findByReviewIds(byId.keySet())) {
                    if (ans.getComment() == null) continue;
                    Review rv = byId.get(ans.getReview().getId());
                    comments.computeIfAbsent(ans.getQuestion().getId(), k -> new ArrayList<>())
                            .add(rv.getRole() + " (" + rv.getReviewer().getFullName() + "): " + ans.getComment());
                }
            }
        }
        var numbered = ctx.numberedQuestionsFor(a.getTeamId());
        List<CategoryResultDto> cats = new ArrayList<>();
        for (ScoringEngine.CategoryScore cs : r.categories()) {
            List<QuestionResultDto> qr = new ArrayList<>();
            numbered.forEach((q, n) -> {
                if (!q.getCategory().getId().equals(cs.categoryId())) return;
                var s = qs.get(q.getId());
                qr.add(new QuestionResultDto(q.getId(), n, q.getText(), q.getReviewedBy(), q.getWeight(), s.applicable(),
                        s.roleAverages(), s.roleCounts(), s.score() == null ? null : ScoringEngine.round2(s.score()),
                        comments.getOrDefault(q.getId(), List.of())));
            });
            cats.add(new CategoryResultDto(cs.categoryId(), cs.name(), cs.block(), cs.points(),
                    ScoringEngine.round2n(cs.score()), qr));
        }
        return new ResultDto(r.status().name(), r.leadAnswered(), r.needed(), r.commonScore(), r.roleScore(),
                r.finalScore(), r.rating(), r.selfGapCount(), cats);
    }

    @Transactional(readOnly = true)
    public List<MyAppraisalDto> mine(AppUser me) {
        List<MyAppraisalDto> out = new ArrayList<>();
        for (Cycle c : cycles.findByStatusInOrderByCreatedAtDesc(List.of(CycleStatus.OPEN, CycleStatus.CLOSED))) {
            appraisals.findByCycleIdAndEmployeeId(c.getId(), me.getId()).ifPresent(a -> {
                String status = null;
                Double fin = null;
                String rating = null;
                if (c.getStatus() == CycleStatus.CLOSED) {
                    var r = scores.score(scores.context(c), a, scores.submittedAnswers(List.of(a.getId())).get(a.getId()));
                    status = r.status().name();
                    fin = r.finalScore();
                    rating = r.rating();
                }
                out.add(new MyAppraisalDto(a.getId(), Mapper.cycle(c, 0), a.getTeamName(),
                        a.getLevel() == null ? null : a.getLevel().getName(), status, fin, rating));
            });
        }
        return out;
    }

    // ------------------------------------------------------------------ lead actions

    public void setLevel(Long id, Long cycleLevelId, AppUser me) {
        Appraisal a = leadable(id, me);
        if (cycleLevelId == null) {
            a.setLevel(null);
            return;
        }
        CycleLevel l = cycleLevels.findById(cycleLevelId).filter(x -> x.getCycle().getId().equals(a.getCycle().getId()))
                .orElseThrow(() -> ApiException.notFound("Level"));
        a.setLevel(l);
    }

    public void setLeadComments(Long id, String text, AppUser me) {
        Appraisal a = leadable(id, me);
        a.setLeadComments(text == null || text.isBlank() ? null : text.trim());
    }

    // ------------------------------------------------------------------ achievements and goals

    private Appraisal editableByOwnerOrLead(Long appraisalId, AppUser me) {
        Appraisal a = viewable(appraisalId, me);
        requireOpen(a);
        return a;
    }

    public AchievementDto addAchievement(Long appraisalId, AchievementDto dto, AppUser me) {
        Appraisal a = editableByOwnerOrLead(appraisalId, me);
        Achievement x = new Achievement();
        x.setAppraisal(a);
        x.setSortOrder(achievements.findByAppraisalIdOrderBySortOrderAscIdAsc(appraisalId).size() + 1);
        apply(x, dto, access.canLead(me, a));
        return Mapper.achievement(achievements.save(x));
    }

    public AchievementDto updateAchievement(Long id, AchievementDto dto, AppUser me) {
        Achievement x = achievements.findById(id).orElseThrow(() -> ApiException.notFound("Achievement"));
        Appraisal a = editableByOwnerOrLead(x.getAppraisal().getId(), me);
        apply(x, dto, access.canLead(me, a));
        return Mapper.achievement(x);
    }

    private void apply(Achievement x, AchievementDto dto, boolean lead) {
        boolean changed = !Objects.equals(x.getText(), dto.text().trim()) || !Objects.equals(x.getSituation(), blank(dto.situation()))
                || !Objects.equals(x.getBusinessImpact(), blank(dto.businessImpact()));
        x.setText(dto.text().trim());
        x.setSituation(blank(dto.situation()));
        x.setBusinessImpact(blank(dto.businessImpact()));
        if (lead) x.setVerified(dto.verified());
        else if (changed) x.setVerified(false); // employee edits need re-verification
    }

    public void deleteAchievement(Long id, AppUser me) {
        Achievement x = achievements.findById(id).orElseThrow(() -> ApiException.notFound("Achievement"));
        Appraisal a = editableByOwnerOrLead(x.getAppraisal().getId(), me);
        if (x.isVerified() && !access.canLead(me, a)) throw ApiException.forbidden("A verified achievement can only be removed by the lead");
        achievements.delete(x);
    }

    public GoalDto addGoal(Long appraisalId, GoalDto dto, AppUser me) {
        Appraisal a = editableByOwnerOrLead(appraisalId, me);
        Goal g = new Goal();
        g.setAppraisal(a);
        g.setSortOrder(goals.findByAppraisalIdOrderBySortOrderAscIdAsc(appraisalId).size() + 1);
        g.setText(dto.text().trim());
        g.setSuccessCriteria(blank(dto.successCriteria()));
        return Mapper.goal(goals.save(g));
    }

    public GoalDto updateGoal(Long id, GoalDto dto, AppUser me) {
        Goal g = goals.findById(id).orElseThrow(() -> ApiException.notFound("Goal"));
        editableByOwnerOrLead(g.getAppraisal().getId(), me);
        g.setText(dto.text().trim());
        g.setSuccessCriteria(blank(dto.successCriteria()));
        return Mapper.goal(g);
    }

    public void deleteGoal(Long id, AppUser me) {
        Goal g = goals.findById(id).orElseThrow(() -> ApiException.notFound("Goal"));
        editableByOwnerOrLead(g.getAppraisal().getId(), me);
        goals.delete(g);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ------------------------------------------------------------------ summary

    @Transactional(readOnly = true)
    public SummaryDto summary(Long cycleId, AppUser me) {
        Cycle c = cycles.findById(cycleId).orElseThrow(() -> ApiException.notFound("Cycle"));
        if (c.getSnapshotAt() == null) throw ApiException.bad("This cycle has not been opened yet");
        boolean admin = me.hasRole(Role.ADMIN);
        if (!admin && !me.hasRole(Role.TL) && !me.hasRole(Role.MANAGER)) throw ApiException.forbidden("Only leads and admins can see the summary");
        List<Appraisal> list = appraisals.findByCycleIdOrderByTeamNameAscEmployeeFullNameAsc(cycleId).stream()
                .filter(a -> access.canLead(me, a)).toList();
        var ctx = scores.context(c);
        var ans = scores.submittedAnswers(list.stream().map(Appraisal::getId).toList());
        Map<Long, Map<ReviewerRole, Integer>> submittedCounts = new HashMap<>();
        if (!list.isEmpty()) {
            reviews.findByAppraisalIds(list.stream().map(Appraisal::getId).toList()).stream()
                    .filter(r -> r.getStatus() == ReviewStatus.SUBMITTED)
                    .forEach(r -> submittedCounts.computeIfAbsent(r.getAppraisal().getId(), k -> new EnumMap<>(ReviewerRole.class))
                            .merge(r.getRole(), 1, Integer::sum));
        }
        List<SummaryRowDto> rows = new ArrayList<>();
        Map<String, List<Double>> teamScores = new LinkedHashMap<>();
        Map<String, Integer> teamCounts = new LinkedHashMap<>();
        for (Appraisal a : list) {
            var r = scores.score(ctx, a, ans.get(a.getId()));
            rows.add(new SummaryRowDto(a.getId(), a.getTeamName(), a.getEmployee().getFullName(),
                    a.getLevel() == null ? null : a.getLevel().getName(), r.leadAnswered(), r.needed(), r.status().name(),
                    r.commonScore(), r.roleScore(), r.finalScore(), r.rating(),
                    achievements.countByAppraisalIdAndVerifiedTrue(a.getId()), goals.countByAppraisalId(a.getId()),
                    r.selfGapCount(), submittedCounts.getOrDefault(a.getId(), Map.of())));
            teamCounts.merge(a.getTeamName(), 1, Integer::sum);
            List<Double> ts = teamScores.computeIfAbsent(a.getTeamName(), k -> new ArrayList<>());
            if (r.finalScore() != null) ts.add(r.finalScore());
        }
        List<TeamSummaryDto> teams = teamCounts.entrySet().stream().map(e -> {
            List<Double> ts = teamScores.get(e.getKey());
            Double avg = ts.isEmpty() ? null : ScoringEngine.round2(ts.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            return new TeamSummaryDto(e.getKey(), e.getValue(), ts.size(), avg);
        }).toList();
        return new SummaryDto(Mapper.cycle(c, list.size()), teams, rows);
    }
}
