package com.appraisal.service;

import com.appraisal.domain.Enums.Block;
import com.appraisal.domain.Enums.ReviewedBy;
import com.appraisal.domain.Enums.ReviewerRole;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pure scoring logic, a 1:1 port of the CA team sheet formulas:
 *
 * <ol>
 *   <li>Question score = weighted average of the Self / Peer / TL / Manager numbers given
 *       (default 10/20/30/40). Blank or N/A reviews are skipped and the rest re-weighted.
 *       When several people answered in the same role, their numbers are averaged first.</li>
 *   <li>A question applies to a level when its category has &gt; 0 points for that level
 *       (and the question is not switched off for that level).</li>
 *   <li>Category score = weighted average (question weights) of the question scores.</li>
 *   <li>Block score = category scores weighted by the category points for the employee's level
 *       (for a single role-specific category this equals the sheet's plain weighted average).</li>
 *   <li>Final = (commonWeight x Common + roleWeight x Role) / (commonWeight + roleWeight), rounded to 2 dp.
 *       Shown only when a level is set and a TL or the Manager answered (number or N/A) every applicable question.</li>
 *   <li>Rating = the band with the highest "from score" that is &lt;= final.</li>
 * </ol>
 */
public final class ScoringEngine {

    private ScoringEngine() {}

    public record Weights(double common, double role, double self, double peer, double tl, double manager) {
        double reviewer(ReviewerRole r) {
            return switch (r) {
                case SELF -> self;
                case PEER -> peer;
                case TL -> tl;
                case MANAGER -> manager;
            };
        }
    }

    public record Cat(long id, String name, Block block, int sortOrder, Map<Long, Double> levelPoints) {}

    public record Q(long id, long categoryId, String text, double weight, ReviewedBy reviewedBy, Set<Long> excludedLevelIds) {}

    /** One submitted answer. value == null means N/A. */
    public record A(long questionId, ReviewerRole role, Integer value) {}

    public record Band(String label, double minScore) {}

    public enum Status {
        /** No level chosen and nothing answered by TL / Manager. */
        NOT_STARTED,
        SELECT_LEVEL,
        INCOMPLETE,
        CHECK_ANSWERS,
        SCORED
    }

    public record QuestionScore(long questionId, long categoryId, boolean applicable,
                                Map<ReviewerRole, Double> roleAverages, Map<ReviewerRole, Integer> roleCounts,
                                boolean answeredByLead, Double score) {}

    public record CategoryScore(long categoryId, String name, Block block, double points, Double score) {}

    public record Result(Status status, int leadAnswered, int needed,
                         List<CategoryScore> categories, Double commonScore, Double roleScore,
                         Double finalScore, String rating, List<QuestionScore> questions,
                         int selfGapCount) {}

    public static Result compute(Weights w, Long levelId, List<Cat> cats, List<Q> questions, List<A> answers, List<Band> bands) {
        Map<Long, Cat> catById = new HashMap<>();
        cats.forEach(c -> catById.put(c.id(), c));

        // group answers: question -> role -> numbers / any-answer flag
        Map<Long, Map<ReviewerRole, List<Integer>>> numbers = new HashMap<>();
        Map<Long, EnumSet<ReviewerRole>> answeredRoles = new HashMap<>();
        for (A a : answers) {
            answeredRoles.computeIfAbsent(a.questionId(), k -> EnumSet.noneOf(ReviewerRole.class)).add(a.role());
            if (a.value() != null) {
                numbers.computeIfAbsent(a.questionId(), k -> new EnumMap<>(ReviewerRole.class))
                        .computeIfAbsent(a.role(), k -> new ArrayList<>()).add(a.value());
            }
        }

        List<QuestionScore> qScores = new ArrayList<>();
        int needed = 0, leadAnswered = 0, selfGap = 0;
        for (Q q : questions) {
            Cat c = catById.get(q.categoryId());
            boolean applicable = isApplicable(c, q, levelId);
            Map<ReviewerRole, Double> avgs = new EnumMap<>(ReviewerRole.class);
            Map<ReviewerRole, Integer> counts = new EnumMap<>(ReviewerRole.class);
            Map<ReviewerRole, List<Integer>> byRole = numbers.getOrDefault(q.id(), Map.of());
            for (var e : byRole.entrySet()) {
                if (q.reviewedBy() == ReviewedBy.TL_MANAGER_ONLY
                        && (e.getKey() == ReviewerRole.SELF || e.getKey() == ReviewerRole.PEER)) continue;
                avgs.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0));
                counts.put(e.getKey(), e.getValue().size());
            }
            EnumSet<ReviewerRole> roles = answeredRoles.getOrDefault(q.id(), EnumSet.noneOf(ReviewerRole.class));
            boolean lead = roles.contains(ReviewerRole.TL) || roles.contains(ReviewerRole.MANAGER);

            Double score = null;
            if (applicable) {
                needed++;
                if (lead) leadAnswered++;
                double num = 0, den = 0;
                for (var e : avgs.entrySet()) {
                    double rw = w.reviewer(e.getKey());
                    num += e.getValue() * rw;
                    den += rw;
                }
                if (den > 0) score = num / den;

                Double self = avgs.get(ReviewerRole.SELF);
                Double leadNum = avgs.containsKey(ReviewerRole.MANAGER) ? avgs.get(ReviewerRole.MANAGER) : avgs.get(ReviewerRole.TL);
                if (self != null && leadNum != null && Math.abs(self - leadNum) >= 3) selfGap++;
            }
            qScores.add(new QuestionScore(q.id(), q.categoryId(), applicable, avgs, counts, lead, score));
        }

        // category scores
        List<CategoryScore> catScores = new ArrayList<>();
        Map<Long, List<QuestionScore>> qByCat = new HashMap<>();
        Map<Long, Q> qById = new HashMap<>();
        questions.forEach(q -> qById.put(q.id(), q));
        qScores.forEach(qs -> qByCat.computeIfAbsent(qs.categoryId(), k -> new ArrayList<>()).add(qs));
        List<Cat> sortedCats = new ArrayList<>(cats);
        sortedCats.sort(Comparator.comparing(Cat::block).thenComparingInt(Cat::sortOrder));
        for (Cat c : sortedCats) {
            double num = 0, den = 0;
            for (QuestionScore qs : qByCat.getOrDefault(c.id(), List.of())) {
                if (qs.score() == null) continue;
                double qw = qById.get(qs.questionId()).weight();
                num += qw * qs.score();
                den += qw;
            }
            Double s = den > 0 ? num / den : null;
            catScores.add(new CategoryScore(c.id(), c.name(), c.block(), points(c, levelId), s));
        }

        Double common = blockScore(catScores, Block.COMMON);
        Double role = blockScore(catScores, Block.ROLE);

        Status status;
        Double fin = null;
        String rating = null;
        if (levelId == null) {
            status = leadAnswered == 0 ? Status.NOT_STARTED : Status.SELECT_LEVEL;
        } else if (leadAnswered < needed || needed == 0) {
            status = Status.INCOMPLETE;
        } else {
            // A block with no score (e.g. a team with no role-specific questions) is left out
            // and the other block carries the full weight.
            double num = 0, den = 0;
            if (common != null) { num += w.common() * common; den += w.common(); }
            if (role != null) { num += w.role() * role; den += w.role(); }
            if (den > 0) {
                fin = round2(num / den);
                rating = rate(fin, bands);
                status = Status.SCORED;
            } else {
                status = Status.CHECK_ANSWERS;
            }
        }
        return new Result(status, leadAnswered, needed, catScores, round2n(common), round2n(role), fin, rating, qScores, selfGap);
    }

    public static boolean isApplicable(Cat c, Q q, Long levelId) {
        if (c == null) return false;
        if (levelId == null) return true; // no level yet: show everything, like the sheet's IFERROR(...,1)
        if (q.excludedLevelIds() != null && q.excludedLevelIds().contains(levelId)) return false;
        return points(c, levelId) > 0;
    }

    private static double points(Cat c, Long levelId) {
        if (levelId == null) return 0;
        Double p = c.levelPoints().get(levelId);
        return p == null ? 0 : p;
    }

    private static Double blockScore(List<CategoryScore> cats, Block block) {
        double num = 0, den = 0;
        for (CategoryScore c : cats) {
            if (c.block() != block || c.score() == null || c.points() <= 0) continue;
            num += c.points() * c.score();
            den += c.points();
        }
        return den > 0 ? num / den : null;
    }

    public static String rate(double score, List<Band> bands) {
        String label = null;
        double best = Double.NEGATIVE_INFINITY;
        for (Band b : bands) {
            if (score >= b.minScore() && b.minScore() >= best) {
                best = b.minScore();
                label = b.label();
            }
        }
        return label;
    }

    static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    static Double round2n(Double v) {
        return v == null ? null : round2(v);
    }
}
