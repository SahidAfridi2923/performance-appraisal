package com.appraisal.service;

import com.appraisal.domain.Enums.Block;
import com.appraisal.domain.Enums.ReviewedBy;
import com.appraisal.service.ScoringEngine.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.appraisal.domain.Enums.ReviewerRole.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the engine against the CA sheet of Performance_Appraisal_All_Teams.xlsx.
 * Levels: 1 = Junior, 2 = Senior, 3 = Team Lead.
 */
class ScoringEngineTest {
    static final long JR = 1, SR = 2, TL_LEVEL = 3;
    static final Weights W = new Weights(0.4, 0.6, 0.1, 0.2, 0.3, 0.4);
    static final List<Band> BANDS = List.of(new Band("Unsatisfactory", 0), new Band("Needs Improvement", 3),
            new Band("Meets Expectations", 5), new Band("Exceeds Expectations", 7), new Band("Outstanding", 9));

    // CA sheet: category points by level (Scoring_Rules B20:D26) and question weights (column D)
    static final Object[][] COMMON = {
            {"Policies", new double[]{4, 4, 4}, new double[]{1, 1, 1, 1}},
            {"Communication", new double[]{3, 3, 3}, new double[]{1, 1, 1}},
            {"Leave", new double[]{3, 3, 3}, new double[]{0.5, 0.5, 1, 1, 1}},
            {"Discipline", new double[]{2, 2, 2}, new double[]{1, 1, 1}},
            {"Team level", new double[]{6, 7, 6}, new double[]{0.34, 0.33, 0.33, 0.5, 0.5, 1, 1, 0.5, 0.5}},
            {"Team lead level", new double[]{0, 0, 14}, new double[]{0.34, 0.33, 0.33, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1}},
            {"Individual", new double[]{22, 21, 8}, new double[]{0.34, 0.33, 0.33, 0.5, 0.5, 1, 1, 1, 0.5, 0.5, 1, 1, 1, 1, 1, 1, 1}},
    };
    static final double[] ROLE = {0.75, 0.67, 0.58, 1, 1, 1, 1, 1, 1, 1, 1};

    final List<Cat> cats = new ArrayList<>();
    final List<Q> qs = new ArrayList<>();
    final List<Long> commonQ = new ArrayList<>(), roleQ = new ArrayList<>(), leadLevelQ = new ArrayList<>();

    ScoringEngineTest() {
        long cid = 1, qid = 1;
        for (Object[] c : COMMON) {
            double[] p = (double[]) c[1];
            cats.add(new Cat(cid, (String) c[0], Block.COMMON, (int) cid, Map.of(JR, p[0], SR, p[1], TL_LEVEL, p[2])));
            for (double w : (double[]) c[2]) {
                qs.add(new Q(qid, cid, "q" + qid, w, ReviewedBy.ALL, Set.of()));
                commonQ.add(qid);
                if (c[0].equals("Team lead level")) leadLevelQ.add(qid);
                qid++;
            }
            cid++;
        }
        cats.add(new Cat(cid, "Role-specific", Block.ROLE, 1, Map.of(JR, 1.0, SR, 1.0, TL_LEVEL, 1.0)));
        for (double w : ROLE) {
            qs.add(new Q(qid, cid, "q" + qid, w, ReviewedBy.ALL, Set.of()));
            roleQ.add(qid++);
        }
    }

    static void answerAll(List<A> out, List<Long> ids, int self, int peer, int tl, int mgr) {
        for (long id : ids) {
            out.add(new A(id, SELF, self));
            out.add(new A(id, PEER, peer));
            out.add(new A(id, TL, tl));
            out.add(new A(id, MANAGER, mgr));
        }
    }

    @Test
    void matchesCaSheetSampleResource1() {
        // Resource 1 in the CA sheet: Senior, common answers 9/8/8/7, role answers 8/8/7/7.
        List<A> ans = new ArrayList<>();
        answerAll(ans, commonQ.stream().filter(id -> !leadLevelQ.contains(id)).toList(), 9, 8, 8, 7);
        answerAll(ans, roleQ, 8, 8, 7, 7);

        Result r = ScoringEngine.compute(W, SR, cats, qs, ans, BANDS);

        assertEquals(Status.SCORED, r.status());
        assertEquals(52, r.needed());          // CA!L83
        assertEquals(52, r.leadAnswered());    // CA!K83
        assertEquals(7.7, r.commonScore(), 1e-9);   // CA!H91
        assertEquals(7.3, r.roleScore(), 1e-9);     // CA!H92
        assertEquals(7.46, r.finalScore(), 1e-9);   // CA!H93
        assertEquals("Exceeds Expectations", r.rating()); // CA!H94
        // Team lead level does not apply to a Senior
        assertNull(r.categories().stream().filter(c -> c.name().equals("Team lead level")).findFirst().orElseThrow().score());
    }

