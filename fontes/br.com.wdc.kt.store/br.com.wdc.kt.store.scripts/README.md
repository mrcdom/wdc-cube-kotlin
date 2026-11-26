# store-scripts

Scripts for creating, seeding and migrating the database (H2 and PostgreSQL), the jOOQ class generator, and the generators of design tokens and UI texts.

## Overview

This module is responsible for the whole lifecycle of the database schema:

1. **Table creation** — checks which tables exist and creates the missing ones
2. **Initial data seed** — populates the database with sample users, products and purchases
3. **Incremental migrations** — applies schema changes in an ordered and idempotent way

## Components

| Class | Responsibility |
|-------|----------------|
| `DBCreate` | Orchestrates table creation and migration execution |
| `DBReset` | Clears all tables and inserts sample data (seed) |
| `MigrationRunner` | Runs migration scripts, recording the steps already executed |
| `Migration_NNNN_*` | Individual migration scripts with numbered steps |
| `GenerateJooqSchema` | Generates the jOOQ classes of `:store-persistence` from the `DBCreate` DDL |
| `GenerateDesignTokens` | `:store-design`'s `tokens.json` → `StoreTokens.kt`, the TypeScript client's `tokens.ts` and the landing page's `tokens.css` (`./gradlew :store-scripts:generateDesignTokens`; `DesignTokensTest` fails while they are stale) |
| `GenerateMessages` | `:store-i18n`'s `messages.<language>.json` → `StoreMessages.kt` and the TypeScript client's `messages.ts` (`./gradlew :store-scripts:generateMessages`; `MessagesCatalogTest` fails while they are stale) |

## How It Works

### DBCreate

Entry point, and **source of truth of the schema**: the DDL is written here, with the type variants of each database. It queries the JDBC metadata to find out which tables exist and creates the missing ones, with their sequences and indexes.

Then it runs the pending migrations (`MigrationRunner`) and, last, if any table was created or if `withReset()` was called, loads the demo data (`DBReset`) — unless `withSkipReset()` was called, as the jOOQ class generator does. The seed comes after the migrations because it already writes in the current format.

```kotlin
// At backend startup (BusinessContext)
DBCreate()
    .withConnection(connection)
    .run()

// In tests (TestEnvironment) — forces a reset to guarantee a clean state
DBCreate()
    .withConnection(connection)
    .withReset()
    .run()
```

### DBReset (Seed)

Clears all tables in the right order (respecting foreign keys) and inserts sample data:

- **Users**: `admin` (role ADMIN), `fulano` and `beotrano` (role CUSTOMER)
- **Products**: Italian Design Coffee Maker, Wilson Ball, Thread Seal Tape, USB Flash Drive 2GB — with HTML descriptions and images read from the classpath (`META-INF/images/`, in the resources of `:store-persistence`)
- **Purchases**: 2 purchases by admin with 3 items in total

Every table's ids start at 1 (the Home refuses a non-positive product id); existing databases keep their ids until a reset. The generated IDs are available as static fields (`DBReset.ADMIN_ID`, `DBReset.COFFEE_MAKER_ID`, etc.) for use in tests.

