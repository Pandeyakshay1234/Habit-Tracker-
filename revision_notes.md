# Habit Tracker — Core Revision Notes

> **Purpose:** Interview prep + deep understanding. Read this alongside the code, not inside it.
> Updated after every step.

---

## Phase 1 · Step 1 — Project Setup

--- 

### 1. Maven & `pom.xml`

**What is Maven?**
A build tool. Does 3 things: downloads dependencies, compiles code, packages your app into a JAR.

**What is `pom.xml`?**
Project Object Model. Describes your project identity and all its dependencies in one file.

---

### 2. `spring-boot-starter-parent`

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.4</version>
</parent>
```

- Pre-defines **compatible versions** for ALL Spring libraries — you never get version conflicts
- Sets up Maven compiler and build defaults automatically
- Think of it as: *"I inherit all sensible defaults from Spring Boot's team"*

> **Interview Q:** *"Why use spring-boot-starter-parent?"*
> "It manages dependency versions centrally. Without it, I'd manually ensure every library is mutually compatible. Spring Boot's team has already verified those combinations."

---

### 3. Every Dependency — What, Why, Why Not Others

#### `spring-boot-starter-web`
- Gives you: **embedded Tomcat** + Spring MVC + Jackson (JSON ↔ Java converter)
- Old Java: deploy WAR to external Tomcat server. Spring Boot: Tomcat is *inside* your JAR. Just run `java -jar app.jar`
- **Why not Jetty?** Tomcat is default and most battle-tested. Companies expect it.

#### `spring-boot-starter-data-jpa`
- **JPA** = specification (rules/interfaces) for mapping Java objects → DB tables
- **Hibernate** = implementation of JPA (the actual SQL-generating engine)
- **Spring Data JPA** = sits on top, auto-generates SQL from method names: `findByEmail()`
- **Why not plain JDBC?** JDBC is low-level — raw SQL, manual connection handling, manual ResultSet mapping. JPA abstracts all that.
- **Why not MyBatis?** Also popular but JPA is more expected in Spring interviews.

#### `mysql-connector-j` · scope: `runtime`
- The physical connection driver between Java and MySQL
- Hibernate uses it internally — your code never imports MySQL classes directly
- **Why runtime scope?** Your code compiles without it. It's only needed when the app actually runs.

#### `spring-boot-starter-security`
- **What it does immediately:** blocks ALL endpoints (returns 401) by default
- Philosophy: *deny all, allow specific* — the safest default
- Gives you: BCryptPasswordEncoder, security filter chain, authentication infrastructure
- Customized in Step 5 to make `/auth/register` and `/auth/login` public

#### `jjwt-api` + `jjwt-impl` + `jjwt-jackson`
- 3 artifacts = API (stable interface you code against) separated from implementation (can change without breaking your code)
- `jjwt-api` → compile scope (you import these)
- `jjwt-impl`, `jjwt-jackson` → runtime scope (never imported directly)
- **Why JJWT?** Most popular JWT library for Java. Well-maintained.
- **Why not Spring Security OAuth2?** Overkill. JJWT gives direct, understandable control.

#### `spring-boot-starter-validation`
- Standard: Bean Validation (JSR-380). Implementation: Hibernate Validator.
- Write `@NotBlank @Email` on DTO fields → Spring validates before your method runs (with `@Valid`)
- **Why?** Without it: manual null/empty/format checks in every service method. Error-prone and repetitive.

#### `spring-boot-starter-mail`
- Wraps the complex JavaMail API into a simple `JavaMailSender` interface
- Uses SMTP protocol to talk to Gmail or any mail server
- Used in Step 10 for 9 PM habit reminder emails

#### `lombok`
- Annotation processor — runs at **compile time**, generates code invisibly in bytecode
- Key annotations: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`, `@Data`
- `optional=true` → excluded from final JAR (it's done its job by then)
- **Why?** Entity class without Lombok = 100+ lines. With Lombok = 20 lines.

#### `spring-boot-starter-test` · scope: `test`
- Bundles: JUnit 5, Mockito, AssertJ, MockMvc, Spring Test
- `test` scope = NOT in final JAR. Only on classpath during test execution.

---

### 4. Maven Dependency Scopes

| Scope | At compile? | At runtime? | In final JAR? |
|---|---|---|---|
| `compile` (default) | ✅ | ✅ | ✅ |
| `runtime` | ❌ | ✅ | ✅ |
| `test` | ✅ tests only | ✅ tests only | ❌ |
| `optional` | ✅ | ✅ | ❌ |

---

### 5. `spring-boot-maven-plugin`

Packages your app as a **fat JAR** — one JAR with ALL dependencies inside.
You can run `java -jar habit-tracker.jar` anywhere without Maven installed.
Lombok excluded from JAR → already did its work at compile time.

---

### 6. `application.properties` — Key Decisions

#### `server.servlet.context-path=/api/v1`
Every endpoint is auto-prefixed. Controller writes `@GetMapping("/habits")` → becomes `/api/v1/habits`.
**Why versioning?** Ship `/api/v2` later without breaking clients still on `/api/v1`.

#### `spring.jpa.hibernate.ddl-auto`

| Value | What happens | Use when |
|---|---|---|
| `update` | Adds missing tables/columns, never drops | **Development** ✅ |
| `validate` | Fails if schema doesn't match entities | Staging |
| `none` | Does nothing to schema | Production |
| `create-drop` | Creates on start, drops on stop | Tests |

We use `update` now. Phase 2 introduces Flyway for proper versioned migrations.

#### `spring.jpa.show-sql=true`
Prints every SQL Hibernate generates. Essential for learning and debugging.
**Off in production** — performance hit + potential info leak.

#### `app.jwt.secret` / `app.jwt.expiration`
Custom properties (not Spring defaults). Read in code via `@Value("${app.jwt.secret}")`.
**In production:** inject as environment variables. Never commit real values to Git.

#### Mail: port 587 + STARTTLS vs port 465 + SSL
- Port 465 → SSL-wrapped from the start
- Port 587 → starts plain, upgrades to TLS via STARTTLS command

We use **587 + STARTTLS** — the modern standard, required by Gmail.

---

### 7. Project Package Structure — Layered Architecture

```
com.habittracker.habit_tracker
├── config/       ← Security config, Bean definitions
├── controller/   ← REST endpoints, HTTP in/out
├── dto/          ← Request/response shapes (never expose entities directly)
├── entity/       ← JPA entities = DB table mappings
├── exception/    ← Custom exceptions + global error handler
├── repository/   ← Spring Data JPA interfaces, DB queries
├── security/     ← JWT filter, JWT utility
└── service/      ← Business logic (the brain)
```

**Request flow:** `HTTP Request → Controller → Service → Repository → DB → back`

Each layer has **one responsibility**. You can swap the DB layer without touching the Controller.

> **Interview Q:** *"What is separation of concerns?"*
> "Each layer owns one job. Controllers handle HTTP. Services handle logic. Repositories handle DB. They're loosely coupled — I can change one without breaking others."

---

### 8. `@SpringBootApplication` — 3 Annotations in 1

| Hidden annotation | What it does | 
|---|---|
| `@SpringBootConfiguration` | Marks this as primary Spring config class |
| `@EnableAutoConfiguration` | Reads classpath → auto-creates DataSource, EntityManager, etc. |
| `@ComponentScan` | Scans this package + all sub-packages for Spring-managed classes |

**Why main class must be in the root package:** `@ComponentScan` scans *downward*. Sub-packages are found. Sibling packages are missed.

---

### 9. `@EnableScheduling`

Activates Spring's task scheduler. Without it, `@Scheduled` methods (Step 10) silently do nothing.
Added in main class now so it's not forgotten.

---

### ✅ Step 1 — Git Commit

```bash
git add .
git commit -m "Step 1: Project setup - pom.xml, application.properties, main class"
```

**Before committing:** passwords in `application.properties` must be placeholders.
Add real credentials to `application-local.properties` and add that file to `.gitignore`.

---

### 🧪 Manual Tests After Step 1

1. `mvn dependency:resolve` → all JARs download, no errors
2. `mvn compile` → compiles cleanly
3. `mvn spring-boot:run` → see `Tomcat started on port 8080` in console
   *(DB connection error is expected if MySQL isn't running — that's fine)*

---

> **Next:** Step 2 — Database Entities (User, Habit, HabitLog)

---

## Phase 1 · Step 2 — Database Entities

---

### 1. Core JPA Annotations — What Every One Does

| Annotation | What it does |
|---|---|
| `@Entity` | Tells Hibernate: "this class maps to a DB table" |
| `@Table(name = "...")` | Sets the exact table name. Without it, Hibernate uses the class name (e.g., `User` → table `user`) |
| `@Id` | Marks the primary key field |
| `@GeneratedValue(strategy = GenerationType.IDENTITY)` | DB auto-increments the id (MySQL AUTO_INCREMENT). You never set id manually. |
| `@Column(nullable = false)` | Generates `NOT NULL` constraint in DDL. Hibernate also validates before inserting. |
| `@Column(unique = true)` | Generates `UNIQUE` constraint. Used on email — no two users share an email. |
| `@Column(updatable = false)` | Hibernate never sends this column in UPDATE statements. Used on `createdAt`. |
| `@CreationTimestamp` | Hibernate sets this field to current timestamp automatically on INSERT. You never set it. |
| `@UpdateTimestamp` | Hibernate updates this field to current timestamp automatically on every UPDATE. |

---

### 2. Why `@Table(name = "users")` and not `User`?

`user` is a **reserved keyword in MySQL**. If you let Hibernate name the table `user`, queries will fail with a SQL syntax error.
Always name it `users` (plural) explicitly.

---

### 3. Lombok on Entities — What Each Annotation Does

| Annotation | Generates |
|---|---|
| `@Getter` | `getId()`, `getName()`, etc. for every field |
| `@Setter` | `setId()`, `setName()`, etc. |
| `@NoArgsConstructor` | `new User()` — required by JPA spec (Hibernate needs it to instantiate entities) |
| `@AllArgsConstructor` | `new User(id, email, password, ...)` — used internally by `@Builder` |
| `@Builder` | `User.builder().email("a@b.com").name("X").build()` — clean object creation |

> **Why not `@Data` on entities?**
> `@Data` includes `@EqualsAndHashCode` which generates `equals()` using ALL fields including `habits` (the list).
> If you call `equals()` on a User, it traverses the `habits` list, which triggers a DB query (lazy load), which can cause infinite loops or `LazyInitializationException`.
> **Rule:** Never use `@Data` on JPA entities. Use `@Getter + @Setter` only.

---

### 4. `@Builder.Default` — Why It's Required

When you use `@Builder` and have a field with a default value:
```java
private List<Habit> habits = new ArrayList<>();
```
Lombok's `@Builder` **ignores** field initializers by default. If you do `User.builder().build()`, `habits` will be `null`, not an empty list.

`@Builder.Default` tells Lombok: "use this initializer as the default in the builder too."

Same for `streakFreezeTokens = 1` — without `@Builder.Default`, it would be `0`.

---

### 5. Relationships — ManyToOne vs OneToMany

**The mental model:**
- One **User** has many **Habits** → User side: `@OneToMany`, Habit side: `@ManyToOne`
- One **Habit** has many **HabitLogs** → Habit side: `@OneToMany`, HabitLog side: `@ManyToOne`

**Who owns the relationship?**
Always the `@ManyToOne` side (the one with the foreign key column in the DB).
`@OneToMany(mappedBy = "user")` → "the `user` field in the Habit class owns/controls this relationship."

**Why `@JoinColumn(name = "user_id")`?**
This tells Hibernate the exact foreign key column name in the `habits` table. Without it, Hibernate generates a name like `user_id` anyway, but being explicit prevents surprises.

---

### 6. `FetchType.LAZY` — Critical Performance Decision

```java
@ManyToOne(fetch = FetchType.LAZY)
private User user;
```

**EAGER (the dangerous default for `@ManyToOne`):**
Every time you load a `Habit`, Hibernate immediately also runs a second SQL query to load the full `User`. Even if you don't need it.

**LAZY:**
Hibernate returns a proxy object. The real `User` data is only fetched if you actually call `habit.getUser().getName()`.

**Why LAZY?**
Performance. Loading 100 habits would also fire 100 user queries (called the N+1 problem).

> **Interview Q:** *"What is the N+1 problem?"*
> "If I fetch 100 habits and each triggers a separate query to load its user, that's 1 (habits) + 100 (users) = 101 queries. LAZY loading prevents this by not loading related entities until explicitly accessed."

---

### 7. `cascade = CascadeType.ALL` + `orphanRemoval = true`

**Cascade:** when you do an operation on a parent, propagate it to children.
`CascadeType.ALL` = if I save/delete/merge User → automatically save/delete/merge all their Habits.

**orphanRemoval = true:** if you remove a Habit from `user.getHabits()` list (in Java), Hibernate automatically deletes that Habit row from the DB.

Without `orphanRemoval`: the Habit becomes an "orphan" — no user owns it, but it stays in the DB.

---

### 8. Why `LocalDate` for `logDate` in HabitLog?

`LocalDate` = just a date: `2024-04-19`. No time, no timezone.
`LocalDateTime` = date + time: `2024-04-19T21:30:00`.

We log habits by **day**, not by exact time. Using `LocalDate`:
- Simpler unique constraint (habit + date, not habit + date + time)
- No timezone confusion — "logging on April 19" means April 19 everywhere
- Streak calculation is date arithmetic — just comparing dates, not times

---

### 9. Why `@UniqueConstraint` in HabitLog?

```java
@Table(
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"habit_id", "log_date"},
        name = "uk_habit_log_date"
    )
)
```

This enforces at the **database level** that you cannot log the same habit twice on the same day.

**Two-layer protection:**
1. Service layer: checks in code before inserting (throws `DuplicateLogException`)
2. DB constraint: even if the code has a bug, the DB rejects duplicate inserts

**Why name the constraint?** `uk_habit_log_date` — named constraints appear in error messages. Unnamed constraints get ugly auto-generated names. Naming them makes debugging easy.

---

### 10. Why Not Store Streak Count in the DB?

We do NOT have a `currentStreak` or `longestStreak` column on `Habit`.

**Why?**
Streaks are **derived data** — they can always be calculated from `HabitLog` records.
Storing derived data = risk of it going out of sync. Example: if a log is deleted, you'd need to remember to also recalculate and update the streak column. Bugs guaranteed.

**Instead:** Calculate streak dynamically in the service layer (Step 8) by reading HabitLogs.
This is always correct because the source of truth is the logs themselves.

---

### 11. `streakFreezeTokens` on User

Business rule: user gets 1 freeze token per week. Stored on the User entity.
A scheduler (Step 10) adds 1 token per week (capped at some max, e.g., 2).
When user misses a day but has tokens, 1 token is consumed and streak is preserved.

---

### DB Schema — What Hibernate Will Generate

```sql
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password      VARCHAR(255) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    streak_freeze_tokens INT DEFAULT 1,
    created_at    DATETIME,
    updated_at    DATETIME
);

CREATE TABLE habits (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    user_id     BIGINT NOT NULL,
    created_at  DATETIME,
    updated_at  DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE habit_logs (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    habit_id          BIGINT NOT NULL,
    log_date          DATE NOT NULL,
    used_streak_freeze TINYINT(1) DEFAULT 0,
    created_at        DATETIME,
    FOREIGN KEY (habit_id) REFERENCES habits(id),
    CONSTRAINT uk_habit_log_date UNIQUE (habit_id, log_date)
);
```

---

### ✅ Step 2 — Git Commit

```bash
git add .
git commit -m "Step 2: Add User, Habit, HabitLog entities with JPA mappings"
```

### 🧪 Manual Tests After Step 2

1. Make sure MySQL is running and the DB `habit_tracker_db` exists (or `createDatabaseIfNotExist=true` will create it)
2. Update `application.properties` with your real MySQL password
3. Run `mvn spring-boot:run`
4. Check console for Hibernate DDL — you should see `CREATE TABLE` or `ALTER TABLE` statements
5. Open MySQL Workbench / DBeaver and verify 3 tables were created with correct columns and constraints

---

> **Next:** Step 3 — Repository Layer (UserRepository, HabitRepository, HabitLogRepository)

---

## Phase 1 · Step 3 — Repository Layer

---

### 1. What is a Repository?

A Spring Data JPA repository is an **interface** (not a class). You define what queries you need — Spring generates the actual SQL and implementation at startup.
No SQL, no JDBC, no boilerplate.

### 2. `Optional<T>` — Why It's Used Instead of Returning Null

`Optional<User>` forces the caller to handle the "not found" case explicitly.

```java
// BAD (old way — NullPointerException risk):
User user = userRepository.findByEmail(email);
user.getName(); // → NPE if user is null

// GOOD (Optional forces handling):
userRepository.findByEmail(email)
    .orElseThrow(() -> new RuntimeException("User not found"));
```

---

## Phase 1 · Step 4 — DTOs (Data Transfer Objects)

---

### 1. What is a DTO and why do we need it?

DTO stands for Data Transfer Object. It is an object used to encapsulate data and send it from one subsystem of an application to another (e.g., from the Backend to the Frontend).

**Why not return Entities directly?**
1. **Security/Privacy:** A `User` entity has a `password` field. If you return it directly in a REST API, you leak passwords (even hashed ones).
2. **Infinite Recursion (StackOverflowError):** A `User` has a list of `Habits`. A `Habit` has a `User`. If Jackson tries to convert `User` to JSON, it loads Habits, which load User, which load Habits... forever.
3. **Decoupling:** Your API contract should not change just because you added a column to your database table.

### 2. Java 17 `record` vs `class`

We used Java 17 `record` for all our DTOs.
```java
public record RegisterRequest(String name, String email, String password) {}
```
**Why?**
Records are designed specifically to be immutable data carriers. You get `equals()`, `hashCode()`, `toString()`, and getters (`name()`, `email()`) completely for free without using Lombok or writing boilerplate. 

### 3. Validation Annotations (`@NotBlank`, `@Email`, `@Size`)

On `RegisterRequest`, we added validation:
```java
@NotBlank(message = "Email is required")
@Email(message = "Invalid email format")
String email;
```
When a user hits the registration API, Spring Boot will automatically intercept the JSON, convert it to this DTO, and check these rules before our Service layer even starts executing. If validation fails, it immediately returns a 400 Bad Request.

> **Interview Q:** *"How do you handle input validation?"*
> "I use Java Bean Validation (JSR-380) annotations like `@NotBlank` directly on my DTOs. This keeps validation logic out of my Service classes and ensures bad data is rejected at the Controller layer."

---

## Phase 1 · Step 5 — Custom Exceptions & Global Error Handling

---

### 1. Why do we need Global Error Handling?

Without a global handler, if your code throws an exception, Spring Boot returns a giant, ugly HTML stack trace to the frontend, or a messy JSON object that changes structure depending on the error.

Frontend teams HATE unpredictable error responses. They want a **consistent, standard JSON format** for *every* error.

### 2. `ErrorResponse` DTO

We created a custom `ErrorResponse` record:
```json
{
  "timestamp": "2026-04-19T14:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Habit with id 5 not found",
  "path": "/api/v1/habits/5"
}
```
Now, the frontend can always expect this exact shape.

### 3. `@RestControllerAdvice`

This is a Spring Boot "interceptor". It wraps around *all* your controllers.
If a controller throws an exception, the `@RestControllerAdvice` catches it *before* it reaches the user, converts it into our nice `ErrorResponse` JSON, and sends it back.

### 4. Custom Exceptions

We created two custom exceptions:
1. `ResourceNotFoundException`: Thrown when a User or Habit ID doesn't exist (returns 404).
2. `DuplicateResourceException`: Thrown when an email is taken or a habit is logged twice in one day (returns 409 Conflict).

By extending `RuntimeException`, we don't have to add `throws` to every method signature.

### 5. Handling Validation Errors (`MethodArgumentNotValidException`)

When our `@NotBlank` or `@Email` checks fail on a DTO, Spring throws a `MethodArgumentNotValidException`. 
Our GlobalExceptionHandler intercepts this and extracts the specific field errors into a map.
```json
{
  "status": 400,
  "message": "Validation failed",
  "validationErrors": {
    "email": "Invalid email format",
    "password": "Password must be at least 6 characters"
  }
}
```
This makes it incredibly easy for the React frontend to display specific red error text exactly under the correct input fields!

---

## Phase 1 · Step 6 — `CustomUserDetailsService` (Bridge Between DB and Spring Security)

---

### 1. What problem does this solve?

Spring Security needs to load a user during login but has no idea about your MySQL DB or JPA setup.
It defines a contract: *"Implement `UserDetailsService`, and I'll call you when I need a user."*
Your job: implement that interface — go to `UserRepository`, fetch user by email, return a `UserDetails` object.

---

### 2. The 5 Pieces

| Piece | What it is | Simple explanation |
|---|---|---|
| `UserDetailsService` | Spring Security interface | Contract with 1 method: `loadUserByUsername(String username)` |
| `loadUserByUsername()` | The method you implement | Spring calls this when someone tries to log in |
| `UserDetails` | Spring Security interface | Describes a user: password, roles, is account expired? |
| `User` (Spring's) | Built-in `UserDetails` implementation | Ready-made wrapper — you build it from your DB entity |
| `UsernameNotFoundException` | Special exception | Must throw this if user not found — Spring expects it exactly |

> ⚠️ **Two different `User` classes exist:**
> - Your `User` entity (`com.habittracker.entity`) → the JPA / DB row
> - Spring Security's `User` (`org.springframework.security.core.userdetails`) → the auth wrapper
> We return Spring's `User`, built **from** your entity.

---

### 3. Why NOT make your `User` entity implement `UserDetails` directly?

Common beginner mistake. Problems:
1. **Breaks Separation of Concerns** — entity owns DB mapping AND security rules — two jobs
2. **Tight coupling** — JPA entity depends on Spring Security library
3. **Interview red flag** — experienced reviewers see this immediately

**Rule:** Keep them separate. `CustomUserDetailsService` is the translator between the two worlds.

---

### 4. Why email instead of username in `loadUserByUsername`?

The parameter is named `username` in Spring's interface — but it's just a `String`. Spring doesn't care what it contains.
Our users log in with email. So we pass email in, query by email. Naming is misleading — logic is correct.

---

### 5. Why not other approaches?

| Alternative | Problem |
|---|---|
| `InMemoryUserDetailsManager` | Hardcoded users — gone on restart. Demos only. |
| Spring's JDBC authentication | Assumes a fixed table schema — no control |
| OAuth2 / social login | Completely different flow — overkill |

Our approach: full control over query, entity, and returned object.

---

### 6. How this fits in the login flow

```
POST /auth/login  { email, password }
       ↓
AuthenticationManager → asks for user
       ↓
CustomUserDetailsService.loadUserByUsername(email)
       ↓
UserRepository.findByEmail(email)   ← goes to MySQL
       ↓
Wraps DB user into Spring's UserDetails
       ↓
Spring compares hashed password with what user typed
       ↓
✅ Match → authenticated   ❌ No match → 401
```

---

### 7. File location

```
com.habittracker.habit_tracker
└── security/
    └── CustomUserDetailsService.java
```

Lives in `security/` — auth infrastructure, not business logic.

---

### 7. Implementation — `CustomUserDetailsService.java`

```java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // "username" here is actually the email — Spring's naming is generic
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + username));

        // Return Spring's built-in User object — translated from our entity
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.emptyList()   // no roles yet
        );
    }
}
```

**Key decisions explained:**
- `@Service` → Spring-managed bean, injectable everywhere
- `@RequiredArgsConstructor` → Lombok generates constructor, `UserRepository` is injected
- `UsernameNotFoundException` (not `RuntimeException`) → Spring Security specifically checks for this type
- `Collections.emptyList()` for authorities → no role-based access yet; will add in Phase 2
- Full class name for Spring's `User` to avoid ambiguity with our entity `User`

### ✅ Step 8 — Git Commit

```bash
git add .
git commit -m "Step 8: Add CustomUserDetailsService - DB to Spring Security bridge"
```

---

## Phase 1 · Step 7 — `JwtUtil.java` (JWT Token Factory)

---

### 1. What is JWT in simple words?

Like a theme park wristband:
- Show ticket (email+password) once at entrance → get a **wristband (JWT token)**
- Every ride (request) → show wristband only → no re-checking with ticket booth
- Server stores **nothing** — the token is self-contained

---

### 2. What's inside a JWT?

Format: `header.payload.signature` (3 parts separated by dots)

| Part | Name | Contains |
|---|---|---|
| Part 1 | Header | Algorithm used (HS256) |
| Part 2 | Payload/Claims | Email, issued-at, expiry |
| Part 3 | Signature | Header+Payload encrypted with secret key |

**Signature = tamper-proof.** If anyone changes the payload, signature won't match → token rejected.

---

### 3. The 4 Methods in JwtUtil

| Method | Job |
|---|---|
| `generateToken(UserDetails)` | Creates a new JWT for a logged-in user |
| `extractEmail(token)` | Reads email (subject) out of the token |
| `isTokenValid(token, UserDetails)` | Checks email matches + not expired |
| `extractExpiration(token)` | Gets when the token dies |

---

### 4. Why JWT over Sessions?

| Sessions | JWT |
|---|---|
| Server stores session in memory/DB | Server stores **nothing** |
| Hard to scale (sticky sessions) | Any server can verify any token |
| Problems with mobile apps | Mobile-friendly |

---

### 5. Key Design Decisions

| Decision | Why |
|---|---|
| `@Component` | Spring-managed — injectable everywhere |
| `@Value("${app.jwt.secret}")` | Secret from config, never hardcoded |
| `subject(email)` | Email is the unique identifier stored in token |
| `HS256` | Symmetric — same key signs and verifies. Fast, standard. |
| `BASE64.decode(secretKey)` | Secret stored as Base64 in properties for safe config |
| Generic `extractClaim()` | Avoids writing parse logic for every single field |
| `SecretKey` return type | Required by JJWT 0.12.x `verifyWith()` and `signWith()` |

---

### 6. ⚠️ JJWT 0.12.x API Breaking Changes

JJWT completely redesigned its API in `0.12.x`. We use `0.12.3`, so all old tutorials (0.11.x) are wrong.

| What changed | Old (0.11.x) | New (0.12.x) — what we use |
|---|---|---|
| Parser entry point | `Jwts.parserBuilder()` | `Jwts.parser()` |
| Set verification key | `.setSigningKey(key)` | `.verifyWith(secretKey)` |
| Parse token | `.parseClaimsJws(token)` | `.parseSignedClaims(token)` |
| Get claims | `.getBody()` | `.getPayload()` |
| Sign token | `.signWith(key, SignatureAlgorithm.HS256)` | `.signWith(key)` — algorithm auto-inferred |
| Return type of `getSigningKey()` | `Key` | `SecretKey` |
| Set claims | `.setClaims(map)` | `.claims(map)` |
| Set subject | `.setSubject(email)` | `.subject(email)` |
| Set issued at | `.setIssuedAt(date)` | `.issuedAt(date)` |
| Set expiration | `.setExpiration(date)` | `.expiration(date)` |

> **Why this matters for interviews:**
> If asked "how do you parse a JWT?" — describe the 0.12.x API. Most tutorials online still show 0.11.x. Knowing this version difference signals real-world experience.

---

### 7. application.properties entries

```properties
app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
app.jwt.expiration=86400000
```

`86400000` ms = 24 hours. Secret must be Base64-encoded, minimum 32 chars raw.

---

### 8. File Created

```
security/JwtUtil.java  ✅ IMPLEMENTED (JJWT 0.12.3 API)
```

---


### 9. JWT Authentication Full Roadmap

```
✅ Step 7  → JwtUtil.java                  (generate & validate tokens)
✅ Step 8  → CustomUserDetailsService.java  (load user from DB)
✅ Step 9  → JwtAuthenticationFilter.java   (intercept every request)
⏳ Step 10 → SecurityConfig.java            (wire everything, define routes)  ← NEXT
⏳ Step 11 → AuthService.java               (register & login logic)
⏳ Step 12 → AuthController.java            (/auth/register, /auth/login)
```

---

## Phase 1 · Step 9 — `JwtAuthenticationFilter.java`

---

### 1. What is a Filter and Why Do We Need One?

Spring Security works via a **filter chain** — a sequence of filters that every HTTP request must pass through before hitting your Controllers. Each filter can inspect, modify, or reject the request.

Our filter's job: **"Does this request carry a valid JWT? If yes, tell Spring Security who the user is."**

Without this filter, Spring Security has no idea about JWT. It would block every request that isn't public.

---

### 2. `OncePerRequestFilter` — Why Extend This?

Spring can dispatch a request internally multiple times (e.g., error forwards, async processing). A regular filter might run multiple times per single user request.

`OncePerRequestFilter` **guarantees our filter runs exactly once** per HTTP request — preventing double authentication or double token validation.

> **Rule:** Any Spring Security filter that should run once per request → extend `OncePerRequestFilter`.

---

### 3. The 9-Step Flow Inside `doFilterInternal`

```
1. Read "Authorization" header from request
      ↓
2. Header missing or doesn't start with "Bearer "?
   → pass request down the chain, return early
      ↓
3. Extract JWT: authHeader.substring(7)
      ↓
4. Extract email from JWT (JwtUtil.extractEmail)
      ↓
5. Is email non-null AND SecurityContext empty (not already authenticated)?
      ↓
6. Load user from DB (CustomUserDetailsService.loadUserByUsername)
      ↓
7. Is token valid? (email match + not expired)
      ↓
8. Create UsernamePasswordAuthenticationToken
   Set it in SecurityContextHolder
      ↓
9. filterChain.doFilter(request, response) → pass to next filter / Controller
```

---

### 4. `SecurityContextHolder` — The Most Important Concept

`SecurityContextHolder` is where Spring Security stores the **currently authenticated user** for the lifetime of a request.

- Each thread gets its own SecurityContext (ThreadLocal — not shared between requests)
- When we call `SecurityContextHolder.getContext().setAuthentication(authToken)` → we're saying "this request belongs to this user"
- After the filter runs, any code in the Controller can call `SecurityContextHolder.getContext().getAuthentication()` to get the logged-in user

> **Interview Q:** *"How does Spring Security know who the logged-in user is?"*
> "The `JwtAuthenticationFilter` runs before every request. It validates the JWT, then stores the authenticated user in the `SecurityContextHolder`. This makes the user available throughout the entire request lifecycle."

---

### 5. `UsernamePasswordAuthenticationToken` — The Auth Object

This is Spring Security's standard authentication token. Constructor has two forms:

```java
// UNAUTHENTICATED (used when sending credentials to be verified):
new UsernamePasswordAuthenticationToken(principal, credentials)

// AUTHENTICATED (used after verification — what we use):
new UsernamePasswordAuthenticationToken(principal, credentials, authorities)
```

**The 3-argument constructor** is what marks the token as **authenticated** (sets `authenticated = true` internally). That's critical — without it, Spring Security won't treat this as a valid authentication.

| Arg | What we pass | Why |
|---|---|---|
| `principal` | `UserDetails` object | Who is authenticated |
| `credentials` | `null` | Password already verified — we don't store it |
| `authorities` | `userDetails.getAuthorities()` | Roles/permissions |

---

### 6. `WebAuthenticationDetailsSource` — Why Set Details?

```java
authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
```

Attaches extra context to the auth token: **IP address + session ID**.

Used by Spring Security's audit infrastructure. Not required for basic auth to work, but it's best practice. Without it, some advanced Spring Security features (like session fixation protection) won't have the context they need.

---

### 7. Why `filterChain.doFilter()` MUST Always Be Called

`filterChain.doFilter(request, response)` passes the request to the **next filter** in the chain (and eventually to your Controller).

If you forget to call it:
- The request is silently dropped
- The client receives no response
- You spend hours debugging a blank response

**Rule:** Always call `filterChain.doFilter()` at the end of `doFilterInternal`, even if you rejected the token.

---

### 8. What `@NonNull` Does

`@NonNull` on method parameters:
- Documents that these params should never be null
- Makes static analysis tools warn if null could be passed
- Has no runtime behavior — purely compile-time contract

Required here because `OncePerRequestFilter`'s abstract method signature uses it.

---

### ✅ Step 9 — Git Commit

```bash
git add .
git commit -m "Step 9: Add JwtAuthenticationFilter - JWT validation on every request"
```

---

## Phase 1 · Step 10 — Spring Security Configuration (`SecurityConfig.java`)

---

### 1. The Big Picture: What `SecurityConfig` Does

`SecurityConfig` is the **central nervous system** of Spring Security in our application.

It brings together all security components we built in Steps 7, 8, and 9:
- `JwtUtil` (token parsing & validation)
- `CustomUserDetailsService` (DB user lookup)
- `JwtAuthenticationFilter` (request gatekeeper)

And configures:
1. **Which routes are public vs protected**
2. **Stateless session policy** (no server-side HTTP sessions)
3. **CSRF disabled** (safe for stateless JWT APIs)
4. **Filter positioning** (`JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`)
5. **Spring Security Beans** (`PasswordEncoder`, `AuthenticationProvider`, `AuthenticationManager`)

---

### 2. Spring Security 6 vs Older Versions: The Architecture Shift

In older Spring Boot 2.x / Spring Security 5.x apps:
```java
// ❌ DEPRECATED & REMOVED in Spring Security 6:
@Configuration
public class OldSecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception { ... }
}
```

In Spring Boot 3.x / Spring Security 6.x:
- `WebSecurityConfigurerAdapter` has been **completely removed**.
- We use a **component-based `@Bean` model**.
- We declare a `@Bean` method returning `SecurityFilterChain`.
- Method chaining configuration now uses **lambda DSL** (e.g., `csrf(AbstractHttpConfigurer::disable)`).

> **Interview Q:** *"How did Spring Security configuration change in Spring Boot 3 / Spring Security 6?"*
> "Spring Security moved away from extending `WebSecurityConfigurerAdapter` to a component-based configuration style. We now declare standalone `@Bean` methods, specifically returning a `SecurityFilterChain` configured via Lambda DSL, and expose beans like `PasswordEncoder` and `AuthenticationManager` directly in the Spring context."

---

### 3. Why Disable CSRF in a JWT REST API?

**What is CSRF (Cross-Site Request Forgery)?**
An attack where a malicious website tricks a user's browser into executing unwanted actions on a trusted site where the user is currently authenticated.

**Why does CSRF happen with cookies?**
Browsers automatically attach cookies (like `JSESSIONID`) to cross-origin requests.

**Why is our JWT API immune to CSRF?**
1. We store NO session cookies on the client.
2. The JWT is sent explicitly in the `Authorization: Bearer <token>` HTTP header by our client (e.g. mobile app, frontend SPA).
3. Browsers **never** attach custom HTTP headers automatically on cross-site requests.
4. Therefore, CSRF attacks cannot succeed, and enabling CSRF protection would only add unnecessary overhead and require CSRF token handling.

```java
.csrf(AbstractHttpConfigurer::disable)
```

---

### 4. Route Authorization: `authorizeHttpRequests`

We configure our endpoint access rules:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**", "/auth/**", "/error").permitAll()
    .anyRequest().authenticated()
)
```

- **`permitAll()` on `/api/v1/auth/**` & `/auth/**`**: Anyone can register (`/auth/register`) or login (`/auth/login`) without an existing JWT. `/error` is permitted so Spring Boot's internal error handler can render proper error responses.
- **`anyRequest().authenticated()`**: Every other endpoint (e.g., `/habits/**`, `/habits/{id}/logs`) requires a valid JWT.

> **Rule:** *Principle of Least Privilege* — explicitly whitelist public routes, lock down everything else by default.

---

### 5. Stateless Session Policy: `SessionCreationPolicy.STATELESS`

```java
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

- **`ALWAYS`**: Always create an `HttpSession`.
- **`IF_REQUIRED`** (Default): Create an `HttpSession` only if needed.
- **`NEVER`**: Never create an `HttpSession`, but use one if it already exists.
- **`STATELESS`**: Spring Security will **never** create an `HttpSession` and will **never** store or use `SecurityContext` in a session.

Every HTTP request is completely independent and must provide its own credentials (the JWT token).

---

### 6. Filter Ordering: Why `addFilterBefore`?

```java
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

Spring Security has a default chain of filters. One standard filter is `UsernamePasswordAuthenticationFilter` (used for traditional form login).

By placing `JwtAuthenticationFilter` **before** it:
1. When a request arrives, our filter runs first.
2. It extracts the JWT, verifies it, loads the user, and sets `SecurityContextHolder.getContext().setAuthentication(authToken)`.
3. When subsequent authorization checks run down the chain (like `FilterSecurityInterceptor` / `AuthorizationFilter`), they see the user is already authenticated!

---

### 7. The Core Beans Defined in `SecurityConfig`

#### A. `PasswordEncoder` (`BCryptPasswordEncoder`)
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```
- Uses **BCrypt** hashing algorithm.
- Has a built-in random salt generator (generates a unique salt for every password).
- Hashing is one-way (irreversible) and deliberately CPU-intensive (work factor / 10 rounds) to resist brute-force attacks.
- Used in `AuthService` when creating users and `DaoAuthenticationProvider` when verifying passwords.

#### B. `AuthenticationProvider` (`DaoAuthenticationProvider`)
```java
@Bean
public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
    authProvider.setUserDetailsService(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
}
```
- `DaoAuthenticationProvider` is Spring's standard provider that retrieves user details from a `UserDetailsService` and checks passwords with a `PasswordEncoder`.

#### C. `AuthenticationManager`
```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```
- The top-level interface for authenticating a request.
- In `AuthService` (Step 11), we call `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))` to verify credentials during login.

---

### 8. Key Interview Questions & Answers

> **Q1: Why is `BCrypt` preferred over `MD5` or `SHA-256` for password hashing?**
> "MD5 and SHA-256 are general-purpose cryptographic hash functions designed to be extremely fast. Fast hashing makes them vulnerable to brute-force and rainbow table attacks. BCrypt is a slow, adaptive hashing algorithm with built-in salting. Its work factor (iteration count) can be increased as hardware gets faster, making brute-force attacks computationally infeasible."

> **Q2: What is the role of `AuthenticationManager` in Spring Security?**
> "`AuthenticationManager` is the main coordinator for authentication. When passed an `Authentication` object containing user credentials, it iterates through its registered `AuthenticationProvider`s (like `DaoAuthenticationProvider`) to validate the credentials and return a fully authenticated `Authentication` object."

> **Q3: What happens if an unauthenticated user calls a protected endpoint?**
> "The `JwtAuthenticationFilter` passes the request through without setting an authentication object in `SecurityContextHolder`. When the request reaches the `AuthorizationFilter` at the end of the filter chain, it checks `anyRequest().authenticated()`, sees no authentication in the context, and responds with HTTP 401 Unauthorized."

---

### ✅ Step 10 — Git Commit

```bash
git add .
git commit -m "Step 10: Add SecurityConfig - SecurityFilterChain, stateless JWT policy, routes, and auth beans"
```

---

## Phase 1 · Step 11 — Authentication Service (`AuthService.java`)

---

### 1. What is `AuthService` and Why Do We Need It?

`AuthService` holds the **core business logic** for user onboarding and authentication.

In clean layered architecture (`Controller → Service → Repository → Database`):
- **Controllers** should be thin: only handle HTTP requests, path variables, request bodies, and HTTP status codes.
- **Repositories** should be simple: perform database CRUD.
- **Services** are where the business rules live: validation, password hashing, transaction boundaries, orchestration between multiple repositories/security managers, and token generation.

---

### 2. Deep Dive: The Registration Flow (`register`)

```
1. Receive RegisterRequest (name, email, password)
      ↓
2. Normalize email: request.email().toLowerCase().trim()
      ↓
3. Check DB: userRepository.existsByEmail(email)
      → If TRUE: throw DuplicateResourceException (409 Conflict)
      ↓
4. Hash password: passwordEncoder.encode(password)  [BCrypt with salt]
      ↓
5. Build User entity (User.builder()...)
   - streakFreezeTokens set to default (1) via @Builder.Default
      ↓
6. Persist User: userRepository.save(user)  → MySQL INSERT
      ↓
7. Load UserDetails: userDetailsService.loadUserByUsername(user.getEmail())
      ↓
8. Generate JWT: jwtUtil.generateToken(userDetails)
      ↓
9. Return AuthResponse (token, id, name, email, streakFreezeTokens)
```

---

### 3. Deep Dive: The Login Flow (`login`)

```
1. Receive LoginRequest (email, password)
      ↓
2. Normalize email: request.email().toLowerCase().trim()
      ↓
3. Delegate to AuthenticationManager:
   authenticationManager.authenticate(
       new UsernamePasswordAuthenticationToken(email, password)
   )
      ↓
   Did password match BCrypt hash in DB?
      - ❌ NO  → throws BadCredentialsException (GlobalExceptionHandler returns 401 Unauthorized)
      - ✅ YES → continues without exception
      ↓
4. Fetch User entity: userRepository.findByEmail(email)
      - If missing: throw ResourceNotFoundException (404)
      ↓
5. Load UserDetails: userDetailsService.loadUserByUsername(email)
      ↓
6. Generate JWT: jwtUtil.generateToken(userDetails)
      ↓
7. Return AuthResponse (token, id, name, email, streakFreezeTokens)
```

---

### 4. Why Delegate Login to `AuthenticationManager`?

> **Interview Q:** *"Why use `AuthenticationManager.authenticate(...)` instead of manually doing `userRepository.findByEmail()` + `passwordEncoder.matches()` in your service?"*

**Answer:**
1. **Separation of Concerns:** Spring Security is designed to handle authentication. Doing manual `passwordEncoder.matches()` bypasses Spring Security's authentication lifecycle.
2. **Security Events & Auditing:** `AuthenticationManager` fires authentication success/failure events (e.g. `AuthenticationSuccessEvent`, `AuthenticationFailureBadCredentialsEvent`) which security audit loggers, rate limiters, or account lockout mechanisms can listen to.
3. **Pluggable Architecture:** If tomorrow we add multi-factor authentication (MFA), LDAP, or OAuth2, `AuthenticationManager` coordinates all providers without changing the service logic.

---

### 5. Why `@Transactional` and `@Transactional(readOnly = true)`?

- **`@Transactional` on `register()`:**
  - Wraps the method execution in a database transaction.
  - If an exception occurs after saving the user (e.g. failure during post-registration steps), the database operation is automatically **rolled back**, preventing partial/corrupt data.

- **`@Transactional(readOnly = true)` on `login()`:**
  - Informs Hibernate/JPA that no entity modifications will take place during this call.
  - **Performance Optimization:** Hibernate disables dirty checking (it doesn't need to snapshot entities or check if fields were modified), reducing CPU and memory overhead.

---

### 6. Email Normalization

```java
String normalizedEmail = request.email().toLowerCase().trim();
```

- Prevents duplicate registrations like `User@Example.com` and `user@example.com` creating separate accounts in databases with case-sensitive collation.
- Strips accidental leading/trailing spaces from user input.

---

### 7. Key Interview Questions & Answers

> **Q1: What happens if a user submits an incorrect password during login?**
> "`AuthenticationManager.authenticate()` delegates to `DaoAuthenticationProvider`. It loads the user via `CustomUserDetailsService` and checks the raw password against the stored BCrypt hash using `PasswordEncoder.matches()`. If they don't match, it throws `BadCredentialsException`. Our `GlobalExceptionHandler` intercepts this and returns a clean HTTP 401 Unauthorized response with message 'Invalid email or password'."

> **Q2: Why do we return an `AuthResponse` record instead of the `User` entity?**
> "1. **Security:** Exposing JPA entities directly in API responses can accidentally leak sensitive fields like the BCrypt password hash or internal database audit fields.
> 2. **Decoupling:** DTOs decouple the external API contract from the internal database schema.
> 3. **Immutability:** Java 17 records are immutable data carriers, thread-safe and free from boilerplate."

---

### ✅ Step 11 — Git Commit

```bash
git add .
git commit -m "Step 11: Add AuthService - user registration, BCrypt password hashing, login authentication, and JWT issuance"
```

---

## Phase 1 · Step 12 — Authentication Controller (`AuthController.java`)

---

### 1. The Role of the Controller in Clean Layered Architecture

`AuthController` is the **public REST API gateway** for our authentication system.

In our 4-tier architecture (`Controller → Service → Repository → Database`):
- The **Controller layer** should NEVER contain business logic or database queries.
- Its only responsibilities are:
  1. Map incoming HTTP requests (`POST /api/v1/auth/register`, `POST /api/v1/auth/login`) to Java methods.
  2. Trigger request payload validation (`@Valid`).
  3. Delegate the actual work to `AuthService`.
  4. Wrap and return the result in a `ResponseEntity` with the correct HTTP status code (`201 Created` or `200 OK`).

---

### 2. Key Annotations Explained

| Annotation | What It Does | Why We Use It |
|---|---|---|
| `@RestController` | Combines `@Controller` + `@ResponseBody` | Automatically serializes return values into JSON format using Jackson. |
| `@RequestMapping("/auth")` | Sets base URL prefix for this controller | Combined with `server.servlet.context-path=/api/v1`, full URLs are `/api/v1/auth/*`. |
| `@RequiredArgsConstructor` | Generates constructor for `final AuthService` | Idiomatic Spring constructor injection (no `@Autowired` field injection). |
| `@PostMapping("/register")` | Maps HTTP POST `/api/v1/auth/register` | Used for submitting new user data. |
| `@PostMapping("/login")` | Maps HTTP POST `/api/v1/auth/login` | Used for submitting login credentials. |
| `@Valid` | Triggers Bean Validation (JSR-380) | Evaluates `@NotBlank`, `@Email`, `@Size` on the DTO. If invalid, throws `MethodArgumentNotValidException` before the method body runs. |
| `@RequestBody` | Reads HTTP request body | Tells Jackson to deserialize incoming JSON string into Java record DTOs (`RegisterRequest` / `LoginRequest`). |

---

### 3. REST HTTP Status Code Standards

> **Interview Q:** *"Why use HTTP 201 Created for registration and HTTP 200 OK for login?"*

- **`201 CREATED` (`HttpStatus.CREATED`) on `/register`:**
  - Standard REST specification RFC 7231: Whenever an HTTP request results in the creation of a new persistent resource on the server (a new User row in MySQL), the server MUST return `201 Created`.
- **`200 OK` (`HttpStatus.OK`) on `/login`:**
  - Login does NOT create a new entity in the database. It validates existing credentials and retrieves an authorization token. `200 OK` is the standard status code for successful retrieval/action.

---

### 4. The Complete End-to-End JWT Authentication Lifecycle

```
CLIENT (Postman / React / Mobile)
  │
  │  POST /api/v1/auth/register { "name": "Akshay", "email": "ak@test.com", "password": "pass" }
  ▼
[Tomcat Web Server]
  │ (Context Path: /api/v1)
  ▼
[Spring Security Filter Chain]
  │
  ├─► JwtAuthenticationFilter: Header missing /auth/** → pass through
  │
  ├─► SecurityConfig: requestMatchers("/api/v1/auth/**").permitAll() → ALLOWED ✅
  ▼
[AuthController.register(@Valid @RequestBody RegisterRequest)]
  │
  ├─► Bean Validation passes (@NotBlank, @Email, @Size)
  ▼
[AuthService.register(request)]
  │
  ├─► userRepository.existsByEmail() → False
  ├─► passwordEncoder.encode("pass") → "$2a$10$e8..."
  ├─► userRepository.save(User) → MySQL INSERT INTO users...
  ├─► userDetailsService.loadUserByUsername("ak@test.com")
  ├─► jwtUtil.generateToken(userDetails) → "eyJhbGciOiJIUzI1NiIsIn..."
  ▼
[AuthController returns ResponseEntity(AuthResponse, HttpStatus.CREATED)]
  │
  ▼
CLIENT receives HTTP 201 Created + JSON:
{
  "token": "eyJhbGciOiJIUzI1NiIsIn...",
  "userId": 1,
  "name": "Akshay",
  "email": "ak@test.com",
  "streakFreezeTokens": 1
}
```

---

### 5. Key Interview Questions & Answers

> **Q1: Why is `@Valid` placed on the Controller method argument instead of validating in the Service?**
> "Failing fast at the controller layer prevents unnecessary service invocations and database roundtrips for malformed requests. Spring MVC automatically routes validation failures to `@ExceptionHandler(MethodArgumentNotValidException.class)` in `GlobalExceptionHandler`, producing structured 400 Bad Request responses."

> **Q2: What is the difference between `@Controller` and `@RestController`?**
> "`@Controller` is for traditional Spring MVC returning server-rendered HTML views (JSP/Thymeleaf). To return JSON from a `@Controller`, you must add `@ResponseBody` on every method. `@RestController` is a convenience meta-annotation that includes `@Controller` and `@ResponseBody`, ensuring all method return values are automatically serialized into the HTTP response body as JSON."

---

### ✅ Step 12 — Git Commit

```bash
git add .
git commit -m "Step 12: Add AuthController - register and login REST endpoints (Phase 1 JWT Auth complete)"
```

---

## 🏆 Phase 1 Summary: JWT Authentication System Complete!

| Step | Component | Purpose | Status |
|---|---|---|---|
| Step 7 | `JwtUtil.java` | Generate, parse, sign, and validate JWT tokens (JJWT 0.12.3) | ✅ |
| Step 8 | `CustomUserDetailsService.java` | Bridge between MySQL `User` entity and Spring Security's `UserDetails` | ✅ |
| Step 9 | `JwtAuthenticationFilter.java` | Intercept every incoming request to extract & validate JWT in `SecurityContextHolder` | ✅ |
| Step 10 | `SecurityConfig.java` | Stateless session policy, CSRF disabled, public route permitAll, auth beans | ✅ |
| Step 11 | `AuthService.java` | Registration validation, BCrypt hashing, `AuthenticationManager` login delegation | ✅ |
| Step 12 | `AuthController.java` | REST endpoints for `/api/v1/auth/register` (201) and `/api/v1/auth/login` (200) | ✅ |

---

---

> **Phase 2:** Habit Management & Tracking System

---

## Phase 2 · Step 13 — Habit Service (`HabitService.java`)

---

### 1. What is `HabitService` and Why Do We Need It?

`HabitService` is the domain engine responsible for:
1. **Habit Lifecycle (CRUD):** Creating, retrieving, updating, and deleting habits.
2. **User Data Isolation & Security (IDOR Prevention):** Ensuring that a user can ONLY view, edit, or delete habits that belong to their own account.
3. **Streak Calculation Engine:** Dynamically calculating the `currentStreak` and `longestStreak` from historical `HabitLog` records.

---

### 2. User Isolation & Preventing Insecure Direct Object References (IDOR)

> **Interview Q:** *"Why don't we use `habitRepository.findById(habitId)` when fetching, updating, or deleting a habit?"*

**Answer:**
If we used `habitRepository.findById(habitId)`:
- User A (ID: 1) could send a request `PUT /api/v1/habits/42` or `DELETE /api/v1/habits/42`.
- If habit `42` belongs to User B (ID: 2), User A could modify or delete User B's private data! This critical security vulnerability is called **Insecure Direct Object Reference (IDOR)** (OWASP Top 10).

**Our Solution:**
We always extract the authenticated user's email from the JWT token and use:
```java
habitRepository.findByIdAndUserId(habitId, user.getId())
```
This generates SQL:
```sql
SELECT * FROM habits WHERE id = ? AND user_id = ?
```
If habit `42` does not belong to the calling user, the query returns `Optional.empty()`, resulting in a safe `ResourceNotFoundException (404)`.

---

### 3. Deep Dive: Dynamic Streak Calculation Algorithm

```
                  ┌─────────────────────────────────────┐
                  │ Chronologically Sorted Habit Logs   │
                  │   [Aug 17, Aug 18, Aug 19, Aug 20]  │
                  └──────────────────┬──────────────────┘
                                     │
                     ┌───────────────┴───────────────┐
                     ▼                               ▼
       [1. Current Streak Engine]       [2. Longest Streak Engine]
       - Check if today is logged.       - Iterate sorted unique dates.
       - If not, check yesterday.        - If date == prevDate + 1:
       - Walk backward consecutively:      runningStreak++
         Aug 20 -> Aug 19 -> Aug 18      - Else: runningStreak = 1
       - Current Streak = 3 days 🔥     - Longest Streak = Max(running) 🏆
```

#### Why Calculate Streaks Dynamically instead of Static DB Columns?
1. **Single Source of Truth:** `HabitLog` rows represent reality. If streak counters were stored as raw integer columns on the `habits` table, race conditions, missed days, or manual log edits could cause the counter to get out of sync with actual logs.
2. **Time-Sensitive State:** A streak expires automatically if yesterday passed without a log. With dynamic calculation, opening the app the next day immediately reflects the correct streak without needing nightly background cron jobs to decrement numbers.

---

### 4. Code Breakdown: How Each Method Works (4 Simple Steps)

Every CRUD method follows the exact same 4-step pattern:

```
Step 1: Find the User by email (from JWT token)
Step 2: Find / Create the Habit (using findByIdAndUserId for security)
Step 3: Perform Action (save / update / delete / calculate streaks)
Step 4: Return DTO (HabitResponse)
```

| Method | Transaction | What It Does (In Simple Words) |
|---|---|---|
| `createHabit` | `@Transactional` | Finds user $\rightarrow$ builds habit $\rightarrow$ saves to DB $\rightarrow$ returns with `streak = 0`. |
| `getAllUserHabits` | `@Transactional(readOnly = true)` | Finds user $\rightarrow$ gets habits $\rightarrow$ loops through habits to calculate streaks $\rightarrow$ returns list. |
| `getHabitById` | `@Transactional(readOnly = true)` | Finds habit by ID + User ID $\rightarrow$ calculates streaks $\rightarrow$ returns response. |
| `updateHabit` | `@Transactional` | Finds habit $\rightarrow$ updates `name` & `description` $\rightarrow$ saves $\rightarrow$ returns updated response. |
| `deleteHabit` | `@Transactional` | Finds habit $\rightarrow$ deletes from DB (cascade removes all its logs automatically). |
| `calculateCurrentStreak` | Helper | Uses `HashSet` of logged dates $\rightarrow$ counts backwards consecutively from today/yesterday. |
| `calculateLongestStreak` | Helper | Loops through sorted logs $\rightarrow$ tracks maximum consecutive days chain seen. |

---

### 5. How to Explain This in an Interview (Fresher Cheatsheet)

> **"How do you handle Habit CRUD and ensure data security?"**
> *"In `HabitService`, every method receives the logged-in user's email from the JWT token. To prevent IDOR attacks where one user could tamper with another's data, we always query using `findByIdAndUserId(habitId, userId)` instead of just `findById`. If the habit doesn't belong to the caller, it safely throws a 404 `ResourceNotFoundException`."*

> **"How does your streak algorithm work?"**
> *"1. **Current Streak:** I put all logged dates into a `HashSet`. I check if today or yesterday is logged, then use a `while` loop walking backwards day by day (`minusDays(1)`). As long as each previous day exists in the set, I increment the streak counter.
> 2. **Longest Streak:** I iterate through the chronologically sorted logs. If `currentDate` is `previousDate + 1`, I increase the streak. If there's a gap, I reset the streak counter to 1. I keep track of the maximum streak achieved."*

---

### ✅ Step 13 — Git Commit

```bash
git add .
git commit -m "Step 13: Add HabitService - Clean, simplified Habit CRUD with dynamic streak calculation"
```

---

> **Next Step:** Step 14 — Habit Logging Engine & Streak Freeze Service (`HabitLogService.java`)

