package com.habittracker.habit_tracker.config;

import com.habittracker.habit_tracker.security.CustomUserDetailsService;
import com.habittracker.habit_tracker.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

/**
 * SecurityConfig — The Central Nervous System of Spring Security
 *
 * Configures the entire security posture of our application:
 * 1. Configures CORS (allows React frontend on port 5173 / 3000)
 * 2. Disables CSRF (not needed for stateless REST APIs using JWT)
 * 3. Sets session management to STATELESS (no HTTP sessions created or used)
 * 4. Defines URL authorization rules (public endpoints vs protected endpoints)
 * 5. Registers our custom JwtAuthenticationFilter into the security filter
 * chain
 * 6. Registers core security Beans: PasswordEncoder, AuthenticationProvider,
 * AuthenticationManager, CorsConfigurationSource
 *
 * Uses modern Spring Security 6 / Spring Boot 3 component-based configuration
 * (no deprecated WebSecurityConfigurerAdapter).
 */
@Configuration // Marks this class as a source of Spring bean definitions
@EnableWebSecurity // Enables Spring Security's web security support and filter chain
@RequiredArgsConstructor // Lombok generates constructor for all final fields (jwtAuthFilter,
                         // userDetailsService)
public class SecurityConfig {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCIES
    // ─────────────────────────────────────────────────────────────

    // Our custom filter that extracts and validates JWT from incoming request
    // headers
    private final JwtAuthenticationFilter jwtAuthFilter;

    // Our custom service that loads user details from the database by email
    private final CustomUserDetailsService userDetailsService;

    // ─────────────────────────────────────────────────────────────
    // 1. SECURITY FILTER CHAIN (Routing, CORS, CSRF, Sessions, Filter Order)
    // ─────────────────────────────────────────────────────────────

    /**
     * Defines the SecurityFilterChain bean that configures HTTP request handling.
     *
     * @param http the HttpSecurity builder to configure
     * @return the built SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean                                       // Tells Spring to manage and inject the returned SecurityFilterChain
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── 1. CONFIGURE CORS (Cross-Origin Resource Sharing) ───────────────
            // Connects our custom CorsConfigurationSource bean to allow browser requests from React
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── 2. DISABLE CSRF (Cross-Site Request Forgery) ─────────────────────
            // CSRF protection relies on browser cookies being sent automatically.
            // Since our REST API is stateless and authenticates via Bearer JWT tokens
            // in headers (which browsers NEVER attach automatically), CSRF is not a risk.
            .csrf(AbstractHttpConfigur
                er::disable)

            // ── 3. CONFIGURE URL AUTHORIZATION RULES ─────────────────────────────
            // Specify which endpoints are public and which require authentication.
            .authorizeHttpRequests(auth -> auth
                // Allow public access to auth routes, error handling, and Swagger/OpenAPI documentation
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/auth/**",
                    "/error",
                    "/v3/api-docs/**",
                    "/v3/api-docs",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Require valid authentication for ANY other endpoint in the application
                .anyRequest().authenticated()
            )

            // ── 3. CONFIGURE STATELESS SESSION MANAGEMENT ────────────────────────
            // Spring Security will never create or use an HttpSession to store user state.
            // Every single request must carry its own authentication (the JWT token).
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── 4. WIRE THE AUTHENTICATION PROVIDER ──────────────────────────────
            // Connects DaoAuthenticationProvider (which uses our CustomUserDetailsService + BCrypt)
            .authenticationProvider(authenticationProvider())

            // ── 5. ADD CUSTOM JWT FILTER BEFORE USERNAME_PASSWORD FILTER ─────────
            // Place JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter.
            // This ensures our token is checked and SecurityContext is populated BEFORE
            // Spring Security checks if the request is authorized.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // Build and return the configured SecurityFilterChain object
        return http.build();
    }

    // ─────────────────────────────────────────────────────────────
    // 2. PASSWORD ENCODER BEAN
    // ─────────────────────────────────────────────────────────────

    /**
     * PasswordEncoder bean using BCrypt strong hashing algorithm.
     * BCrypt automatically handles salt generation and is computationally secure.
     *
     * Used by:
     * - AuthService: to hash raw passwords during user registration
     * - DaoAuthenticationProvider: to verify raw login passwords against stored
     * hashes
     *
     * @return BCryptPasswordEncoder instance
     */
    @Bean // Registers PasswordEncoder in Spring's ApplicationContext
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Uses default strength (10 rounds of hashing)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. AUTHENTICATION PROVIDER BEAN
    // ─────────────────────────────────────────────────────────────

    /**
     * DaoAuthenticationProvider is Spring Security's standard
     * AuthenticationProvider
     * for username/password authentication using a UserDetailsService and
     * PasswordEncoder.
     *
     * How it works:
     * 1. Calls userDetailsService.loadUserByUsername(email) to fetch UserDetails
     * from DB
     * 2. Uses passwordEncoder to verify raw password against DB-stored hashed
     * password
     * 3. If valid, returns an authenticated Authentication object
     *
     * @return configured DaoAuthenticationProvider
     */
    @Bean // Registers AuthenticationProvider bean in Spring Context
    public AuthenticationProvider authenticationProvider() {
        // Create an instance of DaoAuthenticationProvider
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        // Set the custom service that retrieves user details from MySQL
        authProvider.setUserDetailsService(userDetailsService);
        // Set the password encoder used to check hashed passwords
        authProvider.setPasswordEncoder(passwordEncoder());
        // Return the fully configured provider
        return authProvider;
    }

    // ─────────────────────────────────────────────────────────────
    // 4. AUTHENTICATION MANAGER BEAN
    // ─────────────────────────────────────────────────────────────

    /**
     * Exposes Spring Security's AuthenticationManager as a Spring Bean.
     *
     * In Spring Security 6, we obtain the AuthenticationManager from the
     * AuthenticationConfiguration helper rather than overriding a configurer
     * method.
     *
     * Used by:
     * - AuthService: authManager.authenticate(new
     * UsernamePasswordAuthenticationToken(email, password))
     *
     * @param config Spring's AuthenticationConfiguration
     * @return the managed AuthenticationManager instance
     * @throws Exception if AuthenticationManager cannot be retrieved
     */
    @Bean // Exposes AuthenticationManager to be autowired in AuthService
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        // Retrieve and return the centrally configured AuthenticationManager
        return config.getAuthenticationManager();
    }

    // ─────────────────────────────────────────────────────────────
    // 5. CORS CONFIGURATION SOURCE BEAN
    // ─────────────────────────────────────────────────────────────

    /**
     * Defines the CORS configuration rules for our application.
     *
     * Tells web browsers that requests originating from our React frontend
     * (running on http://localhost:5173 or http://localhost:3000) are trusted
     * and allowed to access backend endpoints, send JWT Bearer tokens, and parse
     * responses.
     *
     * @return configured CorsConfigurationSource
     */
    @Bean // Registers CORS configuration in Spring Security filter chain
    public CorsConfigurationSource corsConfigurationSource() {
        // Create a new CorsConfiguration object
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow frontend origins (Vite default port 5173 and alternate React port 3000)
        configuration
                .setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173"));

        // Allow standard REST HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Allow standard HTTP headers including Authorization for JWT and Content-Type
        // for JSON
        configuration.setAllowedHeaders(
                Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));

        // Allow browser credentials such as Authorization headers
        configuration.setAllowCredentials(true);

        // Cache pre-flight OPTIONS response for 1 hour (3600 seconds) to avoid
        // redundant requests
        configuration.setMaxAge(3600L);

        // Register this CORS configuration for all endpoints across the entire
        // application
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        // Return the configured source
        return source;
    }
}
