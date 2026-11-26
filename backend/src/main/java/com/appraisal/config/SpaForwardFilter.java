package com.appraisal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * When the React build is bundled into the jar (static/index.html), browser routes such as
 * /admin/questions are served index.html so a page refresh works. API calls and files pass through.
 */
@Component
public class SpaForwardFilter extends OncePerRequestFilter {
    private final boolean bundled = new ClassPathResource("static/index.html").exists();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (bundled && "GET".equals(req.getMethod()) && !path.startsWith("/api") && !path.equals("/")
                && !last.contains(".")) {
            req.getRequestDispatcher("/index.html").forward(req, res);
            return;
        }
        chain.doFilter(req, res);
    }
}
