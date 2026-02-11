# Canonical AI Agent Policy

This file is the single source of truth for AI coding behavior in this repository.
All tool-specific instruction files must defer to this policy.

## Goal
Generate production-ready code that follows Clean Architecture (Uncle Bob), is fully testable, and includes meaningful automated tests.

## Architecture (Mandatory)
1. Use clear layers:
- Domain: entities, value objects, domain services, core business rules.
- Application: use cases, orchestration, ports, DTOs.
- Interface Adapters: controllers, presenters, repository/gateway adapters.
- Infrastructure: framework wiring, ORM, HTTP, filesystem, external SDKs.

2. Enforce inward dependencies only:
- Domain depends on nothing outer.
- Application may depend on Domain only.
- Adapters/Infrastructure depend inward via interfaces.

3. Define ports in inner layers and implement them in outer layers.
4. Keep business logic out of controllers/framework classes/ORM models.

## Testing (Mandatory)
1. Every behavior change must include or update tests.
2. Use case logic must be testable without framework/runtime coupling.
3. Prefer this test mix:
- Many unit tests for Domain/Application.
- Integration tests for adapters/persistence/external boundaries.
- Minimal end-to-end tests for critical flows.

4. Test behavior and outcomes, not implementation details.
5. Avoid hidden global state and nondeterminism.

## Mocking Policy (Strict)
1. Never mock external libraries, framework internals, or vendor SDKs.
2. Never mock Symfony/Doctrine internals or third-party package classes.
3. Allowed alternatives:
- In-memory implementations for interfaces owned by this codebase.
- Handwritten fakes/stubs only for project-owned ports.
- Integration/contract tests for real adapters.

4. If a dependency is hard to test, introduce a project-owned port and test the adapter at integration level.

## Quality Bar
1. Small, cohesive, explicit classes/functions.
2. Strong typing and clear boundary DTOs/value objects where useful.
3. Fail fast on invalid input.
4. Prefer immutability in domain code when practical.

## PR/Change Checklist
- [ ] Clean Architecture boundaries preserved.
- [ ] Dependencies point inward.
- [ ] Tests added/updated and passing.
- [ ] No external-library/framework mocking introduced.

## Commit Message Policy (Mandatory)
Use Conventional Commits format:

`<type>(<optional-scope>): <imperative summary>`

Allowed types:
- `feat`: new functionality
- `fix`: bug fix
- `refactor`: internal code change without behavior change
- `test`: add/update tests
- `docs`: documentation only
- `chore`: tooling/maintenance
- `perf`: performance improvement
- `build`: build/dependency changes
- `ci`: CI/CD changes

Rules:
1. Summary must be imperative and specific (max ~72 chars).
2. Include scope when useful (e.g., `auth`, `orders`, `api`).
3. Add a body explaining why and key changes when non-trivial.
4. Reference issues/tickets in footer when available.

Examples:
- `fix(auth): reject expired JWT during refresh`
- `refactor(payments): extract settlement policy from controller`
- `test(orders): cover duplicate checkout idempotency`
