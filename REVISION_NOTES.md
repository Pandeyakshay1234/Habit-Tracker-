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

