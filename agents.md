# Agents Guide

This document provides instructions for AI agents and developers working on the Thereabout project.

## Component guidance and simplicity

- Follow `backend/AGENTS.md` and `frontend/AGENTS.md` for the relevant component.
- Prefer the simplest design that fully meets the requirements. Do not add speculative abstractions or weaken correctness, security or data integrity to reduce code.
- Explain constraints and invariants rather than repeating signatures in comments. Keep changes scoped and preserve unrelated work.

## Running Tests

Only [`backend/pom.xml`](backend/pom.xml) is a Maven project (no root reactor). Run Maven from `backend/`—not `mvn -pl backend` from the repo root.

To run all tests:

```bash
cd backend && mvn clean install
```

This command will:
- Clean previous build artifacts
- Compile the project
- Run all unit and integration tests
- Package the application

## Database Setup for Tests

Use the isolated test instance, never the development or production database:

```bash
docker compose -f docker-compose-test.yaml up -d --wait
cd backend && mvn clean install
```

The instance binds to `127.0.0.1:3307` and uses `thereabout_test` with a dedicated user and storage. Test settings use `THEREABOUT_TEST_DB_URL`, `THEREABOUT_TEST_DB_USER` and `THEREABOUT_TEST_DB_PASSWORD`; the database name must end in `_test`. Tests may delete fixture data. Never reset existing development data without explicit permission.

Stop only the test instance when finished:

```bash
docker compose -f docker-compose-test.yaml down
```

## Testing

### Assertions

Use **AssertJ** for all assertions in tests. AssertJ provides a fluent API that is more readable and provides better error messages than JUnit assertions.

**Example:**
```java
import static org.assertj.core.api.Assertions.assertThat;

// Instead of:
assertEquals(expected, actual);
assertTrue(condition);
assertFalse(condition);

// Use:
assertThat(actual).isEqualTo(expected);
assertThat(condition).isTrue();
assertThat(condition).isFalse();

// For BigDecimal comparisons:
assertThat(bigDecimal).isEqualByComparingTo(expected);
```

AssertJ is included in `spring-boot-starter-test`, so no additional dependency is needed.

## JPA Entities

Do not use `@Column(name = "...")` when the column name matches Hibernate's automatic camelCase-to-snake_case conversion (e.g. `firstName` already maps to `first_name`). Only use `@Column` when specifying constraints like `nullable`, `precision`, `length`, or `updatable`.

## Notes

- Run targeted behavioural tests during implementation, then the relevant full checks. Avoid redundant tests that merely mirror the implementation.
- Use real domain objects in tests; mock external dependencies and collaborators.
- The test database must be running before backend integration tests. CI follows the same isolated database naming convention.
