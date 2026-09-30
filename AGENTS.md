# Canonical AI Agent Policy

This file is the single source of truth for AI coding behavior in this repository. All tool-specific instruction files must defer to it.

## 0. Product and Business Context

This monorepo implements a shared-expense application.

### Product goal

It helps a group manage shared expenses, reimbursements, a virtual common cash pool, income linked to a shared asset, and unequal ownership shares for revenue distribution.

Its primary use case is family members jointly managing an apartment: they can advance expenses, validate or refuse proposed participations, reimburse later, upload supporting documents, and follow what they owe or are owed. Rental income belongs to members according to ownership shares that may differ.

### Core business rules

#### Groups and members

- The application supports multiple groups; a group cannot be deleted.
- Any member may invite another member.
- Only the group creator may change ownership shares.

#### Expenses

- An expense is created by a member, is traceable, and may have one or more supporting documents.
- Allocation is equal by default and uses exactly one of these modes:
  - **equal**: share the total equally between all participants;
  - **equal with caps**: capped participants never exceed their maximum; redistribute the remainder iteratively, with at least one uncapped participant;
  - **cumulative tiers**: strictly increasing cumulative thresholds; each tier is equally shared by its participants and the final tier covers the total;
  - **custom**: specify an exact amount for every participant.
- The creator records participations agreed outside the application. Each impacted member alone validates or refuses theirs.
- One refusal invalidates the expense and requires participations to be re-entered. An expense affects balances only once accepted under that rule.
- Creating an expense “for others only” is not a separate business case for now.

#### Reimbursements

- Reimbursements may be direct or indirect. The unified history of debt reduction includes direct reimbursement, compensation through a validated expense, and the excess of a cash-pool withdrawal over the withdrawer’s own revenue share.
- If Alice records Bob reimbursing her, Bob does not confirm. If Bob records reimbursing Alice with a supporting document, Alice may reject it.
- Reimbursements with supporting documents stay visible and reviewable.

#### Common cash pool and ownership shares

- Rental income enters a virtual common cash pool. Any member may record it, with future contestation/review supported.
- Revenue follows ownership shares; those shares do not apply by default to ordinary expenses.
- A member cannot withdraw unavailable money. Withdrawal above their own revenue share reduces what other members owe them.
- Ownership shares are historized with an effective date; a change is a major event and must never rewrite history.

#### Traceability and immutability

- Historical financial movements are immutable in amount. Model a practical amount change as multiple invoices or cancellation/replacement, never destructive mutation.
- The immutable financial ledger is the canonical source for balances. Accepted expenses, reimbursements, cash-pool income and withdrawals, and revenue distributions are append-only financial events; balances and debt views are derived projections, never authoritative mutable state.
- Supporting-document replacement or deletion is traced; prior documents remain accessible in history. Only an event creator may delete its attachment, and deletion is audited.

### Visibility, language, and non-goals

- Each member must understand what remains to pay, what was reimbursed, which expenses generated debt, and which events reduced it.
- The UI may explain how remaining debt relates to expenses, but that explanation is not another source of truth.
- User-facing labels, messages, and help avoid technical/financial jargon such as “ledger”, “immutable ledger”, “projection”, “endpoint”, “backend”, or “identifiant technique”; use plain terms such as “historique des opérations”, “ce qui reste à payer”, and “identifiant du groupe”. Developer documentation may use technical terms.
- No voting system, legal/accounting suite, multiple currencies, or SSR is required for now.

## 1. Goal and normative language

Produce production-ready, fully testable code with meaningful automated tests; follow Clean Architecture, preserve GraalVM native-image compatibility where relevant, and preserve auditability and historical traceability.

- **MUST** means mandatory. A change that violates it is incomplete.
- **MUST NOT** prohibits the behavior.
- **SHOULD** is a strong default; depart only when the alternative is clearer while preserving correctness, auditability, native compatibility, and architecture.
- **MAY** permits a context-dependent choice.

The policy remains deliberately specific where product correctness, security, auditability, testability, or build reproducibility is at stake. Avoid weakening a precise rule merely because a nearby guideline is expressed as a preference.

## 2. Collaboration and repository boundaries

### Clarification and guideline evolution

