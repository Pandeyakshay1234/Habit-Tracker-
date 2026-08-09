package com.habittracker.habit_tracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter — The Security Gatekeeper
 *
 * Runs ONCE on every incoming HTTP request (before it hits any Controller).
 * Its job: check if the request carries a valid JWT token.
 * If yes → tell Spring Security "this user is authenticated".
 * If no  → do nothing (Spring Security will block protected routes later).
 *
 * Flow:
 *   1. Read "Authorization" header → look for "Bearer <token>"
 *   2. Extract email from token (using JwtUtil)
 *   3. Load user from DB (using CustomUserDetailsService)
 *   4. Validate token (email match + not expired)
 *   5. Set SecurityContext → Spring knows who this user is for the rest of this request
 *   6. Pass request down the filter chain
 *
 * Extends OncePerRequestFilter:
 *   Guarantees this filter runs exactly once per request (not multiple times
 *   if there are request dispatches/forwards internally).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCIES
    // ─────────────────────────────────────────────────────────────

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    // ─────────────────────────────────────────────────────────────
    // CORE FILTER LOGIC
    // ─────────────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain        // the rest of the filter chain
    ) throws ServletException, IOException {

        // ── STEP 1: Read the Authorization header ─────────────────
        // Expected format: "Authorization: Bearer eyJhbGci..."
        final String authHeader = request.getHeader("Authorization");

        // If there's no Authorization header, or it doesn't start with "Bearer ",
        // this request has no JWT → skip our logic, pass it along.
        // Spring Security will then block it if the route requires authentication.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;  // ← early return: nothing more to do here
        }

        // ── STEP 2: Extract the JWT from the header ───────────────
        // "Bearer " is 7 characters → substring(7) gives us just the token
        final String jwt = authHeader.substring(7);

        // ── STEP 3: Extract the email from the token ──────────────
        // JwtUtil parses the token and reads the "subject" claim (which is the email).
        // If the token is malformed or expired, JwtUtil throws a JwtException here.
        // (GlobalExceptionHandler can catch it, or Spring Security blocks the request.)
        final String email = jwtUtil.extractEmail(jwt);

        // ── STEP 4: Check SecurityContext ─────────────────────────
        // SecurityContextHolder.getContext().getAuthentication() returns:
        //   - null  → no one is authenticated yet for this request
        //   - non-null → already authenticated (e.g., by a previous filter)
        //
        // We only proceed if:
        //   a) email was successfully extracted from token (email != null)
        //   b) no one is already authenticated for this request
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // ── STEP 5: Load user from DB ──────────────────────────
            // CustomUserDetailsService goes to MySQL, fetches the user by email.
            // Returns Spring's UserDetails (email + hashed password + authorities).
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // ── STEP 6: Validate the token ─────────────────────────
            // JwtUtil checks two things:
            //   1. Email in token matches the loaded user's email
            //   2. Token has not expired
            if (jwtUtil.isTokenValid(jwt, userDetails)) {

                // ── STEP 7: Create authentication object ──────────
                // UsernamePasswordAuthenticationToken is Spring Security's standard
                // way to represent an authenticated user.
                //
                // Constructor args:
                //   1. principal   → the UserDetails (who is this?)
                //   2. credentials → null (we don't store the password here — already verified)
                //   3. authorities → the user's roles/permissions
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                          // credentials = null after authentication
                                userDetails.getAuthorities()   // roles/permissions (empty list for now)
                        );

                // Attach request details (IP address, session ID) to the auth token.
                // Used by Spring Security for audit logging and session management.
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // ── STEP 8: Set SecurityContext ────────────────────
                // This is the key line. It tells Spring Security:
                // "For this request, this user is authenticated."
                // Every subsequent call to SecurityContextHolder.getContext().getAuthentication()
                // in this request's lifecycle will return this auth token.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
            // If token is invalid → we don't set the context → request stays unauthenticated
            // → Spring Security will block the request if the route needs authentication
        }

        // ── STEP 9: Continue the filter chain ─────────────────────
        // ALWAYS call this — it passes the request to the next filter or the Controller.
        // If we don't call it, the request is silently dropped (very bad!).
        filterChain.doFilter(request, response);
    }
}
