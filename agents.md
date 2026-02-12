# Agents Guide

This document provides instructions for AI agents and developers working on the Thereabout project.

## Running Tests

To run all tests in the project, use Maven:

```bash
mvn clean install
```

This command will:
- Clean previous build artifacts
- Compile the project
- Run all unit and integration tests
- Package the application

## Database Setup for Tests

The tests require a MariaDB database to be available. If the database is unavailable when running tests, you can start it using Docker Compose:

```bash
docker-compose -f docker-compose-development.yaml up -d
```

This will start a MariaDB container that the tests can connect to. The database will be available on port 3306.

To stop the database when you're done:

```bash
docker-compose -f docker-compose-development.yaml down
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

- The test profile is configured to use the database connection settings from your environment variables
- Make sure the database is running before executing tests, otherwise tests will fail with connection errors
- The `docker-compose-development.yaml` file contains the MariaDB service configuration needed for development and testing
