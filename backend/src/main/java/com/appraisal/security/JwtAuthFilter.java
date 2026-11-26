package com.appraisal.security;

import com.appraisal.domain.AppUser;
import com.appraisal.repo.AppUserRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AppUserRepo users;

    public JwtAuthFilter(JwtService jwt, AppUserRepo users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long id = jwt.parse(header.substring(7));
            if (id != null) {
                users.findById(id).filter(AppUser::isActive).ifPresent(u -> {
                    var authorities = u.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
                    var auth = new UsernamePasswordAuthenticationToken(u.getId(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }
        chain.doFilter(req, res);
    }
}
