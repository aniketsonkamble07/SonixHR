package com.sonixhr.filter;

import com.sonixhr.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RequestCacheCleanupFilter extends OncePerRequestFilter {

    private final SecurityUtils securityUtils;

    @Override
    protected void doFilterInternal(@org.springframework.lang.NonNull HttpServletRequest request,
                                    @org.springframework.lang.NonNull HttpServletResponse response,
                                    @org.springframework.lang.NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Clear request cache after each request
            securityUtils.clearRequestCache();
        }
    }
}