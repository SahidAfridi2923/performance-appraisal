package com.appraisal.service;

import com.appraisal.config.ApiException;
import com.appraisal.domain.AppUser;
import com.appraisal.domain.Team;
import com.appraisal.dto.Dtos.*;
import com.appraisal.repo.AppUserRepo;
import com.appraisal.repo.LevelRepo;
import com.appraisal.repo.TeamRepo;
import com.appraisal.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class UserService {
    private final AppUserRepo users;
    private final TeamRepo teams;
    private final LevelRepo levels;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public UserService(AppUserRepo users, TeamRepo teams, LevelRepo levels, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.teams = teams;
        this.levels = levels;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public LoginResponse login(LoginRequest req) {
        AppUser u = users.findByUsernameIgnoreCase(req.username().trim())
                .filter(AppUser::isActive)
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Wrong username or password"));
        return new LoginResponse(jwt.issue(u.getId(), u.getUsername()), Mapper.me(u));
    }

    public void changePassword(AppUser u, ChangePasswordRequest req) {
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) throw ApiException.bad("Current password is wrong");
        u.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(u);
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findAllByOrderByFullNameAsc().stream().map(Mapper::user).toList();
    }

    public UserDto save(Long id, UserSaveRequest req) {
        AppUser u = id == null ? new AppUser() : users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        users.findByUsernameIgnoreCase(req.username().trim()).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw ApiException.conflict("Username '" + req.username() + "' is taken"); });
        if (id == null && (req.password() == null || req.password().isBlank()))
            throw ApiException.bad("A password is needed for a new user");
        u.setUsername(req.username().trim());
        u.setFullName(req.fullName().trim());
        u.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim());
        u.setActive(req.active());
        u.setAppraised(req.appraised());
        u.getRoles().clear();
        u.getRoles().addAll(req.roles());
        u.setTeam(req.teamId() == null ? null : teams.findById(req.teamId()).orElseThrow(() -> ApiException.notFound("Team")));
        u.setLevel(req.levelId() == null ? null : levels.findById(req.levelId()).orElseThrow(() -> ApiException.notFound("Level")));
        Set<Team> managed = new HashSet<>();
        if (req.managedTeamIds() != null) req.managedTeamIds().forEach(tid -> managed.add(teams.findById(tid).orElseThrow(() -> ApiException.notFound("Team"))));
        u.getManagedTeams().clear();
        u.getManagedTeams().addAll(managed);
        if (req.password() != null && !req.password().isBlank()) u.setPasswordHash(encoder.encode(req.password()));
        return Mapper.user(users.save(u));
    }

    public void resetPassword(Long id, String password) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        u.setPasswordHash(encoder.encode(password));
    }

    public void deactivate(Long id, AppUser actor) {
        if (actor.getId().equals(id)) throw ApiException.bad("You cannot deactivate yourself");
        users.findById(id).orElseThrow(() -> ApiException.notFound("User")).setActive(false);
    }
}