- Before editing, surface blocking questions, ambiguities, and conflicting interpretations. Ask before assuming anything that materially changes scope, behavior, architecture, or acceptance criteria; proceed without a question only when the request is precise enough.
- When a user provides a reusable addition or refinement to these rules, propose updating this file in the same conversation. Update it after confirmation when feasible.

### Monorepo and shared contracts

- This is a polyglot monorepo. The backend lives in `coprogo/` (named `backend` in the composite Gradle build); `frontend/` is the Angular SPA; `.github/` contains CI/CD and automation. Root files may orchestrate the monorepo. Do not blur these boundaries.
- Never share backend domain models directly with the frontend. Intentional shared contracts are API schemas, generated clients, stable DTOs, and documentation artifacts only; do not introduce hidden runtime coupling.

## 3. Clean Architecture

### Layers and dependency direction

- **Backend**: Domain (entities, value objects, domain services, invariants, policies); Application (use cases, ports, orchestration, commands, queries, DTOs); Interface Adapters (REST controllers, request/response mappers, presenters, repository adapters); Infrastructure (Micronaut wiring, persistence, S3-compatible storage, token validation adapters, external integrations).
- **Frontend**: Domain (pure TypeScript business models/rules); Application (use cases, orchestration, application services); Presentation/Interface Adapters (Angular pages, view models, presentational components, forms, mappers); Infrastructure (HTTP clients, Google auth adapters, storage adapters, DTO mapping).
- Dependencies point inward: Domain depends on no outer layer; Application only on Domain; adapters and infrastructure depend inward through ports. Business logic never lives in Micronaut controllers or Angular components.
- At transport boundaries convert primitives to domain/application types as early as practical. Do not let raw strings, UUIDs, amounts, or dates pass deeper when a validating type already exists; make conversion explicit and fail fast.
- Only aggregate roots own repositories, unless an entity has an independently justified lifecycle. For use cases that invoke aggregate behavior, its repository reconstitutes the complete consistency boundary and persists changes through the root rather than exposing repositories for its children. This does not require rewriting unchanged rows or hydrating complete aggregates for collection reads.
- Do not let SQL table shape, foreign keys, or relational inheritance dictate the domain model. In particular, do not duplicate a parent identity in a child solely to simplify persistence.
- Coordinate multiple aggregates atomically through an application port; do not turn one aggregate into another aggregate's child to obtain transactionality.

Keep adapters focused on conversion, delivery, and persistence/integration mechanics. A controller, component, mapper, or persistence model is never a convenient substitute for an aggregate, domain policy, or application workflow.

## 4. Technology and runtime constraints

### Backend

- Use Micronaut, Kotlin, Gradle Kotlin DSL, PostgreSQL, Micronaut Data R2DBC, Flyway, REST, GraalVM native-image compatibility, Google account authentication, and S3-compatible document storage.
- Login occurs client-side; the backend validates the Google ID token. The canonical identity and internal member identifier is the normalized, verified Google email. Google `sub` is optional non-canonical metadata only.

### Frontend and monorepo

- Use an Angular SPA with standalone APIs and Angular Signals; SSR is not required by default.
- Root Gradle orchestrates the monorepo; Gradle remains authoritative for backend logic and Angular CLI/tooling for frontend logic. Root tasks coordinate install, checks, local entrypoints, and CI ergonomics.
- When Gradle needs Node, prefer `com.github.node-gradle.node` or equivalent. Convenience Gradle tasks may wrap long-running processes, but native backend/frontend tools remain the authoritative entrypoints. In frontend-only CI, prefer direct npm/Angular commands unless aggregation is needed.
- In a non-interactive Gradle shell, when a root `.sdkmanrc` exists and SDKMAN is installed, source SDKMAN and run `sdk env` before `./gradlew`.

## 5. Backend implementation rules

### Architecture, concurrency, and style

