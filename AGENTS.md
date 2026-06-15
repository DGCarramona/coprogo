# Canonical AI Agent Policy

This file is the single source of truth for AI coding behavior in this repository.
All tool-specific instruction files must defer to this policy.

## 0. Product and Business Context (Mandatory)

This repository contains a monorepo for a shared-expense management application.

### Product goal

The application helps a group of people manage:
- shared expenses
- reimbursements
- a virtual common cash pool
- income linked to a shared asset
- unequal ownership shares for revenue distribution

Primary use case:
A group of family members jointly manage an apartment. Members can advance expenses, validate or refuse proposed participations, reimburse later, upload supporting documents, and track how much they still owe or are owed. The apartment also generates rental income, which belongs to members according to ownership shares that may differ.

### Core business rules

#### Groups and members
- The application supports multiple groups.
- A group cannot be deleted.
- Any member may invite another member.
- Only the group creator may change ownership shares.

#### Expenses
- An expense is created by a member.
- Every expense must be traceable and may include one or more supporting documents.
- Expense participation is equal by default.
- A participation may also be:
    - partial
    - capped
    - limited to a maximum contribution
    - limited to a reference amount, with any excess remaining the responsibility of the expense creator
- The creator records the proposed participations according to what was agreed outside the application.
- Each impacted member validates or refuses only their own participation.
- If any impacted member refuses, the expense entry is invalidated and participations must be re-entered.
- An expense only affects balances once it is effectively accepted according to the above validation rule.
- A member cannot create an expense “for others only” as a separate business case for now.

#### Reimbursements
- Reimbursements may be direct or indirect.
- The application must show a unified history of what reduced a member’s debt:
    - direct reimbursement
    - compensation through a validated expense
    - compensation through a cash-pool withdrawal exceeding the withdrawer’s own revenue share
- If Alice records that Bob reimbursed her, Bob does not need to confirm.
- If Bob records that he reimbursed Alice and provides a supporting document, Alice may reject it.
- Reimbursements with supporting documents must remain visible and reviewable.

#### Common cash pool
- Rental income goes into a virtual common cash pool.
- Revenue distribution follows ownership shares.
- Ownership shares do not apply by default to ordinary expenses.
- A member cannot withdraw money from the common cash pool if sufficient money is not available.
- If a member withdraws more than their own revenue share, the excess reduces what other members owe them.

#### Ownership shares
- Ownership shares are historized with an effective date.
- A share change is a major business event and must never rewrite history.

#### Traceability and immutability
- Historical financial movements are immutable in amount.
- If the amount changes in practice, model it as:
    - multiple invoices, or
    - cancellation/replacement, not destructive mutation
- The canonical source of truth for member balances is an immutable financial ledger.
- Balance-affecting business facts must be modeled as append-only financial events:
    - accepted expenses
    - reimbursements
    - cash-pool income
    - cash-pool withdrawals
    - revenue distributions
- Current balances and debt views are projections derived from that immutable ledger, not authoritative mutable state.
- Supporting document replacement or deletion must be traced.
- Previous supporting documents must remain accessible in history.
- Only the creator of an event may delete its attachment, and deletion must be audited.

### Visibility and UX expectations
- Each member must be able to understand:
    - what they still owe
    - what has already been reimbursed
    - which expenses generated the debt
    - which events reduced that debt
- The system’s source of truth is financial events and balances.
- The UI may provide an explanatory allocation of remaining debt to expenses, but this is a presentation concern, not a separate source of truth.

### Non-goals
- The application does not implement a voting system for decisions.
- The application does not aim to be full legal/accounting software.
- The application does not support multiple currencies for now.
- The application does not require SSR for the frontend.

## 1. Goal

Generate production-ready code that:
- follows Clean Architecture
- is fully testable
- preserves GraalVM native-image compatibility for the backend
- includes meaningful automated tests
- preserves auditability and historical traceability

## User Interaction and Guideline Evolution (Mandatory)

### Clarification before implementation

- The agent must surface all blocking questions, ambiguities, and conflicting interpretations in the user request before starting implementation or editing files.
- When an assumption could materially affect scope, behavior, architecture, or acceptance criteria, the agent must ask first instead of silently choosing an interpretation.
- The agent may proceed without additional questions only when the request is already precise enough that no material uncertainty remains.

