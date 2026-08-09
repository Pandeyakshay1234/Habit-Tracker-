package com.habittracker.habit_tracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JwtUtil — Token Factory
 *
 * Responsibilities:
 *  1. Generate a JWT token for an authenticated user
 *  2. Extract the email (subject) from a token
 *  3. Validate a token (correct user + not expired)
 *  4. Provide the signing key from config
 *
 * ⚠️ JJWT 0.12.x API (NOT 0.11.x):
 *   OLD: Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody()
 *   NEW: Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()
 */
@Component
public class JwtUtil {

    // ─────────────────────────────────────────────────────────────
    // CONFIG VALUES (read from application.properties)
    // ─────────────────────────────────────────────────────────────

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    // ─────────────────────────────────────────────────────────────
    // 1. GENERATE TOKEN
    // ─────────────────────────────────────────────────────────────

    /**
     * Generates a JWT token with no extra claims.
     * The subject (identifier) is the user's email.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a JWT token with optional extra claims.
     * Extra claims can carry additional data (e.g. roles, userId) if needed in future.
     *
     * JJWT 0.12.x: signWith(key) — algorithm is automatically inferred from the key type.
     * No need to pass SignatureAlgorithm.HS256 explicitly anymore.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts
                .builder()
                .claims(extraClaims)                                         // set extra claims first
                .subject(userDetails.getUsername())                           // email stored as subject
                .issuedAt(new Date(System.currentTimeMillis()))               // issued right now
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration)) // expires after configured time
                .signWith(getSigningKey())                                    // JJWT 0.12.x: no algorithm needed
                .compact();                                                   // build into compact String
    }

    // ─────────────────────────────────────────────────────────────
    // 2. VALIDATE TOKEN
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns true if:
     *  - The email in the token matches the given UserDetails
     *  - The token has not expired
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return (email.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /**
     * Returns true if the token's expiration date is before now.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ─────────────────────────────────────────────────────────────
    // 3. EXTRACT CLAIMS FROM TOKEN
    // ─────────────────────────────────────────────────────────────

    /**
     * Extracts the email (stored as subject) from the token.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the expiration date from the token.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic claim extractor — avoids repeating parse logic for every field.
     * Uses a Function (method reference or lambda) to pick which claim to return.
     *
     * Example: extractClaim(token, Claims::getSubject)
     *          extractClaim(token, claims -> claims.get("role", String.class))
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses the full JWT and returns all claims inside the payload.
     * Also verifies the signature — if tampered, throws JwtException.
     *
     * JJWT 0.12.x changes:
     *   OLD: .parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody()
     *   NEW: .parser().verifyWith(key).build().parseSignedClaims(token).getPayload()
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()                          // 0.12.x: parser() replaces parserBuilder()
                .verifyWith(getSigningKey())        // 0.12.x: verifyWith() replaces setSigningKey()
                .build()
                .parseSignedClaims(token)          // 0.12.x: parseSignedClaims() replaces parseClaimsJws()
                .getPayload();                     // 0.12.x: getPayload() replaces getBody()
    }

    // ─────────────────────────────────────────────────────────────
    // 4. SIGNING KEY
    // ─────────────────────────────────────────────────────────────

    /**
     * Decodes the Base64-encoded secret from application.properties
     * and converts it into a SecretKey object that JJWT uses for signing.
     *
     * JJWT 0.12.x: return type is SecretKey (not Key) — used by verifyWith() and signWith().
     * Why Base64? Raw secret bytes are not safe to store as plain text in config files.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
