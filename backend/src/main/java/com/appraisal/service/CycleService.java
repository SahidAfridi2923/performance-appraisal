package com.appraisal.service;

import com.appraisal.config.ApiException;
import com.appraisal.domain.*;
import com.appraisal.domain.Enums.*;
import com.appraisal.dto.Dtos.*;
import com.appraisal.repo.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Instant;
import java.util.*;

/**
 * Appraisal cycles. Opening a cycle copies the live question bank + scoring settings into cycle_* tables
 * (a frozen version), and creates one appraisal per active, appraised user that has a team.
 */
@Service
@Transactional
public class CycleService {
    private final CycleRepo cycles;
    private final CycleLevelRepo cycleLevels;
    private final CycleCategoryRepo cycleCategories;
    private final CycleQuestionRepo cycleQuestions;
    private final CycleRatingBandRepo cycleBands;
    private final AppraisalRepo appraisals;
    private final ReviewRepo reviews;
    private final AnswerRepo answers;
    private final LevelRepo levels;
    private final CategoryRepo categories;
    private final QuestionRepo questions;
    private final TeamRepo teams;
    private final RatingBandRepo bands;
    private final AppUserRepo users;
    private final QuestionBankService bank;
    private final AchievementRepo achievements;
    private final GoalRepo goals;
    private final CycleMembershipRepo memberships;
    private final EntityManager em;

    public CycleService(CycleRepo cycles, CycleLevelRepo cycleLevels, CycleCategoryRepo cycleCategories,
                        CycleQuestionRepo cycleQuestions, CycleRatingBandRepo cycleBands, AppraisalRepo appraisals,
                        ReviewRepo reviews, AnswerRepo answers, LevelRepo levels, CategoryRepo categories,
                        QuestionRepo questions, TeamRepo teams, RatingBandRepo bands, AppUserRepo users,
                        QuestionBankService bank, AchievementRepo achievements, GoalRepo goals,
                        CycleMembershipRepo memberships, EntityManager em) {
        this.cycles = cycles;
        this.cycleLevels = cycleLevels;
        this.cycleCategories = cycleCategories;
        this.cycleQuestions = cycleQuestions;
        this.cycleBands = cycleBands;
        this.appraisals = appraisals;
        this.reviews = reviews;
        this.answers = answers;
        this.levels = levels;
        this.categories = categories;
        this.questions = questions;
        this.teams = teams;
        this.bands = bands;
        this.users = users;
        this.bank = bank;
        this.achievements = achievements;
        this.goals = goals;
        this.memberships = memberships;
        this.em = em;
    }

    public Cycle get(Long id) {
        return cycles.findById(id).orElseThrow(() -> ApiException.notFound("Cycle"));
    }

