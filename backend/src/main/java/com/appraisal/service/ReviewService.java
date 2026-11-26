package com.appraisal.service;

import com.appraisal.config.ApiException;
import com.appraisal.domain.*;
import com.appraisal.domain.Enums.*;
import com.appraisal.dto.Dtos.*;
import com.appraisal.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class ReviewService {
    private final CycleService cycles;
    private final AppraisalRepo appraisals;
    private final ReviewRepo reviews;
    private final AccessService access;
    private final ScoreService scores;

    public ReviewService(CycleService cycles, AppraisalRepo appraisals, ReviewRepo reviews,
                         AccessService access, ScoreService scores) {
        this.cycles = cycles;
        this.appraisals = appraisals;
        this.reviews = reviews;
        this.access = access;
        this.scores = scores;
    }

    @Transactional(readOnly = true)
    public CycleTasksDto tasks(Long cycleId, AppUser me) {
        Cycle c = cycles.get(cycleId);
        if (c.getStatus() == CycleStatus.DRAFT) throw ApiException.bad("This cycle has not been opened yet");
        List<Appraisal> list = appraisals.findByCycleIdOrderByTeamNameAscEmployeeFullNameAsc(cycleId);
        Map<String, Review> mine = new HashMap<>();
        for (Review r : reviews.findByCycle(cycleId)) {
            if (r.getReviewer().getId().equals(me.getId())) mine.put(r.getAppraisal().getId() + ":" + r.getRole(), r);
        }
        List<TaskDto> tasks = new ArrayList<>();
        Long myAppraisal = null;
        for (Appraisal a : list) {
            if (access.isSelf(me, a)) myAppraisal = a.getId();
            List<ReviewerRole> roles = access.eligibleRoles(me, a);
            // keep roles of reviews already written even if access changed later
            for (ReviewerRole r : ReviewerRole.values()) if (mine.containsKey(a.getId() + ":" + r) && !roles.contains(r)) roles.add(r);
            boolean lead = access.canLead(me, a);
            if (roles.isEmpty() && !lead) continue;
            List<RoleTaskDto> rt = roles.stream().map(r -> {
                Review rv = mine.get(a.getId() + ":" + r);
                return new RoleTaskDto(r, rv == null ? null : rv.getStatus(), rv == null ? null : rv.getId());
            }).toList();
            tasks.add(new TaskDto(a.getId(), a.getEmployee().getId(), a.getEmployee().getFullName(), a.getTeamName(),
                    a.getLevel() == null ? null : a.getLevel().getName(), rt, lead, access.canViewResults(me, a)));
        }
        return new CycleTasksDto(Mapper.cycle(c, list.size()), tasks, myAppraisal);
    }

    private Appraisal appraisal(Long id) {
        return appraisals.findById(id).orElseThrow(() -> ApiException.notFound("Appraisal"));
    }

    private void checkEligible(AppUser me, Appraisal a, ReviewerRole role, Optional<Review> existing) {
        if (existing.isPresent()) return;
        if (!access.eligibleRoles(me, a).contains(role))
            throw ApiException.forbidden("You cannot review " + a.getEmployee().getFullName() + " as " + role);
    }

    /** Questions this reviewer role answers for this appraisal (applicable to the level, reviewer filter). */
    private LinkedHashMap<CycleQuestion, Integer> formQuestions(ScoreService.CycleContext ctx, Appraisal a, ReviewerRole role) {
        LinkedHashMap<CycleQuestion, Integer> out = new LinkedHashMap<>();
        Long levelId = a.getLevel() == null ? null : a.getLevel().getId();
        if (levelId == null) return out;
        Map<Long, CycleCategory> cats = new HashMap<>();
        ctx.categoriesFor(a.getTeamId()).forEach(c -> cats.put(c.getId(), c));
        ctx.numberedQuestionsFor(a.getTeamId()).forEach((q, n) -> {
            CycleCategory c = cats.get(q.getCategory().getId());
            var ecat = new ScoringEngine.Cat(c.getId(), c.getName(), c.getBlock(), c.getSortOrder(), c.getLevelPoints());
            var eq = new ScoringEngine.Q(q.getId(), c.getId(), q.getText(), q.getWeight(), q.getReviewedBy(), q.getExcludedLevelIds());
            if (!ScoringEngine.isApplicable(ecat, eq, levelId)) return;
            if (q.getReviewedBy() == ReviewedBy.TL_MANAGER_ONLY && (role == ReviewerRole.SELF || role == ReviewerRole.PEER)) return;
            out.put(q, n);
        });
        return out;
    }

    @Transactional(readOnly = true)
    public ReviewFormDto form(Long appraisalId, ReviewerRole role, AppUser me) {
        Appraisal a = appraisal(appraisalId);
        Optional<Review> rv = reviews.findByAppraisalIdAndReviewerIdAndRole(appraisalId, me.getId(), role);
        checkEligible(me, a, role, rv);
        var ctx = scores.context(a.getCycle());
        Map<Long, Answer> given = new HashMap<>();
        rv.ifPresent(r -> r.getAnswers().forEach(x -> given.put(x.getQuestion().getId(), x)));
        var qs = formQuestions(ctx, a, role);
        List<FormCategoryDto> cats = new ArrayList<>();
        for (CycleCategory c : ctx.categoriesFor(a.getTeamId())) {
            List<FormQuestionDto> fq = new ArrayList<>();
            qs.forEach((q, n) -> {
                if (!q.getCategory().getId().equals(c.getId())) return;
                Answer ans = given.get(q.getId());
                fq.add(new FormQuestionDto(q.getId(), n, q.getText(), q.getGuidance(), q.getReviewedBy(),
                        ans == null ? null : ans.getValue(), ans != null && ans.isNa(), ans == null ? null : ans.getComment()));
            });
            if (!fq.isEmpty()) cats.add(new FormCategoryDto(c.getId(), c.getName(), c.getBlock(), fq));
        }
        boolean editable = a.getCycle().getStatus() == CycleStatus.OPEN && a.getLevel() != null
                && rv.map(r -> r.getStatus() == ReviewStatus.DRAFT).orElse(true);
        return new ReviewFormDto(a.getId(), rv.map(Review::getId).orElse(null), a.getEmployee().getFullName(), a.getTeamName(),
                a.getLevel() == null ? null : a.getLevel().getName(), role, rv.map(Review::getStatus).orElse(null),
                a.getCycle().getStatus(), editable, isLead(role), cats);
    }

    private static boolean isLead(ReviewerRole r) {
        return r == ReviewerRole.TL || r == ReviewerRole.MANAGER;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ReviewFormDto save(Long appraisalId, ReviewerRole role, AppUser me, SaveAnswersRequest req, boolean submit) {
        Appraisal a = appraisal(appraisalId);
        Optional<Review> existing = reviews.findByAppraisalIdAndReviewerIdAndRole(appraisalId, me.getId(), role);
        checkEligible(me, a, role, existing);
        if (a.getCycle().getStatus() != CycleStatus.OPEN) throw ApiException.bad("The cycle is not open");
        if (a.getLevel() == null) throw ApiException.bad("The employee's level is not set yet. Ask the TL or Manager to set it.");
        if (existing.isPresent() && existing.get().getStatus() == ReviewStatus.SUBMITTED)
            throw ApiException.bad("This review was already submitted. Ask an admin to reopen it.");

        var ctx = scores.context(a.getCycle());
        var allowed = formQuestions(ctx, a, role);
        Map<Long, CycleQuestion> allowedById = new HashMap<>();
        allowed.keySet().forEach(q -> allowedById.put(q.getId(), q));

        Review r = existing.orElseGet(() -> {
            Review n = new Review();
            n.setAppraisal(a);
            n.setReviewer(me);
            n.setRole(role);
            return n;
        });
        Map<Long, Answer> byQ = new HashMap<>();
        r.getAnswers().forEach(x -> byQ.put(x.getQuestion().getId(), x));
        for (AnswerInput in : req.answers()) {
            CycleQuestion q = allowedById.get(in.questionId());
            if (q == null) continue; // question no longer part of this form
            Answer ans = byQ.get(q.getId());
            boolean empty = !in.na() && in.value() == null && (in.comment() == null || in.comment().isBlank());
            if (empty) {
                if (ans != null) { r.getAnswers().remove(ans); byQ.remove(q.getId()); }
                continue;
            }
            if (ans == null) {
                ans = new Answer();
                ans.setReview(r);
                ans.setQuestion(q);
                r.getAnswers().add(ans);
                byQ.put(q.getId(), ans);
            }
            ans.setNa(in.na());
            ans.setValue(in.na() ? null : in.value());
            ans.setComment(in.comment() == null || in.comment().isBlank() ? null : in.comment().trim());
        }
        r.setUpdatedAt(Instant.now());

        if (submit) {
            List<String> problems = new ArrayList<>();
            allowed.forEach((q, n) -> {
                Answer ans = byQ.get(q.getId());
                if (ans == null || (!ans.isNa() && ans.getValue() == null)) {
                    problems.add("Q" + n + " needs a number or N/A");
                } else if (isLead(role) && ans.getValue() != null && (ans.getValue() <= 2 || ans.getValue() >= 9)
                        && (ans.getComment() == null || ans.getComment().isBlank())) {
                    problems.add("Q" + n + " (" + ans.getValue() + ") needs a comment with a real example");
                }
            });
            if (!problems.isEmpty()) {
                reviews.save(r); // keep the draft
                throw ApiException.bad("Cannot submit yet: " + String.join("; ", problems.subList(0, Math.min(8, problems.size())))
                        + (problems.size() > 8 ? " (+" + (problems.size() - 8) + " more)" : ""));
            }
            r.setStatus(ReviewStatus.SUBMITTED);
            r.setSubmittedAt(Instant.now());
        }
        reviews.save(r);
        return form(appraisalId, role, me);
    }
}