- Keep Micronaut annotations and framework-reactive types at the outer edge. Repository interfaces belong inward; implementations belong outward. External integrations use project-owned ports; configuration enters through explicit adapters; persistence models must not accidentally become domain models.
- SHOULD use non-blocking I/O at infrastructure boundaries: R2DBC for databases and non-blocking integration HTTP where relevant. Do not introduce blocking work in reactive paths; isolate unavoidable blocking at the edge.
- SHOULD use suspendable/reactive composition where it improves correctness and composability, preferring Kotlin coroutines and Flow in project-owned code. Do not add reactive complexity where a pure function is clearer.
- Prefer pure transformations, immutable/copy-based updates, and side effects at outer layers. Prefer map/filter/fold/grouping/partitioning over manual mutable orchestration when clearer.
- Across the codebase, SHOULD express a value's linear flow as a readable pipeline of chained operations rather than introducing single-use intermediate variables. When error translation or a technical detail interrupts that flow, prefer extracting it behind a semantically named function so the caller's pipeline remains visible. Break a pipeline when values must coexist, the workflow genuinely branches, or named intermediate state or distinct side effects materially improve understanding; never force chaining when it makes the code harder to read.
- Prefer set- and aggregate-oriented reasoning for allocations, participant validation, debt computation, revenue distribution, and balance derivation. Prefer batch/set persistence, SQL joins/grouping/bulk operations, bounded aggregate reads, and database-level filters over N+1 or small-data-only convenience methods. Consider indexes, uniqueness, ordering, and filtering.
- Keep Domain and most Application focused on business semantics rather than framework types. Do not let a framework boundary force reactive complexity into otherwise pure domain behavior.

Repository adapters must be designed for expected data volume and query shape. Where an aggregate view needs related data, fetch it through explicit bounded queries; choose a relational or set-based query whenever it is clearer and scalable than one query per row or aggregate. List reads use dedicated projections, never incomplete aggregates or unnecessarily hydrated complete aggregates.
- PostgreSQL is the sole database: use `ENUM` for closed persisted vocabularies and `DOMAIN` for recurring constrained scalars (normalized emails, money cents, positive amounts, signed ledger deltas, ownership basis points). Database constraints are part of the auditability boundary, complementary to application/domain validation.
- Prefer business-oriented column names where the table context suffices (`event`, `expense`, `type`); retain names such as `member_email` when their qualifier is stable business meaning or prevents ambiguity.

### jOOQ and native image

- Flyway SQL migrations are the schema source of truth. Generate committed jOOQ sources under `coprogo/src/generated/jooq` from the dedicated `coprogo_codegen` PostgreSQL database, never the ordinary `coprogo` development database.
- Normal builds must neither require PostgreSQL nor generate jOOQ implicitly. After migration changes run `./gradlew -p coprogo regenerateJooqFromScratch`; verify with `./gradlew -p coprogo verifyJooqIsUpToDate`, which rebuilds a clean codegen schema and fails if committed generated sources differ.
- Runtime persistence prefers R2DBC; JDBC is reserved for Flyway runtime migration and jOOQ generation unless an explicit architectural decision changes this.
- Preserve native-image compatibility: avoid unnecessary reflection, dynamic class loading, and opaque magic in core logic; prefer compile-time wiring and explicit native metadata when necessary. A JVM-green backend change is incomplete if native compilation breaks.

### Application, Kotlin, and domain conventions

- Project-owned write ports prefer `persist(...)` to `save(...)`.
- A Kotlin application use case is an interface plus a `*Impl` carrying `@Singleton`; consumers inject the interface, and the primary entrypoint is `operator fun invoke(...)`. This enables hand-written fakes without Micronaut test infrastructure.
- Create use cases generate their target identifier internally; create commands do not carry it. CUD use cases return no success payload unless business needs require one.
- IDs keep primitive storage private and expose it only through `toPrimitive()`; they expose neither direct `value` nor custom `toString()`.
- Repository ports and I/O-crossing application use cases are `suspend`; keep domain synchronous and pure, and mark an HTTP endpoint `suspend` only when it invokes a suspendable path.
- A use case receiving `GroupId` first calls `GroupAccessPolicy.requireMember`. Financial calculations use money-safe representations and deterministic rounding.
- Do not create a Kotlin extension function used only once unless reuse, a transversal semantic contract, or notable composition is explicitly justified; otherwise use a normal private function or inline/local code.
- The general quality bar is explicit types, fail-fast validation, small cohesive functions/classes, readable names, immutable domain values, explicit money and audit/event models, and no transport or persistence concern in Domain.

