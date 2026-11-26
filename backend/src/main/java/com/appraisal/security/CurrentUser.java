package com.appraisal.security;

import com.appraisal.config.ApiException;
import com.appraisal.domain.AppUser;
import com.appraisal.repo.AppUserRepo;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final AppUserRepo users;

    public CurrentUser(AppUserRepo users) {
        this.users = users;
    }

    public AppUser get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not signed in");
        }
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not signed in"));
    }
}