### Proposing guideline updates

- If the user provides an instruction that complements, refines, or extends the existing repository guidance in a reusable way, the agent must propose updating this `AGENTS.md` file.
- The proposal should be made in the same conversation, before the instruction is likely to be lost or applied only implicitly.
- If the user confirms, the agent should update the guidelines as part of the task when feasible.

## 2. Monorepo Architecture (Mandatory)

Treat the repository as a polyglot monorepo with explicit boundaries.

Expected top-level structure:

- `backend/`: Micronaut backend (Kotlin, Gradle)
- `frontend/`: Angular SPA
- `.github/`: CI/CD and automation
- optional root files for monorepo orchestration only

Do not blur backend and frontend boundaries.

### Shared code rules
- Do not share backend domain models directly with the frontend.
- Shared contracts are allowed only when intentionally designed:
    - API schemas
    - generated clients
    - stable DTO contracts
    - documentation artifacts
- Never introduce hidden runtime coupling between frontend and backend through convenience “shared” code.

## 3. Clean Architecture (Mandatory)

Use clear layers.

### Backend
- **Domain**: entities, value objects, domain services, invariants, policies
- **Application**: use cases, ports, orchestration, commands, queries, DTOs
- **Interface Adapters**: REST controllers, request/response mappers, presenters, repository adapters
- **Infrastructure**: Micronaut wiring, persistence, S3-compatible document storage, token validation adapters, external integrations

### Frontend
- **Domain**: pure TypeScript domain models and business rules
- **Application**: use cases, orchestration, application services
- **Presentation / Interface Adapters**: Angular pages, view models, presentational components, forms, mappers
- **Infrastructure**: HTTP clients, Google auth adapters, storage adapters, API DTO mapping

### Dependency direction
Dependencies must point inward only.

- Domain depends on nothing outer.
- Application depends only on Domain.
- Adapters depend inward through ports.
- Infrastructure depends inward through ports and framework composition.

Business logic must not live in Micronaut controllers or Angular components.

### Boundary conversion
- Convert transport primitives into Domain/Application types, including value objects, as early as practical at boundaries such as HTTP controllers.
- Do not pass raw strings, UUIDs, numeric amounts, or dates deeper than necessary when a domain/application type already exists and can validate the input at the boundary.
- Keep boundary conversion explicit and fail fast so invalid transport input does not leak into inner layers.

## 4. Technical Constraints (Mandatory)

### Backend
- Micronaut
- Kotlin
- Gradle Kotlin DSL
- PostgreSQL
- Micronaut Data R2DBC
- Flyway for schema migrations
- GraalVM native-image compatibility
- REST API
- Google account authentication only, with login performed client-side
- Backend validates and recognizes the user from a Google ID token sent by the client
- The canonical user identity is the normalized, verified Google email address, and that email is also the internal member identifier; Google `sub` may be stored only as non-canonical metadata if needed
- S3-compatible object storage for supporting documents

### Frontend
- Angular SPA
- standalone APIs
- Angular Signals for reactive state
- no SSR required by default

### Monorepo
- A root Gradle build is used as the monorepo orchestration layer.
- Gradle remains the source of truth for backend build logic.
- Angular CLI / Angular build tooling remains the source of truth for frontend build logic.
- Root Gradle tasks coordinate install, checks, local developer entrypoints, and CI ergonomics.
- Prefer the Gradle Node plugin (`com.github.node-gradle.node`) or equivalent when Gradle needs to provision Node.js and run frontend package-manager commands.
- Long-running frontend/backend developer processes may be wrapped by Gradle convenience tasks, but the native backend and frontend tools remain the authoritative runtime entrypoints.
- In CI, prefer direct frontend npm/Angular commands for frontend-only jobs unless a specific Gradle aggregation need exists.
- When running Gradle commands in a non-interactive shell, if a root `.sdkmanrc` file exists and SDKMAN is installed, source SDKMAN and run `sdk env` before invoking `./gradlew` so the project Java version is respected.

## 4.5. Backend Reactive and Functional Style (Mandatory)

Backend code should be as non-blocking, reactive, functional, and set-oriented as practical.

