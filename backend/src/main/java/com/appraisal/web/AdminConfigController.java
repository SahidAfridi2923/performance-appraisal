package com.appraisal.web;

import com.appraisal.domain.Enums.PickListType;
import com.appraisal.dto.Dtos.*;
import com.appraisal.service.CycleService;
import com.appraisal.service.Mapper;
import com.appraisal.service.QuestionBankService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin-only: question bank, teams, levels, scoring settings, rating bands and pick lists. */
@RestController
@RequestMapping("/api/admin")
public class AdminConfigController {
    private final QuestionBankService bank;
    private final CycleService cycles;

    public AdminConfigController(QuestionBankService bank, CycleService cycles) {
        this.bank = bank;
        this.cycles = cycles;
    }

    @GetMapping("/question-bank")
    public QuestionBankDto questionBank(@RequestParam(required = false) Long teamId) {
        return bank.bank(teamId);
    }

    // teams
    @GetMapping("/teams")
    public List<TeamDto> teams() { return bank.teamList(); }

    @PostMapping("/teams")
    public TeamDto createTeam(@Valid @RequestBody TeamDto dto) { return bank.saveTeam(null, dto); }

    @PutMapping("/teams/{id}")
    public TeamDto updateTeam(@PathVariable Long id, @Valid @RequestBody TeamDto dto) {
        TeamDto t = bank.saveTeam(id, dto);
        cycles.reconcileOpenCycles(null); // team name shown in open cycles
        return t;
    }

    @DeleteMapping("/teams/{id}")
    public Map<String, Boolean> deleteTeam(@PathVariable Long id) { return Map.of("deleted", bank.deleteTeam(id)); }

    // levels
    @GetMapping("/levels")
    public List<LevelDto> levels() { return bank.levelList(); }

    @PostMapping("/levels")
    public LevelDto createLevel(@Valid @RequestBody LevelDto dto) { return bank.saveLevel(null, dto); }

    @PutMapping("/levels/{id}")
    public LevelDto updateLevel(@PathVariable Long id, @Valid @RequestBody LevelDto dto) { return bank.saveLevel(id, dto); }

    @DeleteMapping("/levels/{id}")
    public Map<String, Boolean> deleteLevel(@PathVariable Long id) { return Map.of("deleted", bank.deleteLevel(id)); }

    // categories
    @PostMapping("/categories")
    public CategoryDto createCategory(@Valid @RequestBody CategoryDto dto) { return bank.saveCategory(null, dto); }

    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryDto dto) { return bank.saveCategory(id, dto); }

    @DeleteMapping("/categories/{id}")
    public Map<String, Boolean> deleteCategory(@PathVariable Long id) { return Map.of("deleted", bank.deleteCategory(id)); }

    @PutMapping("/categories/order")
    public void reorderCategories(@Valid @RequestBody OrderRequest req) { bank.reorderCategories(req.ids()); }

    @PutMapping("/categories/{id}/question-order")
    public void reorderQuestions(@PathVariable Long id, @Valid @RequestBody OrderRequest req) { bank.reorderQuestions(id, req.ids()); }

    // questions
    @PostMapping("/questions")
    public QuestionDto createQuestion(@Valid @RequestBody QuestionDto dto) { return bank.saveQuestion(null, dto); }

    @PutMapping("/questions/{id}")
    public QuestionDto updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionDto dto) { return bank.saveQuestion(id, dto); }

    @DeleteMapping("/questions/{id}")
    public void deleteQuestion(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean permanent) {
        bank.deleteQuestion(id, permanent);
    }

    // scoring settings and lists
    @GetMapping("/settings")
    public SettingsDto settings() { return Mapper.settings(bank.getSettings()); }

    @PutMapping("/settings")
    public SettingsDto saveSettings(@Valid @RequestBody SettingsDto dto) { return bank.saveSettings(dto); }

    @PutMapping("/rating-bands")
    public List<RatingBandDto> saveBands(@Valid @RequestBody List<@Valid RatingBandDto> list) { return bank.saveBands(list); }

    @PutMapping("/scale-labels")
    public List<ScaleLabelDto> saveScale(@Valid @RequestBody List<@Valid ScaleLabelDto> list) { return bank.saveScale(list); }

    @PutMapping("/pick-lists/{type}")
    public List<String> savePickList(@PathVariable PickListType type, @Valid @RequestBody PickListDto dto) {
        return bank.savePickList(type, dto.items());
    }
}