Passwords are stored as SCRAM-SHA-256 verifiers with a random salt per user (`PasswordVerifiers`), the form the login checks; see [security-architecture.md](../../../docs/security-architecture.md#login-by-challenge-and-response-scram-sha-256). Dates are stored in UTC.

### MigrationRunner

Migration system based on **reflection**:

1. Receives an instance of a migration script (e.g. `Migration_0001_AddUserRoles`)
2. Discovers, via reflection, the public methods whose name starts with `step`
3. Sorts them by step number (`step01_...`, `step02_...`)
4. For each step, checks in the control table `EN_MIGRATION_LOG` whether it was already executed
5. If not, runs the method and records the step with a timestamp

This guarantees **idempotency** — running `DBCreate` multiple times is safe. Steps already executed are skipped.

### Creating a New Migration

1. Create a `Migration_NNNN_DescriptiveName` class in the `sgbd` package:

```kotlin
class Migration_0012_AddProductCategory(private val connection: Connection) {

    fun step01_addCategoryColumn() {
        connection.createStatement().use { stmt ->
            stmt.execute("ALTER TABLE EN_PRODUCT ADD COLUMN IF NOT EXISTS CATEGORY VARCHAR(100) DEFAULT 'GENERAL'")
        }
    }

    fun step02_setCategoryForExisting() {
        connection.createStatement().use { stmt ->
            stmt.execute("UPDATE EN_PRODUCT SET CATEGORY = 'ELECTRONICS' WHERE NAME LIKE '%Flash Drive%'")
        }
    }
}
```

2. Register it at the end of the list in `DBCreate.run()`:

```kotlin
MigrationRunner(conn)
    // …the previous ones
    .run(Migration_0011_LoginFailureTable(conn))
    .run(Migration_0012_AddProductCategory(conn))  // new
```

3. Make the same change in the `DBCreate` DDL — a new database is born in the final format — and regenerate the jOOQ classes:

```bash
cd fontes && ./gradlew :store-scripts:generateJooqSchema
```

Rules for steps:
- Methods must be **public** and **parameterless**
- The name must start with `step` followed by a number (`step01_`, `step02_`)
- Steps run in numeric order
- Each step must be **self-contained** — if it fails, the previous ones are already recorded
- The SQL must work on H2 and on PostgreSQL; when the change only makes sense on one of them, check the database with `DBCreate.detectDialect(connection)`

## Existing Migrations

| Migration | Steps | Description |
|-----------|-------|-------------|
| `Migration_0001_AddUserRoles` | 2 | Adds the `ROLES` column to the `EN_USER` table and sets the ADMIN role for the admin user |
| `Migration_0002_PurchaseBuyDateToTimestamp` | 1 | Changes the `BUYDATE` column from DATE to TIMESTAMP |
| `Migration_0003_CreateSecurityTables` | 2 | Creates the tables `EN_USER_INTENT_SECRET` (per-user HMAC secrets) and `EN_USER_SESSION` (persistent sessions with RSA key pairs) |
| `Migration_0004_ImageVarbinaryAndOrderingIndexes` | 3 | Variable-size product image (removes the zero padding) and the ordering indexes |
| `Migration_0005_UnsignedPasswordDigest` | 1 | Rewrites the password digests that the old seed stored as signed |
| `Migration_0006_PurchaseBuyDateToUtc` | 1 | Moves the purchase date from local time to UTC |
| `Migration_0007_SessionExpiryToUtc` | 1 | Discards the sessions stored with the expiry in local time |
| `Migration_0008_UserRolesDefaultCustomer` | 1 | Sets `DEFAULT 'CUSTOMER'` on `ROLES` where `DBCreate` had created the column without it: a user written without roles is a customer in every database |
| `Migration_0009_PasswordScramVerifier` | 2 | Widens `PASSWORD` and turns every stored MD5 digest into a SCRAM-SHA-256 verifier derived from it: no password needed, and every user keeps theirs |
| `Migration_0010_AuthNonceTable` | 1 | Creates `EN_AUTH_NONCE`: the login nonces in the database, shared by every instance |
| `Migration_0011_LoginFailureTable` | 1 | Creates `EN_LOGIN_FAILURE`: failed sign-ins per user, shared by every instance |

## Use in the Modules

- **backend** — `BusinessContext` calls `DBCreate().withConnection(conn).run()` at Javalin server startup
- **store-tests** — `TestEnvironment` and `RestTestEnvironment` call `DBCreate().withConnection(conn).withReset().run()` to guarantee a clean database before each test

## Dependencies

- `store-persistence` — target of the generated jOOQ classes (and, through it, jOOQ and `framework-jooq`)
- `jooq-codegen` — the class generator
- `h2` — database used by the class generator
