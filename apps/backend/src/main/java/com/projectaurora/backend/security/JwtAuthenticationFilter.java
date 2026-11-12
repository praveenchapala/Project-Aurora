package com.projectaurora.backend.security;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7).trim();

        if (jwt.isEmpty()) {
            rejectRequest(response);
            return;
        }

        try {

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                String userEmail = jwtService.extractUsername(jwt);

                if (userEmail == null || userEmail.isBlank()) {
                    rejectRequest(response);
                    return;
                }

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(userEmail);

                if (!jwtService.isTokenValid(jwt, userDetails)) {
                    rejectRequest(response);
                    return;
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());

                authToken.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request));

                SecurityContextHolder.getContext()
                        .setAuthentication(authToken);
            }

        } catch (JwtException | IllegalArgumentException
                | UsernameNotFoundException ex) {

            SecurityContextHolder.clearContext();
            rejectRequest(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rejectRequest(HttpServletResponse response)
            throws IOException {

        SecurityContextHolder.clearContext();

        response.sendError(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Invalid or expired authentication token");
    }
}
