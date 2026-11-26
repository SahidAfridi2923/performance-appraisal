package com.appraisal.web;

import com.appraisal.dto.Dtos.*;
import com.appraisal.security.CurrentUser;
import com.appraisal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService users;
    private final CurrentUser current;

    public AdminUserController(UserService users, CurrentUser current) {
        this.users = users;
        this.current = current;
    }

    @GetMapping
    public List<UserDto> list() { return users.list(); }

    @PostMapping
    public UserDto create(@Valid @RequestBody UserSaveRequest req) { return users.save(null, req); }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest req) { return users.save(id, req); }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        users.resetPassword(id, req.password());
    }

    @DeleteMapping("/{id}")
    public void deactivate(@PathVariable Long id) { users.deactivate(id, current.get()); }
}
