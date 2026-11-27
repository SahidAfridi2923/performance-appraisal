package com.appraisal.service;

import com.appraisal.config.ApiException;
import com.appraisal.domain.*;
import com.appraisal.domain.Enums.Block;
import com.appraisal.domain.Enums.PickListType;
import com.appraisal.dto.Dtos.*;
import com.appraisal.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * The live (editable) question bank and scoring configuration. Changes here apply to cycles opened afterwards,
 * or to an open cycle when an admin runs "Sync questions" on it.
 */
@Service
@Transactional
public class QuestionBankService {
    private static final double EPS = 0.0001;

    private final TeamRepo teams;
    private final LevelRepo levels;
    private final CategoryRepo categories;
    private final QuestionRepo questions;
    private final ScoringSettingsRepo settings;
    private final RatingBandRepo bands;
    private final ScaleLabelRepo scale;
    private final PickListItemRepo pickLists;
    private final AppUserRepo users;
    private final CycleQuestionRepo cycleQuestions;

    public QuestionBankService(TeamRepo teams, LevelRepo levels, CategoryRepo categories, QuestionRepo questions,
                               ScoringSettingsRepo settings, RatingBandRepo bands, ScaleLabelRepo scale,
                               PickListItemRepo pickLists, AppUserRepo users, CycleQuestionRepo cycleQuestions) {
        this.teams = teams;
        this.levels = levels;
        this.categories = categories;
        this.questions = questions;
        this.settings = settings;
        this.bands = bands;
        this.scale = scale;
        this.pickLists = pickLists;
        this.users = users;
        this.cycleQuestions = cycleQuestions;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public QuestionBankDto bank(Long teamId) {
        List<CategoryDto> common = categories.findByBlockOrderBySortOrderAsc(Block.COMMON).stream().map(this::withQuestions).toList();
        List<CategoryDto> role = teamId == null ? List.of()
                : categories.findByBlockAndTeamIdOrderBySortOrderAsc(Block.ROLE, teamId).stream().map(this::withQuestions).toList();
        return new QuestionBankDto(teamId, levelList(), common, role, Mapper.settings(getSettings()));
    }

    private CategoryDto withQuestions(Category c) {
        return Mapper.category(c, questions.findByCategoryIdOrderBySortOrderAscIdAsc(c.getId()).stream().map(Mapper::question).toList());
    }

    @Transactional(readOnly = true)
    public PublicConfigDto publicConfig() {
        Map<PickListType, List<String>> lists = new EnumMap<>(PickListType.class);
        for (PickListType t : PickListType.values()) lists.put(t, pickList(t));
        return new PublicConfigDto(teamList(), levelList(), bandList(),
                scale.findAllByOrderByValueDesc().stream().map(s -> new ScaleLabelDto(s.getValue(), s.getLabel())).toList(), lists);
    }

    public List<TeamDto> teamList() {
        return teams.findAllByOrderBySortOrderAscNameAsc().stream().map(Mapper::team).toList();
    }

    public List<LevelDto> levelList() {
        return levels.findAllByOrderBySortOrderAsc().stream().map(Mapper::level).toList();
    }

    public List<RatingBandDto> bandList() {
        return bands.findAllByOrderByMinScoreAsc().stream().map(b -> new RatingBandDto(b.getId(), b.getLabel(), b.getMinScore())).toList();
    }

    public List<String> pickList(PickListType t) {
        return pickLists.findByListTypeOrderBySortOrderAsc(t).stream().map(PickListItem::getLabel).toList();
    }

    public ScoringSettings getSettings() {
        return settings.findById(1L).orElseGet(() -> settings.save(new ScoringSettings()));
    }

    // ------------------------------------------------------------------ teams

    public TeamDto saveTeam(Long id, TeamDto dto) {
        Team t = id == null ? new Team() : teams.findById(id).orElseThrow(() -> ApiException.notFound("Team"));
        String code = dto.code().trim().toUpperCase();
        teams.findByCode(code).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw ApiException.conflict("Team code '" + code + "' is already used"); });
        boolean isNew = t.getId() == null;
        t.setCode(code);
        t.setName(dto.name().trim());
        t.setSortOrder(isNew && dto.sortOrder() == 0 ? (int) teams.count() + 1 : dto.sortOrder());
        t.setActive(isNew || dto.active());
        t = teams.save(t);
        if (isNew) {
            // every team starts with one role-specific category that applies to all levels
            Category c = new Category();
            c.setName("Role-specific");
            c.setBlock(Block.ROLE);
            c.setTeam(t);
            c.setSortOrder(1);
            for (Level l : levels.findAll()) c.getLevelPoints().put(l.getId(), 1.0);
            categories.save(c);
        }
        return Mapper.team(t);
    }

    /** Deletes an unused team, otherwise deactivates it. Returns true when it was deleted. */
    public boolean deleteTeam(Long id) {
        Team t = teams.findById(id).orElseThrow(() -> ApiException.notFound("Team"));
        List<Category> cats = categories.findByBlockAndTeamIdOrderBySortOrderAsc(Block.ROLE, id);
        boolean hasQuestions = cats.stream().anyMatch(c -> questions.countByCategoryId(c.getId()) > 0);
        if (users.countByTeamId(id) == 0 && users.countByManagedTeams_Id(id) == 0 && !hasQuestions) {
            categories.deleteAll(cats);
            teams.delete(t);
            return true;
        }
        t.setActive(false);
        return false;
    }

    // ------------------------------------------------------------------ levels

    public LevelDto saveLevel(Long id, LevelDto dto) {
        Level l = id == null ? new Level() : levels.findById(id).orElseThrow(() -> ApiException.notFound("Level"));
        levels.findByName(dto.name().trim()).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw ApiException.conflict("Level '" + dto.name() + "' already exists"); });
        boolean isNew = l.getId() == null;
        Long copyFrom = isNew ? levels.findAllByOrderBySortOrderAsc().stream().findFirst().map(Level::getId).orElse(null) : null;
        l.setName(dto.name().trim());
        l.setSortOrder(isNew && dto.sortOrder() == 0 ? (int) levels.count() + 1 : dto.sortOrder());
        l.setSkipTlReview(dto.skipTlReview());
        l.setActive(isNew || dto.active());
        l = levels.save(l);
        if (isNew) {
            // new level: copy each category's points from the first level so it is usable immediately
            for (Category c : categories.findAll()) {
                double p = copyFrom == null ? 1.0 : c.getLevelPoints().getOrDefault(copyFrom, 0.0);
                c.getLevelPoints().put(l.getId(), p);
            }
        }
        return Mapper.level(l);
    }

    public boolean deleteLevel(Long id) {
        Level l = levels.findById(id).orElseThrow(() -> ApiException.notFound("Level"));
        if (users.countByLevelId(id) == 0) {
            for (Category c : categories.findAll()) c.getLevelPoints().remove(id);
            for (Question q : questions.findAll()) q.getExcludedLevelIds().remove(id);
            levels.delete(l);
            return true;
        }
        l.setActive(false);
        return false;
    }

    // ------------------------------------------------------------------ categories

    public CategoryDto saveCategory(Long id, CategoryDto dto) {
        Category c = id == null ? new Category() : categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (c.getId() == null) {
            c.setBlock(dto.block());
            if (dto.block() == Block.ROLE) {
                if (dto.teamId() == null) throw ApiException.bad("A role-specific category needs a team");
                c.setTeam(teams.findById(dto.teamId()).orElseThrow(() -> ApiException.notFound("Team")));
            }
            List<Category> siblings = dto.block() == Block.COMMON ? categories.findByBlockOrderBySortOrderAsc(Block.COMMON)
                    : categories.findByBlockAndTeamIdOrderBySortOrderAsc(Block.ROLE, dto.teamId());
            c.setSortOrder(siblings.stream().mapToInt(Category::getSortOrder).max().orElse(0) + 1);
            c.setActive(true);
        } else {
            c.setActive(dto.active());
        }
        c.setName(dto.name().trim());
        Set<Long> levelIds = new HashSet<>();
        levels.findAll().forEach(l -> levelIds.add(l.getId()));
        Map<Long, Double> points = new HashMap<>();
        for (Long lid : levelIds) {
            Double p = dto.levelPoints() == null ? null : dto.levelPoints().get(lid);
            if (p != null && p < 0) throw ApiException.bad("Points cannot be negative");
            points.put(lid, p == null ? (c.getBlock() == Block.ROLE ? 1.0 : 0.0) : p);
        }
        c.getLevelPoints().clear();
        c.getLevelPoints().putAll(points);
        return withQuestions(categories.save(c));
    }

    /** Hard-deletes an empty category; archives a category that still has questions. */
    public boolean deleteCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (questions.countByCategoryId(id) == 0) {
            categories.delete(c);
            return true;
        }
        c.setActive(false);
        return false;
    }

    public void reorderCategories(List<Long> ids) {
        int i = 1;
        for (Long id : ids) {
            Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
            c.setSortOrder(i++);
        }
    }

    // ------------------------------------------------------------------ questions

    public QuestionDto saveQuestion(Long id, QuestionDto dto) {
        Question q = id == null ? new Question() : questions.findById(id).orElseThrow(() -> ApiException.notFound("Question"));
        Category c = categories.findById(dto.categoryId()).orElseThrow(() -> ApiException.notFound("Category"));
        boolean moved = q.getCategory() == null || !q.getCategory().getId().equals(c.getId());
        q.setCategory(c);
        q.setText(dto.text().trim());
        q.setWeight(dto.weight());
        q.setReviewedBy(dto.reviewedBy());
        q.setGuidance(dto.guidance() == null || dto.guidance().isBlank() ? null : dto.guidance().trim());
        q.setActive(q.getId() == null || dto.active());
        q.getExcludedLevelIds().clear();
        if (dto.excludedLevelIds() != null) q.getExcludedLevelIds().addAll(dto.excludedLevelIds());
        if (moved) {
            q.setSortOrder(questions.findByCategoryIdOrderBySortOrderAscIdAsc(c.getId()).stream()
                    .mapToInt(Question::getSortOrder).max().orElse(0) + 1);
        }
        return Mapper.question(questions.save(q));
    }

    /**
     * Removes a question. By default it is archived (kept for history, not copied into new cycles).
     * With permanent=true it is deleted, which is only allowed when no cycle ever used it.
     */
    public void deleteQuestion(Long id, boolean permanent) {
        Question q = questions.findById(id).orElseThrow(() -> ApiException.notFound("Question"));
        if (!permanent) {
            q.setActive(false);
            return;
        }
        boolean used = cycleQuestions.existsBySourceQuestionId(id);
        if (used) throw ApiException.conflict("This question was used in an appraisal cycle; it can only be archived");
        questions.delete(q);
    }

    public void reorderQuestions(Long categoryId, List<Long> ids) {
        int i = 1;
        for (Long id : ids) {
            Question q = questions.findById(id).orElseThrow(() -> ApiException.notFound("Question"));
            if (!q.getCategory().getId().equals(categoryId)) throw ApiException.bad("Question " + id + " is not in this category");
            q.setSortOrder(i++);
        }
    }

    // ------------------------------------------------------------------ scoring settings and lists

    public SettingsDto saveSettings(SettingsDto dto) {
        if (Math.abs(dto.commonWeight() + dto.roleWeight() - 1) > EPS)
            throw ApiException.bad("Common + Role-specific weights must total 100%");
        if (Math.abs(dto.selfWeight() + dto.peerWeight() + dto.tlWeight() + dto.managerWeight() - 1) > EPS)
            throw ApiException.bad("Self + Peer + TL + Manager weights must total 100%");
        ScoringSettings s = getSettings();
        s.setCommonWeight(dto.commonWeight());
        s.setRoleWeight(dto.roleWeight());
        s.setSelfWeight(dto.selfWeight());
        s.setPeerWeight(dto.peerWeight());
        s.setTlWeight(dto.tlWeight());
        s.setManagerWeight(dto.managerWeight());
        return Mapper.settings(s);
    }

    public List<RatingBandDto> saveBands(List<RatingBandDto> list) {
        if (list.isEmpty()) throw ApiException.bad("At least one rating band is needed");
        Set<Double> seen = new HashSet<>();
        for (RatingBandDto b : list) {
            if (!seen.add(b.minScore())) throw ApiException.bad("Two bands start at the same score " + b.minScore());
        }
        bands.deleteAllInBatch();
        for (RatingBandDto b : list) {
            RatingBand rb = new RatingBand();
            rb.setLabel(b.label().trim());
            rb.setMinScore(b.minScore());
            bands.save(rb);
        }
        return bandList();
    }

    public List<ScaleLabelDto> saveScale(List<ScaleLabelDto> list) {
        scale.deleteAllInBatch();
        scale.flush();
        for (ScaleLabelDto s : list) {
            ScaleLabel sl = new ScaleLabel();
            sl.setValue(s.value());
            sl.setLabel(s.label().trim());
            scale.save(sl);
        }
        return scale.findAllByOrderByValueDesc().stream().map(s -> new ScaleLabelDto(s.getValue(), s.getLabel())).toList();
    }

    public List<String> savePickList(PickListType type, List<String> items) {
        pickLists.deleteByListType(type);
        pickLists.flush();
        int i = 1;
        for (String label : items) {
            if (label == null || label.isBlank()) continue;
            PickListItem p = new PickListItem();
            p.setListType(type);
            p.setLabel(label.trim());
            p.setSortOrder(i++);
            pickLists.save(p);
        }
        return pickList(type);
    }
}