### Non-blocking I/O
- Prefer non-blocking I/O at infrastructure boundaries.
- Prefer R2DBC for database access.
- Prefer non-blocking HTTP and integration clients where relevant.
- Avoid introducing blocking calls in reactive execution paths.
- If blocking code is unavoidable, isolate it explicitly at the edge.

### Reactive execution model
- Prefer reactive or suspendable flows at application boundaries when they improve correctness and composability.
- Prefer explicit asynchronous composition over hidden thread-blocking behavior.
- Do not introduce reactive complexity where a simpler pure function is sufficient.
- Prefer Kotlin coroutines and Flow in project-owned code unless a framework boundary naturally requires another reactive type.

### Functional style
- Prefer pure functions and explicit transformations.
- Prefer immutable data and copy-based updates where practical.
- Prefer map/filter/fold/grouping/partitioning style over manual mutable orchestration.
- Keep side effects at the outer layers.

### Set-based style
- Prefer set-oriented and aggregate-oriented reasoning for:
    - expense allocations
    - participant validation
    - debt computation
    - revenue distribution
    - balance derivation
- Prefer batch and set-based persistence operations over per-item loops when possible.
- Avoid N+1 processing where a relational or set-based approach is clearer.

### Repository and query performance
- Repository adapters must be designed with data volume and query performance in mind.
- Prefer set-based SQL, joins, grouped queries, and bulk operations over per-row or per-aggregate loops when practical.
- Avoid N+1 access patterns; when returning aggregate views, fetch the required related data in bounded, explicit queries.
- Consider indexes, uniqueness constraints, ordering, and filtering at the database level for expected access patterns.
- Do not introduce convenience repository methods that are correct only for small datasets when a scalable query shape is available.

### Boundary discipline
- Do not leak framework-specific reactive types deep into the domain without a clear reason.
- Keep Domain and most of Application focused on business semantics first.
- Use framework-reactive types at the edges when needed for transport, persistence, or orchestration.

## 5. Native Image Compatibility (Mandatory)

Backend changes must preserve GraalVM native-image compatibility.

- Avoid unnecessary runtime reflection, dynamic class loading, and opaque framework magic in core logic.
- Prefer compile-time wiring and explicit configuration.
- If reflection metadata or special native configuration is required, add it explicitly.
- A backend change is not complete if JVM tests pass but native compilation breaks.

## 6. Testing (Mandatory)

Every behavior change must include or update tests.

### Backend preferred mix
- many unit tests for Domain and Application
- integration tests for persistence, storage, auth, and HTTP boundaries
- minimal end-to-end tests for critical flows

### Frontend preferred mix
- many unit tests for pure logic
- component/integration tests for important screens and workflows
- minimal end-to-end tests for critical flows

### General rules
- Prefer a TDD workflow for any testable change: write or update failing behavior-focused tests first, then implement the smallest production change that makes them pass.
- Test behavior and outcomes, not implementation details.
- Keep use cases testable without framework coupling.
- Avoid hidden global state and nondeterminism.
- Backend tests requiring PostgreSQL must reuse the repository’s Micronaut Test Resources PostgreSQL infrastructure via `@PostgresMicronautTest` instead of declaring containers or database property wiring in each suite.
- Backend integration tests that need the Micronaut application context but do not exercise persistence must reuse the repository’s shared no-database Micronaut test environment via `@NoDbMicronautTest` instead of duplicating datasource/Flyway overrides in each test class.
- Backend repository integration tests should preferably group cases by public repository method using JUnit 5 `@Nested` classes when it improves readability.
- Backend local runtime configuration must use Micronaut environment files rather than custom `.env` loading: keep shared local defaults in `application-runtime.properties`, reserve `application-local.properties` for machine-specific overrides, and commit only `application-local.example.properties`.
- In coroutine-based backend tests, prefer `assertThrows { runTest { ... } }` for error assertions over manual `try/catch + fail`.
- Prioritize tests around:
    - expense validation/refusal
    - reimbursement recording and contestation
    - cash-pool income distribution
    - over-withdrawal effects on balances
    - ownership-share history with effective dates
    - attachment traceability and audit history

## 7. Mocking Policy (Strict)

Never mock framework internals or vendor SDKs directly.

### Forbidden
- mocking Micronaut internals
- mocking Angular internals
- mocking R2DBC internals
- mocking Flyway internals
- mocking S3 SDK internals directly
- mocking Google auth provider internals directly

