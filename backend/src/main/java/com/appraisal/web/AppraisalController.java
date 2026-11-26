package com.appraisal.web;

import com.appraisal.domain.Enums.ReviewerRole;
import com.appraisal.dto.Dtos.*;
import com.appraisal.security.CurrentUser;
import com.appraisal.service.AppraisalService;
import com.appraisal.service.CycleService;
import com.appraisal.service.QuestionBankService;
import com.appraisal.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Endpoints for every signed-in user: cycles, review forms, appraisal pages, summary. */
@RestController
@RequestMapping("/api")
public class AppraisalController {
    private final CurrentUser current;
    private final CycleService cycles;
    private final ReviewService reviews;
    private final AppraisalService appraisals;
    private final QuestionBankService bank;

    public AppraisalController(CurrentUser current, CycleService cycles, ReviewService reviews, AppraisalService appraisals,
                               QuestionBankService bank) {
        this.current = current;
        this.cycles = cycles;
        this.reviews = reviews;
        this.appraisals = appraisals;
        this.bank = bank;
    }

    @GetMapping("/config")
    public PublicConfigDto config() { return bank.publicConfig(); }

    @GetMapping("/cycles")
    public List<CycleDto> cycles() { return cycles.visible(); }

    @GetMapping("/cycles/{id}/tasks")
    public CycleTasksDto tasks(@PathVariable Long id) { return reviews.tasks(id, current.get()); }

    @GetMapping("/cycles/{id}/summary")
    public SummaryDto summary(@PathVariable Long id) { return appraisals.summary(id, current.get()); }

    @GetMapping("/my/appraisals")
    public List<MyAppraisalDto> mine() { return appraisals.mine(current.get()); }

    // review forms
    @GetMapping("/appraisals/{id}/reviews/{role}")
    public ReviewFormDto form(@PathVariable Long id, @PathVariable ReviewerRole role) {
        return reviews.form(id, role, current.get());
    }

    @PutMapping("/appraisals/{id}/reviews/{role}")
    public ReviewFormDto save(@PathVariable Long id, @PathVariable ReviewerRole role, @Valid @RequestBody SaveAnswersRequest req) {
        return reviews.save(id, role, current.get(), req, false);
    }

    @PostMapping("/appraisals/{id}/reviews/{role}/submit")
    public ReviewFormDto submit(@PathVariable Long id, @PathVariable ReviewerRole role, @Valid @RequestBody SaveAnswersRequest req) {
        return reviews.save(id, role, current.get(), req, true);
    }

    // appraisal page
    @GetMapping("/appraisals/{id}")
    public AppraisalDetailDto detail(@PathVariable Long id) { return appraisals.detail(id, current.get()); }

    @PutMapping("/appraisals/{id}/level")
    public void setLevel(@PathVariable Long id, @RequestBody SetLevelRequest req) { appraisals.setLevel(id, req.cycleLevelId(), current.get()); }

    @PutMapping("/appraisals/{id}/lead-comments")
    public void leadComments(@PathVariable Long id, @Valid @RequestBody TextRequest req) { appraisals.setLeadComments(id, req.text(), current.get()); }

    @PostMapping("/appraisals/{id}/achievements")
    public AchievementDto addAchievement(@PathVariable Long id, @Valid @RequestBody AchievementDto dto) {
        return appraisals.addAchievement(id, dto, current.get());
    }

    @PutMapping("/achievements/{id}")
    public AchievementDto updateAchievement(@PathVariable Long id, @Valid @RequestBody AchievementDto dto) {
        return appraisals.updateAchievement(id, dto, current.get());
    }

    @DeleteMapping("/achievements/{id}")
    public void deleteAchievement(@PathVariable Long id) { appraisals.deleteAchievement(id, current.get()); }

    @PostMapping("/appraisals/{id}/goals")
    public GoalDto addGoal(@PathVariable Long id, @Valid @RequestBody GoalDto dto) { return appraisals.addGoal(id, dto, current.get()); }

    @PutMapping("/goals/{id}")
    public GoalDto updateGoal(@PathVariable Long id, @Valid @RequestBody GoalDto dto) { return appraisals.updateGoal(id, dto, current.get()); }

    @DeleteMapping("/goals/{id}")
    public void deleteGoal(@PathVariable Long id) { appraisals.deleteGoal(id, current.get()); }
}