    @Test
    void incompleteUntilLeadAnswersEveryApplicableQuestion() {
        List<A> ans = new ArrayList<>();
        answerAll(ans, commonQ.stream().filter(id -> !leadLevelQ.contains(id)).toList(), 9, 8, 8, 7);
        answerAll(ans, roleQ.subList(0, roleQ.size() - 1), 8, 8, 7, 7);
        Result r = ScoringEngine.compute(W, SR, cats, qs, ans, BANDS);
        assertEquals(Status.INCOMPLETE, r.status());
        assertEquals(51, r.leadAnswered());
        assertNull(r.finalScore());
    }

    @Test
    void teamLeadNeedsTeamLeadLevelQuestions() {
        List<A> ans = new ArrayList<>();
        answerAll(ans, commonQ.stream().filter(id -> !leadLevelQ.contains(id)).toList(), 9, 8, 8, 7);
        answerAll(ans, roleQ, 8, 8, 7, 7);
        Result r = ScoringEngine.compute(W, TL_LEVEL, cats, qs, ans, BANDS);
        assertEquals(64, r.needed());
        assertEquals(Status.INCOMPLETE, r.status());
    }

    @Test
    void naAndBlankReviewsAreReweighted() {
        // Self N/A, Peer blank -> question score = (TL*0.3 + Mgr*0.4) / 0.7
        Q q = qs.get(0);
        List<A> ans = List.of(new A(q.id(), SELF, null), new A(q.id(), TL, 6), new A(q.id(), MANAGER, 9));
        Result r = ScoringEngine.compute(W, SR, cats, qs, ans, BANDS);
        assertEquals((6 * 0.3 + 9 * 0.4) / 0.7, r.questions().get(0).score(), 1e-9);
    }

    @Test
    void severalPeersAreAveragedBeforeWeighting() {
        Q q = qs.get(0);
        List<A> ans = List.of(new A(q.id(), PEER, 4), new A(q.id(), PEER, 8), new A(q.id(), MANAGER, 10));
        Result r = ScoringEngine.compute(W, SR, cats, qs, ans, BANDS);
        assertEquals((6 * 0.2 + 10 * 0.4) / 0.6, r.questions().get(0).score(), 1e-9);
        assertEquals(2, r.questions().get(0).roleCounts().get(PEER));
    }

    @Test
    void tlManagerOnlyQuestionsIgnoreSelfAndPeer() {
        List<Q> custom = new ArrayList<>(qs);
        Q role0 = custom.get(commonQ.size());
        custom.set(commonQ.size(), new Q(role0.id(), role0.categoryId(), role0.text(), role0.weight(), ReviewedBy.TL_MANAGER_ONLY, Set.of()));
        List<A> ans = List.of(new A(role0.id(), SELF, 10), new A(role0.id(), MANAGER, 5));
        Result r = ScoringEngine.compute(W, SR, cats, custom, ans, BANDS);
        assertEquals(5.0, r.questions().get(commonQ.size()).score(), 1e-9);
    }

    @Test
    void questionCanBeSwitchedOffForALevel() {
        List<Q> custom = new ArrayList<>(qs);
        Q first = custom.get(0);
        custom.set(0, new Q(first.id(), first.categoryId(), first.text(), first.weight(), first.reviewedBy(), Set.of(JR)));
        assertFalse(ScoringEngine.compute(W, JR, cats, custom, List.of(), BANDS).questions().get(0).applicable());
        assertTrue(ScoringEngine.compute(W, SR, cats, custom, List.of(), BANDS).questions().get(0).applicable());
    }

    @Test
    void noLevelSelected() {
        assertEquals(Status.NOT_STARTED, ScoringEngine.compute(W, null, cats, qs, List.of(), BANDS).status());
        List<A> ans = List.of(new A(qs.get(0).id(), MANAGER, 7));
        assertEquals(Status.SELECT_LEVEL, ScoringEngine.compute(W, null, cats, qs, ans, BANDS).status());
    }

    @Test
    void ratingBandsUseLowerRangeUntilNextFromScore() {
        assertEquals("Meets Expectations", ScoringEngine.rate(6.99, BANDS));
        assertEquals("Exceeds Expectations", ScoringEngine.rate(7.0, BANDS));
        assertEquals("Unsatisfactory", ScoringEngine.rate(1, BANDS));
        assertEquals("Outstanding", ScoringEngine.rate(10, BANDS));
    }

    @Test
    void reviewerWeightsAreConfigurable() {
        Weights equal = new Weights(0.5, 0.5, 0.25, 0.25, 0.25, 0.25);
        Q q = qs.get(0);
        List<A> ans = List.of(new A(q.id(), SELF, 10), new A(q.id(), PEER, 6), new A(q.id(), TL, 4), new A(q.id(), MANAGER, 8));
        assertEquals(7.0, ScoringEngine.compute(equal, SR, cats, qs, ans, BANDS).questions().get(0).score(), 1e-9);
    }

    @Test
    void selfGapCountsThreeOrMoreApart() {
        Q q = qs.get(0);
        List<A> ans = List.of(new A(q.id(), SELF, 10), new A(q.id(), MANAGER, 6));
        assertEquals(1, ScoringEngine.compute(W, SR, cats, qs, ans, BANDS).selfGapCount());
    }
}
