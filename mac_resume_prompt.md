# 🔁 Habit Tracker — Mac Resume Prompt

> Paste this entire prompt into a new Antigravity conversation on your MacBook.
> Everything will continue exactly where you left off.

---

## PASTE THIS INTO NEW CONVERSATION:

---

I am building a **Spring Boot Habit Tracker backend** as a learning project (for interviews + real experience). I was working on this with you on my Windows laptop and switching to Mac — I want to continue exactly where we left off.

### Our Working Style
- **Learn first, then implement** — before every file, we discuss the "why" behind every design decision
- **Inline comments on every line** of code so I understand exactly what each line does
- **Revision notes** (`revision_notes.md` in the project root) — updated after every step with theory, interview Q&As, and implementation notes. This is my study document.
- We follow a **step-by-step roadmap**, one file at a time, with a git commit after each step

---

### Project Details

**Tech Stack:**
- Java 17 + Spring Boot 3.2.4
- Spring Security + JWT (JJWT 0.12.3)
- Spring Data JPA + Hibernate + MySQL
- Maven, Lombok, Bean Validation

**GitHub Repo:** `https://github.com/Pandeyakshay1234/Habit-Tracker-.git`

**Package root:** `com.habittracker.habit_tracker`

**API prefix:** `/api/v1` (set in application.properties)

**Architecture:** Layered — `controller → service → repository → entity`

---

### Package Structure

```
com.habittracker.habit_tracker
├── config/                    ← (empty, coming in Step 10)
├── controller/                ← (empty, coming in Step 12)
├── dto/
│   ├── AuthResponse.java      ✅
│   ├── HabitLogResponse.java  ✅
│   ├── HabitRequest.java      ✅
│   ├── HabitResponse.java     ✅
│   ├── LoginRequest.java      ✅
│   └── RegisterRequest.java   ✅
├── entity/
│   ├── User.java              ✅
│   ├── Habit.java             ✅
│   └── HabitLog.java         ✅
├── exception/
│   ├── DuplicateResourceException.java   ✅
│   ├── ErrorResponse.java                ✅
│   ├── GlobalExceptionHandler.java       ✅
│   └── ResourceNotFoundException.java    ✅
├── repository/
│   ├── UserRepository.java    ✅
│   ├── HabitRepository.java   ✅
│   └── HabitLogRepository.java ✅
├── security/
│   ├── JwtUtil.java                    ✅  (JJWT 0.12.3 API)
│   ├── CustomUserDetailsService.java   ✅
│   └── JwtAuthenticationFilter.java    ✅
└── HabitTrackerApplication.java        ✅
```

---

### Completed Steps

| Step | File(s) | Status |
|---|---|---|
| Step 1 | `pom.xml`, `application.properties`, `HabitTrackerApplication.java` | ✅ |
| Step 2 | `User.java`, `Habit.java`, `HabitLog.java` | ✅ |
| Step 3 | `UserRepository`, `HabitRepository`, `HabitLogRepository` | ✅ |
| Step 4 | All DTOs | ✅ |
| Step 5 | Custom exceptions + `GlobalExceptionHandler` | ✅ |
| Step 6 | Theory documented in revision_notes | ✅ |
| Step 7 | `JwtUtil.java` (JJWT 0.12.3 — uses `Jwts.parser()` not `parserBuilder()`) | ✅ |
| Step 8 | `CustomUserDetailsService.java` | ✅ |
| Step 9 | `JwtAuthenticationFilter.java` | ✅ |

---

### JWT Authentication Roadmap

```
✅ Step 7  → JwtUtil.java                  (generate & validate tokens)
✅ Step 8  → CustomUserDetailsService.java  (load user from DB)
✅ Step 9  → JwtAuthenticationFilter.java   (intercept every request)
⏳ Step 10 → SecurityConfig.java            (wire everything, define routes)  ← WE ARE HERE
⏳ Step 11 → AuthService.java               (register & login logic)
⏳ Step 12 → AuthController.java            (/auth/register, /auth/login)
```

---

### Important Notes / Decisions Made

1. **JJWT 0.12.3 API** — All old tutorials show 0.11.x. We use the new API:
   - `Jwts.parser()` not `parserBuilder()`
   - `.verifyWith(key)` not `.setSigningKey(key)`
   - `.parseSignedClaims(token)` not `parseClaimsJws(token)`
   - `.getPayload()` not `.getBody()`
   - `signWith(key)` not `signWith(key, SignatureAlgorithm.HS256)`

2. **No `@Data` on JPA entities** — causes LazyInitializationException. Use `@Getter + @Setter` only.

3. **`@Builder.Default`** on list fields in entities — without it, builder sets lists to null.

4. **`LAZY` fetch** on all `@ManyToOne` — prevents N+1 query problem.

5. **DTOs use Java 17 `record`** — not class. Immutable, no boilerplate.

6. **`UsernamePasswordAuthenticationToken` 3-arg constructor** — the 3rd arg (authorities) is what sets `authenticated = true` in Spring Security.

7. **`filterChain.doFilter()` must always be called** in the filter — even on invalid tokens.

---

### What To Do Next

**Please continue with Step 10 — `SecurityConfig.java`** using the same style:
- Explain the "why" before showing the code
- Add inline comments on every line
- Use `@Bean` configuration style (not `extends WebSecurityConfigurerAdapter` — that's deprecated)
- Make `/api/v1/auth/**` PUBLIC, all other routes PROTECTED
- Use `STATELESS` session policy (we use JWT, no sessions)
- Wire in `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
- Register `BCryptPasswordEncoder` as a bean
- Register `AuthenticationManager` as a bean
- After implementation, update `revision_notes.md` with theory + commit message

---