### Allowed
- in-memory implementations for project-owned ports
- handwritten fakes/stubs for interfaces owned by this codebase
- integration tests for real adapters
- framework-supported test utilities at the boundary

If a dependency is hard to test, introduce a project-owned port and test the adapter at integration level.

## 8. Quality Bar

Prefer:
- small, cohesive classes and functions
- explicit types
- fail-fast validation
- immutable domain logic where practical
- readable naming
- non-redundant naming in Domain and Application when the type already carries the meaning, for example `member: MemberId` rather than `memberId: MemberId`
- explicit money/value abstractions
- explicit audit/event models
- declarative transformations over imperative mutation
- set-oriented reasoning when operating on collections or allocations

Avoid:
- god services
- hidden side effects
- framework leakage into inner layers
- transport or persistence concerns in domain code
- destructive mutation of historical business events

## 8.5. Programming Style Preferences (Strong Preference)

Prefer code that is as reactive, functional, and set-based as practical.

### Reactive style
- Prefer reactive state propagation and derived state over imperative state mutation.
- On the frontend, use Angular Signals for local and application state exposure.
- Prefer computed state and explicit derivations over manual synchronization.
- Avoid event spaghetti and ad hoc mutable shared state.

### Functional style
- Prefer pure functions when possible.
- Prefer transformation pipelines over step-by-step mutable procedures.
- Prefer explicit inputs/outputs over hidden side effects.
- Favor immutability by default, especially in Domain and Application layers.
- Keep side effects at the edges of the system.

### Set-based style
- Prefer set-based and collection-oriented thinking over item-by-item procedural logic when the intent is naturally relational or aggregate.
- In persistence code, prefer set-oriented queries and batch operations over unnecessary per-item loops.
- In domain logic, model membership, inclusion, exclusion, intersections, and allocations explicitly when relevant.
- Avoid N+1-style processing when a set-based approach is clearer and more efficient.

### Practicality rule
These are strong preferences, not dogma.
Choose the most readable and maintainable solution that respects:
- correctness
- auditability
- native-image compatibility
- clear architecture boundaries

Do not force functional or reactive patterns where they make the code harder to understand.

## 9. Backend Rules (Mandatory)

- Keep Micronaut annotations out of inner layers where possible.
- Repository interfaces belong to inner layers; implementations belong to outer layers.
- Project-owned repository ports should prefer `persist(...)` over `save(...)` for write methods.
- External integrations must go through project-owned ports.
- Configuration must enter through explicit adapters/config abstractions.
- Persistence models must not become domain models by accident.
- Kotlin application use cases should be invokable classes, exposing their primary entry point as `operator fun invoke(...)`.
- Create use cases must generate the identifier of the resource they create internally; create commands should not carry the target id.
- CUD use cases should return no payload on success unless a business need explicitly requires a return value.
- Backend id value objects must keep their primitive storage private, expose primitive extraction explicitly through `toPrimitive()`, and must not expose direct `value` access or custom `toString()` behavior.
- Repository ports and backend application use cases that may cross I/O boundaries should be `suspend`; keep the domain synchronous and pure, and only mark HTTP endpoints `suspend` when they actually invoke a suspendable path.
- Financial calculations must use appropriate money-safe representations and deterministic rounding rules.
- Prefer immutable value objects and pure domain services where practical.
- Prefer collection and batch-oriented operations over procedural per-item orchestration when possible.
- Keep side effects in outer layers.

## 10. Frontend Rules (Mandatory)

- Keep Angular-specific concerns at the edge.
- Prefer standalone components and `bootstrapApplication`.
- Use Signals for state exposure and derived state.
- Prefer computed signals over manual synchronization.
- Organize presentation in an MVVM-like style:
    - page/container components
    - explicit view models
    - presentational components
- Components orchestrate UI interaction; they do not own core business rules.
- Prefer application services/use cases for screen actions.
- Do not call HTTP directly from deeply nested presentational components.
- Keep transport DTOs distinct from domain/application models.
- Prefer pure mapping functions for DTO/view-model/form transformations.
- Do not introduce NgRx unless there is a demonstrated need that simpler signal-based services cannot handle.