    @Transactional(readOnly = true)
    public List<CycleDto> list() {
        return cycles.findAllByOrderByCreatedAtDesc().stream().map(c -> Mapper.cycle(c, appraisals.countByCycleId(c.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<CycleDto> visible() {
        return cycles.findByStatusInOrderByCreatedAtDesc(List.of(CycleStatus.OPEN, CycleStatus.CLOSED)).stream()
                .map(c -> Mapper.cycle(c, appraisals.countByCycleId(c.getId()))).toList();
    }

    public CycleDto create(CycleSaveRequest req) {
        Cycle c = new Cycle();
        c.setName(req.name().trim());
        return Mapper.cycle(cycles.save(c), 0);
    }

    public CycleDto rename(Long id, CycleSaveRequest req) {
        Cycle c = get(id);
        c.setName(req.name().trim());
        return Mapper.cycle(c, appraisals.countByCycleId(id));
    }

    public CycleDto open(Long id) {
        Cycle c = get(id);
        if (c.getStatus() != CycleStatus.DRAFT) throw ApiException.bad("Only a draft cycle can be opened");
        sync(c, true);
        Map<Long, CycleLevel> levelBySource = levelBySource(c.getId());
        for (AppUser u : users.findByActiveTrueAndAppraisedTrueAndTeamIsNotNull()) {
            createAppraisal(c, u, levelBySource);
        }
        c.setStatus(CycleStatus.OPEN);
        c.setOpenedAt(Instant.now());
        return Mapper.cycle(c, appraisals.countByCycleId(id));
    }

    public CycleDto close(Long id) {
        Cycle c = get(id);
        if (c.getStatus() != CycleStatus.OPEN) throw ApiException.bad("Only an open cycle can be closed");
        c.setStatus(CycleStatus.CLOSED);
        c.setClosedAt(Instant.now());
        return Mapper.cycle(c, appraisals.countByCycleId(id));
    }

    public CycleDto reopen(Long id) {
        Cycle c = get(id);
        if (c.getStatus() != CycleStatus.CLOSED) throw ApiException.bad("Only a closed cycle can be reopened");
        c.setStatus(CycleStatus.OPEN);
        c.setClosedAt(null);
        return Mapper.cycle(c, appraisals.countByCycleId(id));
    }

    /** Deletes a cycle and everything in it (answers, reviews, appraisals, achievements, goals, snapshot). */
    public void delete(Long id) {
        Cycle c = get(id);
        em.createQuery("delete from Answer a where a.review.id in (select r.id from Review r where r.appraisal.cycle.id = :c)").setParameter("c", id).executeUpdate();
        em.createQuery("delete from Review r where r.appraisal.id in (select a.id from Appraisal a where a.cycle.id = :c)").setParameter("c", id).executeUpdate();
        em.createQuery("delete from Achievement x where x.appraisal.id in (select a.id from Appraisal a where a.cycle.id = :c)").setParameter("c", id).executeUpdate();
        em.createQuery("delete from Goal x where x.appraisal.id in (select a.id from Appraisal a where a.cycle.id = :c)").setParameter("c", id).executeUpdate();
        em.createQuery("delete from Appraisal a where a.cycle.id = :c").setParameter("c", id).executeUpdate();
        em.createQuery("delete from CycleMembership m where m.cycleId = :c").setParameter("c", id).executeUpdate();
        // collection tables first
        for (CycleQuestion q : cycleQuestions.findByCycle(id)) cycleQuestions.delete(q);
        for (CycleCategory cc : cycleCategories.findByCycleIdOrderBySortOrderAsc(id)) cycleCategories.delete(cc);
        cycleQuestions.flush();
        em.createQuery("delete from CycleLevel l where l.cycle.id = :c").setParameter("c", id).executeUpdate();
        em.createQuery("delete from CycleRatingBand b where b.cycle.id = :c").setParameter("c", id).executeUpdate();
        cycles.delete(c);
    }

    // ------------------------------------------------------------------ participants

    @Transactional(readOnly = true)
    public List<ParticipantDto> participants(Long cycleId) {
        List<Appraisal> list = appraisals.findByCycleIdOrderByTeamNameAscEmployeeFullNameAsc(cycleId);
        Map<Long, List<Review>> byAppraisal = new HashMap<>();
        reviews.findByCycle(cycleId).forEach(r -> byAppraisal.computeIfAbsent(r.getAppraisal().getId(), k -> new ArrayList<>()).add(r));
        return list.stream().map(a -> {
            Map<ReviewerRole, Integer> sub = new EnumMap<>(ReviewerRole.class), dr = new EnumMap<>(ReviewerRole.class);
            for (Review r : byAppraisal.getOrDefault(a.getId(), List.of())) {
                (r.getStatus() == ReviewStatus.SUBMITTED ? sub : dr).merge(r.getRole(), 1, Integer::sum);
            }
            return new ParticipantDto(a.getId(), a.getEmployee().getId(), a.getEmployee().getFullName(), a.getTeamName(),
                    a.getLevel() == null ? null : a.getLevel().getName(), sub, dr);
        }).toList();
    }

    /**
     * Adds anyone active (any role) to an open cycle. Team and level can be set in the same step; they are saved
     * on the person, because open cycles follow the Users page. The decision is remembered for this cycle only.
     */
    public ParticipantDto addParticipant(Long cycleId, ParticipantRequest req) {
        Cycle c = get(cycleId);
        if (c.getStatus() != CycleStatus.OPEN) throw ApiException.bad("People can be added only while the cycle is open");
        AppUser u = users.findById(req.userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (!u.isActive()) throw ApiException.bad(u.getFullName() + " is inactive; activate them on the Users page first");
        if (req.teamId() != null) u.setTeam(teams.findById(req.teamId()).orElseThrow(() -> ApiException.notFound("Team")));
        if (req.levelId() != null) u.setLevel(levels.findById(req.levelId()).orElseThrow(() -> ApiException.notFound("Level")));
        if (u.getTeam() == null) throw ApiException.bad("Choose a team for " + u.getFullName());
        if (appraisals.findByCycleIdAndEmployeeId(cycleId, u.getId()).isPresent()) throw ApiException.conflict(u.getFullName() + " is already in this cycle");
        setMembership(cycleId, u.getId(), true);
        Appraisal a = createAppraisal(c, u, levelBySource(cycleId));
        return new ParticipantDto(a.getId(), u.getId(), u.getFullName(), a.getTeamName(), a.getLevel() == null ? null : a.getLevel().getName(), Map.of(), Map.of());
    }

    private void setMembership(Long cycleId, Long userId, boolean included) {
        CycleMembership m = memberships.findByCycleIdAndUserId(cycleId, userId).orElseGet(() -> {
            CycleMembership x = new CycleMembership();
            x.setCycleId(cycleId);
            x.setUserId(userId);
            return x;
        });
        m.setIncluded(included);
        memberships.save(m);
    }

    public void removeParticipant(Long cycleId, Long appraisalId) {
        Appraisal a = appraisals.findById(appraisalId).filter(x -> x.getCycle().getId().equals(cycleId))
                .orElseThrow(() -> ApiException.notFound("Appraisal"));
        if (a.getCycle().getStatus() == CycleStatus.CLOSED) throw ApiException.bad("The cycle is closed");
        setMembership(cycleId, a.getEmployee().getId(), false); // stays out of this cycle until added again
        em.createQuery("delete from Answer x where x.review.id in (select r.id from Review r where r.appraisal.id = :a)").setParameter("a", appraisalId).executeUpdate();
        em.createQuery("delete from Review r where r.appraisal.id = :a").setParameter("a", appraisalId).executeUpdate();
        em.createQuery("delete from Achievement x where x.appraisal.id = :a").setParameter("a", appraisalId).executeUpdate();
        em.createQuery("delete from Goal x where x.appraisal.id = :a").setParameter("a", appraisalId).executeUpdate();
        appraisals.delete(a);
    }

    private Appraisal createAppraisal(Cycle c, AppUser u, Map<Long, CycleLevel> levelBySource) {
        Appraisal a = new Appraisal();
        a.setCycle(c);
        a.setEmployee(u);
        a.setTeamId(u.getTeam().getId());
        a.setTeamName(u.getTeam().getName());
        a.setLevel(u.getLevel() == null ? null : levelBySource.get(u.getLevel().getId()));
        return appraisals.save(a);
    }

    private Map<Long, CycleLevel> levelBySource(Long cycleId) {
        Map<Long, CycleLevel> m = new HashMap<>();
        cycleLevels.findByCycleIdOrderBySortOrderAsc(cycleId).forEach(l -> m.put(l.getSourceLevelId(), l));
        return m;
    }

    /**
     * Keeps appraisals in OPEN cycles in line with the Users page: level, team and who takes part.
     * Closed cycles stay frozen. People who should no longer take part are removed only when nothing
     * was written for them yet (no reviews, achievements or goals); otherwise an admin removes them on
     * the cycle page. Pass a user id to check one person, or null for everyone.
     * Returns the number of appraisals changed.
     */
    public int reconcileOpenCycles(Long onlyUserId) {
        int changes = 0;
        List<AppUser> people = onlyUserId == null ? users.findAll()
                : users.findById(onlyUserId).map(List::of).orElse(List.of());
        for (Cycle c : cycles.findByStatusInOrderByCreatedAtDesc(List.of(CycleStatus.OPEN))) {
            Map<Long, CycleLevel> levelMap = levelBySource(c.getId());
            Map<Long, Appraisal> byUser = new HashMap<>();
            appraisals.findByCycleIdOrderByTeamNameAscEmployeeFullNameAsc(c.getId())
                    .forEach(a -> byUser.put(a.getEmployee().getId(), a));
            Map<Long, Boolean> decided = new HashMap<>();
            memberships.findByCycleId(c.getId()).forEach(m -> decided.put(m.getUserId(), m.isIncluded()));
            for (AppUser u : people) {
                Appraisal a = byUser.get(u.getId());
                // an admin's add / remove for this cycle wins over the "appraised" flag
                boolean wanted = decided.getOrDefault(u.getId(), u.isAppraised());
                boolean takesPart = u.isActive() && wanted && u.getTeam() != null;
                if (a == null) {
                    if (takesPart) { createAppraisal(c, u, levelMap); changes++; }
                    continue;
                }
                if (!takesPart) {
                    if (!reviews.existsByAppraisalId(a.getId()) && !achievements.existsByAppraisalId(a.getId())
                            && !goals.existsByAppraisalId(a.getId())) {
                        removeParticipant(c.getId(), a.getId());
                        changes++;
                    }
                    continue;
                }
                CycleLevel want = u.getLevel() == null ? null : levelMap.get(u.getLevel().getId());
                boolean levelKnown = u.getLevel() == null || want != null; // a level created after the cycle opened has no snapshot
                Long have = a.getLevel() == null ? null : a.getLevel().getId();
                Long wantId = want == null ? null : want.getId();
                if (levelKnown && !Objects.equals(have, wantId)) { a.setLevel(want); changes++; }
                if (!a.getTeamId().equals(u.getTeam().getId()) || !a.getTeamName().equals(u.getTeam().getName())) {
                    a.setTeamId(u.getTeam().getId());
                    a.setTeamName(u.getTeam().getName());
                    changes++;
                }
            }
        }
        return changes;
    }

    // ------------------------------------------------------------------ reviews (admin)

    @Transactional(readOnly = true)
    public List<ReviewAdminDto> reviewList(Long cycleId) {
        return reviews.findByCycle(cycleId).stream()
                .sorted(Comparator.comparing((Review r) -> r.getAppraisal().getTeamName())
                        .thenComparing(r -> r.getAppraisal().getEmployee().getFullName()).thenComparing(Review::getRole))
                .map(r -> new ReviewAdminDto(r.getId(), r.getAppraisal().getId(), r.getAppraisal().getEmployee().getFullName(),
                        r.getAppraisal().getTeamName(), r.getReviewer().getFullName(), r.getRole(), r.getStatus(),
                        r.getUpdatedAt(), r.getSubmittedAt())).toList();
    }

    public void reopenReview(Long reviewId) {
        Review r = reviews.findById(reviewId).orElseThrow(() -> ApiException.notFound("Review"));
        if (r.getAppraisal().getCycle().getStatus() != CycleStatus.OPEN) throw ApiException.bad("The cycle is not open");
        r.setStatus(ReviewStatus.DRAFT);
        r.setSubmittedAt(null);
    }

    public void deleteReview(Long reviewId) {
        Review r = reviews.findById(reviewId).orElseThrow(() -> ApiException.notFound("Review"));
        if (r.getAppraisal().getCycle().getStatus() == CycleStatus.CLOSED) throw ApiException.bad("The cycle is closed");
        reviews.delete(r);
    }

    // ------------------------------------------------------------------ snapshot / sync

    /**
     * Brings the cycle's frozen copy in line with the live question bank. Used when a cycle opens, and by admins
     * to push question edits into an open cycle. With apply=false the changes are only reported (rolled back).
     * Answers to questions that are removed from the cycle are deleted.
     */
    public SyncReport sync(Long cycleId, boolean apply) {
        Cycle c = get(cycleId);
        if (c.getStatus() != CycleStatus.OPEN) throw ApiException.bad("Questions can be synced only into an open cycle");
        SyncReport r = sync(c, apply);
        if (!apply) TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return r;
    }

    private SyncReport sync(Cycle c, boolean apply) {
        List<String> notes = new ArrayList<>();
        int qAdded = 0, qUpdated = 0, qRemoved = 0, aRemoved = 0, cAdded = 0, cUpdated = 0, cRemoved = 0, lAdded = 0;

        // settings
        ScoringSettings s = bank.getSettings();
        boolean settingsChanged = c.getSnapshotAt() != null && (c.getCommonWeight() != s.getCommonWeight()
                || c.getRoleWeight() != s.getRoleWeight() || c.getSelfWeight() != s.getSelfWeight()
                || c.getPeerWeight() != s.getPeerWeight() || c.getTlWeight() != s.getTlWeight() || c.getManagerWeight() != s.getManagerWeight());
        c.setCommonWeight(s.getCommonWeight());
        c.setRoleWeight(s.getRoleWeight());
        c.setSelfWeight(s.getSelfWeight());
        c.setPeerWeight(s.getPeerWeight());
        c.setTlWeight(s.getTlWeight());
        c.setManagerWeight(s.getManagerWeight());

        // rating bands: replace
        cycleBands.deleteByCycleId(c.getId());
        for (RatingBand b : bands.findAllByOrderByMinScoreAsc()) {
            CycleRatingBand cb = new CycleRatingBand();
            cb.setCycle(c);
            cb.setLabel(b.getLabel());
            cb.setMinScore(b.getMinScore());
            cycleBands.save(cb);
        }

        // levels: add / update (never removed, appraisals point at them)
        Map<Long, CycleLevel> levelMap = levelBySource(c.getId());
        // every level is copied (inactive ones too) so nobody's level turns into "Not set"
        for (Level l : levels.findAllByOrderBySortOrderAsc()) {
            CycleLevel cl = levelMap.get(l.getId());
            if (cl == null) {
                cl = new CycleLevel();
                cl.setCycle(c);
                cl.setSourceLevelId(l.getId());
                lAdded++;
            }
            cl.setName(l.getName());
            cl.setSortOrder(l.getSortOrder());
            cl.setSkipTlReview(l.isSkipTlReview());
            levelMap.put(l.getId(), cycleLevels.save(cl));
        }

        // categories
        Set<Long> activeTeams = new HashSet<>();
        teams.findAll().stream().filter(Team::isActive).forEach(t -> activeTeams.add(t.getId()));
        // keep role questions of a deactivated team while people of that team are still in this cycle
        appraisals.findByCycleIdOrderByTeamNameAscEmployeeFullNameAsc(c.getId()).forEach(a -> activeTeams.add(a.getTeamId()));
        List<Category> wanted = categories.findAll().stream()
                .filter(Category::isActive)
                .filter(cat -> cat.getBlock() == Block.COMMON || (cat.getTeam() != null && activeTeams.contains(cat.getTeam().getId())))
                .toList();
        Map<Long, CycleCategory> existingCats = new HashMap<>();
        cycleCategories.findByCycleIdOrderBySortOrderAsc(c.getId()).forEach(cc -> existingCats.put(cc.getSourceCategoryId(), cc));
        Map<Long, CycleCategory> catBySource = new HashMap<>();
        for (Category cat : wanted) {
            CycleCategory cc = existingCats.remove(cat.getId());
            Map<Long, Double> pts = new HashMap<>();
            cat.getLevelPoints().forEach((lid, p) -> {
                CycleLevel cl = levelMap.get(lid);
                if (cl != null) pts.put(cl.getId(), p);
            });
            if (cc == null) {
                cc = new CycleCategory();
                cc.setCycle(c);
                cc.setSourceCategoryId(cat.getId());
                cAdded++;
            } else if (!Objects.equals(cc.getName(), cat.getName()) || cc.getSortOrder() != cat.getSortOrder() || !cc.getLevelPoints().equals(pts)) {
                cUpdated++;
            }
            cc.setBlock(cat.getBlock());
            cc.setTeamId(cat.getTeam() == null ? null : cat.getTeam().getId());
            cc.setName(cat.getName());
            cc.setSortOrder(cat.getSortOrder());
            cc.getLevelPoints().clear();
            cc.getLevelPoints().putAll(pts);
            catBySource.put(cat.getId(), cycleCategories.save(cc));
        }

        // questions
        Map<Long, CycleQuestion> existingQs = new HashMap<>();
        cycleQuestions.findByCycle(c.getId()).forEach(q -> existingQs.put(q.getSourceQuestionId(), q));
        for (Category cat : wanted) {
            for (Question q : questions.findByCategoryIdOrderBySortOrderAscIdAsc(cat.getId())) {
                if (!q.isActive()) continue;
                CycleQuestion cq = existingQs.remove(q.getId());
                Set<Long> excluded = new HashSet<>();
                q.getExcludedLevelIds().forEach(lid -> { CycleLevel cl = levelMap.get(lid); if (cl != null) excluded.add(cl.getId()); });
                CycleCategory target = catBySource.get(cat.getId());
                if (cq == null) {
                    cq = new CycleQuestion();
                    cq.setSourceQuestionId(q.getId());
                    qAdded++;
                } else if (!Objects.equals(cq.getText(), q.getText()) || cq.getWeight() != q.getWeight()
                        || cq.getReviewedBy() != q.getReviewedBy() || !Objects.equals(cq.getGuidance(), q.getGuidance())
                        || cq.getSortOrder() != q.getSortOrder() || !cq.getCategory().getId().equals(target.getId())
                        || !cq.getExcludedLevelIds().equals(excluded)) {
                    qUpdated++;
                }
                cq.setCategory(target);
                cq.setText(q.getText());
                cq.setWeight(q.getWeight());
                cq.setReviewedBy(q.getReviewedBy());
                cq.setGuidance(q.getGuidance());
                cq.setSortOrder(q.getSortOrder());
                cq.getExcludedLevelIds().clear();
                cq.getExcludedLevelIds().addAll(excluded);
                cycleQuestions.save(cq);
            }
        }
        for (CycleQuestion gone : existingQs.values()) {
            long n = answers.countByQuestionId(gone.getId());
            aRemoved += (int) n;
            answers.deleteByQuestionId(gone.getId());
            cycleQuestions.delete(gone);
            qRemoved++;
        }
        cycleQuestions.flush();
        for (CycleCategory gone : existingCats.values()) {
            for (CycleQuestion q : cycleQuestions.findByCategoryId(gone.getId())) {
                aRemoved += (int) answers.countByQuestionId(q.getId());
                answers.deleteByQuestionId(q.getId());
                cycleQuestions.delete(q);
                qRemoved++;
            }
            cycleQuestions.flush();
            cycleCategories.delete(gone);
            cRemoved++;
        }

        boolean first = c.getSnapshotAt() == null;
        c.setSnapshotAt(Instant.now());
        if (!first && qAdded > 0) {
            long submittedLead = reviews.findByCycle(c.getId()).stream()
                    .filter(rv -> rv.getStatus() == ReviewStatus.SUBMITTED)
                    .filter(rv -> rv.getRole() == ReviewerRole.TL || rv.getRole() == ReviewerRole.MANAGER).count();
            if (submittedLead > 0) {
                notes.add(submittedLead + " submitted TL/Manager review(s) do not cover the " + qAdded
                        + " new question(s). Final scores stay 'Incomplete' until those reviews are reopened and answered.");
            }
        }
        if (aRemoved > 0) notes.add(aRemoved + " answer(s) to removed questions will be deleted.");
        if (settingsChanged) notes.add("Scoring weights changed; scores in this cycle will be recalculated.");
        return new SyncReport(apply, qAdded, qUpdated, qRemoved, aRemoved, cAdded, cUpdated, cRemoved, lAdded, settingsChanged, notes);
    }
}