## 6. Frontend architecture and server state

### Architecture and presentation

- Keep Angular concerns at the edge. Use standalone components and `bootstrapApplication`; keep transport DTOs distinct from domain/application models and use pure mappings for DTO/domain/view-model/form conversion.
- Follow frontend Clean Architecture: pure Domain; Application use cases, orchestration, and services; Infrastructure HTTP/auth/storage/DTO mapping; Presentation pages, view models, and presentational components. Presentation depends on Application, never infrastructure details; infrastructure does not leak DTOs into Domain.
- Application use cases contain real orchestration, validation, workflow decisions, aggregation, or reusable policy. Do not create one-call pass-through use cases; inject the focused port into the ViewModel/application service that owns the workflow instead.
- Organize presentation as page/container components, explicit ViewModels, and presentational components. Components are thin UI adapters that orchestrate interaction, not core business rules; do not embed HTTP in deeply nested components.
- Use Signals for state exposure and derived state, especially computed signals rather than manual synchronization. Prefer explicit commands over ad hoc mutable state; do not introduce NgRx without demonstrated need.
- Prefer one clear ViewModel per screen/feature. Use constructor injection in project-owned frontend classes where practical; reserve `inject()` for functional Angular APIs (guards), generated code, or impractical constructor cases.
- For an independently loading page sub-section with its own request and loading/error state, extract a widget component and ViewModel; its parent passes context such as `groupId` through `@Input()`.
- Domain and application logic remain testable without Angular; ViewModels use minimal framework setup; component tests focus on rendering, bindings, and emitted interactions rather than lifecycle/internal methods.
- Prefer a clear page/container composition over scattered mutable state. A component may coordinate user interaction and routing, but reusable business behavior belongs in Domain or Application.

Presentational components render and emit interaction; page/container components compose features and routing context. Keep one explicit ownership point for a screen action so loading, errors, invalidation, and UI feedback do not become event spaghetti or ad hoc shared mutable state.

### TanStack Query and HTTP gateways

- TanStack Query is the standard frontend server-state cache.
- Use stable scoped query keys, model reads as queries and writes as mutations with explicit invalidation, and expose query/mutation state through Signals. Do not add `shareReplay` or a concurrent custom cache.
- Promise/Observable conversion is permitted only where TanStack requires it. A Promise-returning HTTP gateway composes generated-client Observables with `pipe(...)`, handles API errors there with `catchError(...)`, maps there with `map(...)`, and calls `firstValueFrom(...)` only as the final Promise boundary. Do not imperatively wrap it in `try/catch`.

## 7. Tests and TDD

Every behavior change MUST use strict red-green-refactor TDD:

1. Write the failing test before production code and run it to establish red.
2. Make the smallest production change that makes it green; do not add speculative code.
3. Refactor only while green.
4. Never edit production code without a corresponding test already written in this session or inherited from the codebase. The test must import or reference the exact source path; generic tests elsewhere do not qualify.
5. Never commit with relevant failing tests; run the relevant suite before commit.
6. Frontend tests are colocated `.spec.ts` files (or shared stubs under `__test__/app/...`); backend tests mirror `src/main/kotlin/...` under `src/test/kotlin/...`.

Writing or editing production code without that test is invalid; revert the production change and resume with the red phase.

- Test outcomes, not implementation details; keep use cases framework-free, avoid hidden globals/nondeterminism, and use many Domain/Application unit tests, integration tests for persistence, storage, auth, and HTTP boundaries, plus few critical end-to-end tests.
- PostgreSQL backend tests use `@PostgresMicronautTest` and its shared Test Resources PostgreSQL infrastructure. Context-only non-persistence integration tests use `@NoDbMicronautTest`. Controller tests are pure unit tests with hand-written fakes, never `@MicronautTest` or a database.
- Group multi-entrypoint tests by public entrypoint: backend with JUnit 5 `@Nested` per public method or HTTP route; frontend classes, ViewModels, and gateways with `describe('<publicMethod>')`; frontend pure modules with `describe('<exportedFunction>')`; components by visible behavior/interaction.
- Backend local runtime configuration uses Micronaut environment files, never custom `.env` loading: shared defaults in `application-runtime.properties`, machine overrides in `application-local.properties`, and only `application-local.example.properties` committed.
- For coroutine error assertions prefer `assertThrows { runTest { ... } }` to manual `try/catch + fail`.
- Collection assertions compare complete expected and actual collections in one assertion, after stable projections when necessary. Never assert inside `forEach`, `forEachIndexed`, `zip(...).forEach`, or another iteration callback. For independent cases use parameterized/nested tests or collect stable outcomes then compare the complete list; setup iteration without assertions is permitted.
- When removing or moving an abstraction, inventory its behavioral guarantees and migrate their tests to the new architectural boundary.
- Prioritize expense validation/refusal, reimbursement recording/contestation, cash-pool income distribution, over-withdrawal balance effects, effective-dated ownership-share history, and attachment traceability/audit history.

