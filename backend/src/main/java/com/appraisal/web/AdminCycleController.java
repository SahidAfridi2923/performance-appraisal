package com.appraisal.web;

import com.appraisal.dto.Dtos.*;
import com.appraisal.service.CycleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminCycleController {
    private final CycleService cycles;

    public AdminCycleController(CycleService cycles) {
        this.cycles = cycles;
    }

    @GetMapping("/cycles")
    public List<CycleDto> list() { return cycles.list(); }

    @PostMapping("/cycles")
    public CycleDto create(@Valid @RequestBody CycleSaveRequest req) { return cycles.create(req); }

    @PutMapping("/cycles/{id}")
    public CycleDto rename(@PathVariable Long id, @Valid @RequestBody CycleSaveRequest req) { return cycles.rename(id, req); }

    @DeleteMapping("/cycles/{id}")
    public void delete(@PathVariable Long id) { cycles.delete(id); }

    @PostMapping("/cycles/{id}/open")
    public CycleDto open(@PathVariable Long id) { return cycles.open(id); }

    @PostMapping("/cycles/{id}/close")
    public CycleDto close(@PathVariable Long id) { return cycles.close(id); }

    @PostMapping("/cycles/{id}/reopen")
    public CycleDto reopen(@PathVariable Long id) { return cycles.reopen(id); }

    /** Push question-bank edits into an open cycle. apply=false only previews the changes. */
    @PostMapping("/cycles/{id}/sync")
    public SyncReport sync(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean apply) {
        return cycles.sync(id, apply);
    }

    @GetMapping("/cycles/{id}/participants")
    public List<ParticipantDto> participants(@PathVariable Long id) { return cycles.participants(id); }

    @PostMapping("/cycles/{id}/participants")
    public ParticipantDto addParticipant(@PathVariable Long id, @Valid @RequestBody ParticipantRequest req) {
        return cycles.addParticipant(id, req);
    }

    @DeleteMapping("/cycles/{id}/participants/{appraisalId}")
    public void removeParticipant(@PathVariable Long id, @PathVariable Long appraisalId) { cycles.removeParticipant(id, appraisalId); }

    @GetMapping("/cycles/{id}/reviews")
    public List<ReviewAdminDto> reviews(@PathVariable Long id) { return cycles.reviewList(id); }

    @PostMapping("/reviews/{id}/reopen")
    public void reopenReview(@PathVariable Long id) { cycles.reopenReview(id); }

    @DeleteMapping("/reviews/{id}")
    public void deleteReview(@PathVariable Long id) { cycles.deleteReview(id); }
}
