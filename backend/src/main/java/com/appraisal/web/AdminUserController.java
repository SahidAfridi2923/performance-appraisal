package com.appraisal.web;

import com.appraisal.dto.Dtos.*;
import com.appraisal.security.CurrentUser;
import com.appraisal.service.CycleService;
import com.appraisal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService users;
    private final CurrentUser current;
    private final CycleService cycles;

    public AdminUserController(UserService users, CurrentUser current, CycleService cycles) {
        this.users = users;
        this.current = current;
        this.cycles = cycles;
    }

    @GetMapping
    public List<UserDto> list() { return users.list(); }

    @PostMapping
    public UserDto create(@Valid @RequestBody UserSaveRequest req) {
        UserDto u = users.save(null, req, current.get());
        cycles.reconcileOpenCycles(u.id()); // open cycles follow the Users page
        return u;
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest req) {
        UserDto u = users.save(id, req, current.get());
        cycles.reconcileOpenCycles(id);
        return u;
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        users.resetPassword(id, req.password());
    }

    @DeleteMapping("/{id}")
    public void deactivate(@PathVariable Long id) {
        users.deactivate(id, current.get());
        cycles.reconcileOpenCycles(id);
    }
}