Use framework test facilities only at their intended boundary. Do not mock a vendor SDK or framework internal merely to make a unit test convenient; test a project-owned abstraction with a fake or test the real adapter at integration level.

### Mocking policy

- Never mock Micronaut, Angular, R2DBC, Flyway, S3 SDK, or Google-auth-provider internals directly.
- Use in-memory project-owned ports, hand-written project-interface fakes/stubs, real adapter integration tests, or framework-supported boundary utilities. Introduce a project-owned port when a dependency is hard to test.

## 8. Security, quality, and completion

### Security and quality

- Supporting documents are sensitive financial information. Google authenticates accounts, but authorization remains internal. Audit creation, validation/refusal, reimbursement declaration, attachment replacement/removal, ownership-share change, cash-pool income, and cash-pool withdrawal; never silently destroy historical evidence.
- Prefer small cohesive functions/classes, explicit types, fail-fast validation, immutable values, readable and non-redundant names (for example `member: MemberId`, not `memberId: MemberId`), explicit money and audit/event models, and declarative collection transformations. These preferences do not justify harder-to-read functional/reactive code.
- Avoid god services, hidden side effects, framework leakage into inner layers, persistence/transport concerns in Domain, destructive mutation of financial history, and forced casts such as `as unknown as` or `as any as`, including tests.

### Verification and acceptance checklist

- All modified backend files pass configured formatting, static analysis, tests, and native checks when relevant (especially CI). Use `./gradlew ktlintFormat` to format and `./gradlew ktlintCheck` in CI; CI must not auto-correct. Do not repeatedly run native compilation after every edit, but run it when native compatibility is materially at risk or before delivery of significant backend work when practical; CI should keep native compilation present as far as possible.
- A backend-impacting change is not done until the full `./gradlew backendMutationTest` task passes on the integrated working tree. Targeted PIT runs may support diagnosis during development, but they do not replace this final mutation-test run.
- Only the primary/root agent runs PIT. Sub-agents MUST NOT execute full or targeted PIT tasks; they hand their changes and ordinary test results back to the primary agent, which runs mutation testing once the relevant work is integrated. This prevents concurrent Test Resources usage and mutation-report overwrites.
- All modified frontend files pass configured formatting, lint, type checking, tests, and production build; fix violations rather than bypassing rules.
- Do not bypass backend checks either: fix the code or configuration while preserving the stated constraints.
- Typical relevant commands are `./gradlew check`, `./gradlew test`, and `./gradlew nativeCompile` for backend-impacting work; `npm run lint`, `npm run test`, and `npm run build` for frontend work. Use root Gradle orchestration when configured; prefer project-scoped tasks or CI matrix execution where appropriate, while frontend-only CI may use direct npm.
- Before delivery or PR, confirm product rules, clean boundaries and inward dependencies, native compatibility, tests, no forbidden mocking, auditability/historical traceability, and quality checks.

## 9. Commit messages

Use Conventional Commits: `<type>(<optional-scope>): <imperative summary>`.

- Allowed types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `build`, `ci`.
- Summaries are imperative and specific; use a scope when useful, add a body for non-trivial changes, and reference tickets in the footer when available.
- Examples: `feat(expenses): validate participant approval flow`; `fix(reimbursements): reject contested repayment document`; `refactor(backend-ledger): extract balance computation service`; `build(frontend): add angular lint and test scripts`; `ci(monorepo): run root gradle frontend and backend checks`.
