package com.appraisal.service;

import com.appraisal.domain.*;
import com.appraisal.domain.Enums.Block;
import com.appraisal.domain.Enums.ReviewStatus;
import com.appraisal.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Feeds a cycle's frozen questions / weights and the submitted answers into {@link ScoringEngine}. */
@Service
@Transactional(readOnly = true)
public class ScoreService {
    private final CycleCategoryRepo cycleCategories;
    private final CycleQuestionRepo cycleQuestions;
    private final CycleRatingBandRepo cycleBands;
    private final ReviewRepo reviews;
    private final AnswerRepo answers;

    public ScoreService(CycleCategoryRepo cycleCategories, CycleQuestionRepo cycleQuestions, CycleRatingBandRepo cycleBands,
                        ReviewRepo reviews, AnswerRepo answers) {
        this.cycleCategories = cycleCategories;
        this.cycleQuestions = cycleQuestions;
        this.cycleBands = cycleBands;
        this.reviews = reviews;
        this.answers = answers;
    }

    /** Everything about one cycle needed to score / display its appraisals. */
    public static final class CycleContext {
        public final Cycle cycle;
        public final ScoringEngine.Weights weights;
        public final List<CycleCategory> categories;
        public final List<CycleQuestion> questions;
        public final List<ScoringEngine.Band> bands;

        CycleContext(Cycle cycle, List<CycleCategory> categories, List<CycleQuestion> questions, List<ScoringEngine.Band> bands) {
            this.cycle = cycle;
            this.weights = new ScoringEngine.Weights(cycle.getCommonWeight(), cycle.getRoleWeight(), cycle.getSelfWeight(),
                    cycle.getPeerWeight(), cycle.getTlWeight(), cycle.getManagerWeight());
            this.categories = categories;
            this.questions = questions;
            this.bands = bands;
        }

        /** Common categories followed by the team's role categories, in display order. */
        public List<CycleCategory> categoriesFor(Long teamId) {
            List<CycleCategory> out = new ArrayList<>();
            categories.stream().filter(c -> c.getBlock() == Block.COMMON).sorted(Comparator.comparingInt(CycleCategory::getSortOrder)).forEach(out::add);
            categories.stream().filter(c -> c.getBlock() == Block.ROLE && Objects.equals(c.getTeamId(), teamId))
                    .sorted(Comparator.comparingInt(CycleCategory::getSortOrder)).forEach(out::add);
            return out;
        }

        /** Questions for the team in display order, with their 1-based display numbers. */
        public LinkedHashMap<CycleQuestion, Integer> numberedQuestionsFor(Long teamId) {
            LinkedHashMap<CycleQuestion, Integer> out = new LinkedHashMap<>();
            int n = 1;
            for (CycleCategory c : categoriesFor(teamId)) {
                List<CycleQuestion> qs = questions.stream().filter(q -> q.getCategory().getId().equals(c.getId()))
                        .sorted(Comparator.comparingInt(CycleQuestion::getSortOrder).thenComparing(CycleQuestion::getId)).toList();
                for (CycleQuestion q : qs) out.put(q, n++);
            }
            return out;
        }
    }

    public CycleContext context(Cycle cycle) {
        List<ScoringEngine.Band> bands = cycleBands.findByCycleIdOrderByMinScoreAsc(cycle.getId()).stream()
                .map(b -> new ScoringEngine.Band(b.getLabel(), b.getMinScore())).toList();
        return new CycleContext(cycle, cycleCategories.findByCycleIdOrderBySortOrderAsc(cycle.getId()),
                cycleQuestions.findByCycle(cycle.getId()), bands);
    }

    /** Submitted answers for the given appraisals, grouped by appraisal id. */
    public Map<Long, List<ScoringEngine.A>> submittedAnswers(Collection<Long> appraisalIds) {
        Map<Long, List<ScoringEngine.A>> out = new HashMap<>();
        if (appraisalIds.isEmpty()) return out;
        List<Review> submitted = reviews.findByAppraisalIds(appraisalIds).stream()
                .filter(r -> r.getStatus() == ReviewStatus.SUBMITTED).toList();
        Map<Long, Review> byId = new HashMap<>();
        submitted.forEach(r -> byId.put(r.getId(), r));
        if (byId.isEmpty()) return out;
        for (Answer a : answers.findByReviewIds(byId.keySet())) {
            if (a.getValue() == null && !a.isNa()) continue; // unanswered
            Review r = byId.get(a.getReview().getId());
            out.computeIfAbsent(r.getAppraisal().getId(), k -> new ArrayList<>())
                    .add(new ScoringEngine.A(a.getQuestion().getId(), r.getRole(), a.isNa() ? null : a.getValue()));
        }
        return out;
    }

    public ScoringEngine.Result score(CycleContext ctx, Appraisal appraisal, List<ScoringEngine.A> answers) {
        List<CycleCategory> cats = ctx.categoriesFor(appraisal.getTeamId());
        Set<Long> catIds = new HashSet<>();
        cats.forEach(c -> catIds.add(c.getId()));
        List<ScoringEngine.Cat> ecats = cats.stream()
                .map(c -> new ScoringEngine.Cat(c.getId(), c.getName(), c.getBlock(), c.getSortOrder(), c.getLevelPoints())).toList();
        List<ScoringEngine.Q> eqs = ctx.numberedQuestionsFor(appraisal.getTeamId()).keySet().stream()
                .filter(q -> catIds.contains(q.getCategory().getId()))
                .map(q -> new ScoringEngine.Q(q.getId(), q.getCategory().getId(), q.getText(), q.getWeight(), q.getReviewedBy(), q.getExcludedLevelIds()))
                .toList();
        Long levelId = appraisal.getLevel() == null ? null : appraisal.getLevel().getId();
        return ScoringEngine.compute(ctx.weights, levelId, ecats, eqs, answers == null ? List.of() : answers, ctx.bands);
    }
}