## 10.5. Frontend Architecture and Testability (Mandatory)

Frontend code must maximize testability and separation of concerns.

### Architectural style
Use a Clean Architecture frontend with a presentation layer organized in an MVVM-like style.

Preferred structure:
- Domain: pure business models, value objects, rules, pure functions
- Application: use cases, orchestration, application services
- Infrastructure: HTTP clients, auth adapters, storage adapters, DTO mapping
- Presentation: Angular pages, view models, presentational components

### Presentation rules
- Treat Angular components as thin UI adapters.
- Keep business logic out of components.
- Prefer page/container components for composition and routing concerns.
- Prefer presentational components for rendering and user interaction only.
- Expose screen state through explicit ViewModels.
- ViewModels should primarily use Angular Signals and computed state.
- Prefer one clear view-model per screen or feature rather than scattered mutable state.

### Testability rules
- Domain logic must be testable without Angular.
- Application/use-case logic must be testable without Angular.
- ViewModels must be testable with minimal framework setup.
- Components should be simple enough that most of their tests focus on rendering, bindings, and emitted interactions.
- Avoid designs that require heavy TestBed setup for ordinary business behavior.
- Prefer pure mapping functions for DTO-to-domain, domain-to-view-model, and form-to-command transformations.

### State management rules
- Prefer Signals for local and feature state.
- Prefer computed state over manual synchronization.
- Prefer explicit command methods over ad hoc mutable state manipulation.
- Do not introduce NgRx unless there is a demonstrated need that simpler signal-based architecture cannot satisfy.

### Dependency rules
- Presentation depends on Application, never on Infrastructure details directly.
- Infrastructure must not leak transport DTOs into Domain.
- HTTP calls must not be embedded in deeply nested components.

## 11. Security and Auditability (Mandatory)

Treat security and traceability as first-class requirements.

- Supporting documents may contain sensitive financial information.
- Authentication is based on Google accounts, but application authorization remains internal to this system.
- Every important business event must be auditable:
    - creation
    - validation/refusal
    - reimbursement declaration
    - attachment replacement/removal
    - ownership-share change
    - cash-pool income registration
    - cash-pool withdrawal
- Never silently destroy historical evidence.

## 12. Static Analysis and Style (Mandatory)

Work is not complete unless repository checks pass.

### Backend
All modified backend files must pass the repository’s configured:
- formatting
- static analysis
- tests
- native build checks when relevant

Backend Kotlin formatting is enforced with `ktlint`.
- Use `./gradlew ktlintFormat` locally to apply formatting.
- CI must verify formatting with `./gradlew ktlintCheck` and must not auto-correct formatting.

### Frontend
All modified frontend files must pass the repository’s configured:
- formatting
- linting
- type checking
- tests
- production build

Do not bypass rules; fix the code.

## 13. Completion Criteria (Mandatory)

A change is not complete unless all relevant checks pass.

Typical expected commands:

### Backend
- `./gradlew check`
- `./gradlew test`
- `./gradlew nativeCompile` for backend-impacting changes

### Frontend
- `npm run lint`
- `npm run test`
- `npm run build`

### Monorepo
- use root Gradle orchestration commands when the repository is configured for them
- prefer project-scoped Gradle tasks or CI matrix execution where appropriate
- frontend-only CI jobs may use direct `npm` commands instead of Gradle

## 14. PR / Change Checklist

- Product rules respected
- Clean Architecture boundaries preserved
- Dependencies point inward
- Native compatibility preserved
- Tests added or updated
- No forbidden mocking introduced
- Auditability preserved
- Historical traceability preserved
- Quality checks passing

## 15. Commit Message Policy (Mandatory)

Use Conventional Commits format:

`<type>(<optional-scope>): <imperative summary>`

Allowed types:
- feat
- fix
- refactor
- test
- docs
- chore
- perf
- build
- ci

Rules:
- Summary must be imperative and specific
- Include scope when useful
- Add a body when the change is non-trivial
- Reference issues/tickets in the footer when available

Examples:
- `feat(expenses): validate participant approval flow`
- `fix(reimbursements): reject contested repayment document`
- `refactor(backend-ledger): extract balance computation service`
- `build(frontend): add angular lint and test scripts`
- `ci(monorepo): run root gradle frontend and backend checks`
