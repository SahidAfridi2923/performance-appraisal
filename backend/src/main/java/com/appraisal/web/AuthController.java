package com.appraisal.web;

import com.appraisal.dto.Dtos.*;
import com.appraisal.security.CurrentUser;
import com.appraisal.service.Mapper;
import com.appraisal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserService users;
    private final CurrentUser current;

    public AuthController(UserService users, CurrentUser current) {
        this.users = users;
        this.current = current;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return users.login(req);
    }

    @GetMapping("/auth/me")
    public MeDto me() {
        return Mapper.me(current.get());
    }

    @PostMapping("/auth/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        users.changePassword(current.get(), req);
    }
}
