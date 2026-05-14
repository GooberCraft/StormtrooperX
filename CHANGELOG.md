# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Permission-aware tab completion for `/stormtrooperx`: the first argument completes to only the subcommands the sender may run; the second argument of `optout`/`optin` completes to online player names for admins with `stormtrooperx.optout.others`.
- `/stormtrooperx help` subcommand, listing only the commands the sender is permitted to run.
- `/stormtrooperx optin` as the idempotent counterpart to `optout`. `optout`/`optin` now always set the named state (reporting "already opted out/in" instead of a no-op write); `toggle` keeps the flip behavior.
- Admin opt-out management: `/stormtrooperx optout|optin <player>` for online players, gated by the new `stormtrooperx.optout.others` permission. Targets are notified of the change.
- `stormtrooperx.admin` permission, required for `/stormtrooperx reload` and granting `stormtrooperx.optout.others` via permission children.
- Join-time reminder: opted-out players get a chat message once their state loads.
- PlaceholderAPI soft dependency — `%stormtrooperx_optout%` resolves to `true`/`false` for the requesting player; the expansion persists across `/papi reload`.

### Changed
- Extracted the projectile-perturbation math into a package-private `ProjectileNerf.perturb(Vector, double, Supplier<Vector>)` helper with an injectable random source. Behavior unchanged.
- `EntityConfig` widened from `private` to package-private for direct test construction.
- `stormtrooperx.use` default changed from `op` to `true` so non-op players can reach `/stormtrooperx optout` (itself `default: true`).
- `/stormtrooperx optout` no longer toggles — it always sets opted-out (idempotent). Use `/stormtrooperx toggle` for the flip behavior.
- `onBowShoot` declares `@EventHandler(ignoreCancelled = true)`, skipping perturbation when an upstream plugin cancels `EntityShootBowEvent`.
- HikariCP MySQL pool sets the Connector/J performance properties recommended by the HikariCP wiki (`useServerPrepStmts`, `useLocalSessionState`, `rewriteBatchedStatements`, `cacheResultSetMetadata`, `cacheServerConfiguration`, `elideSetAutoCommits`, `maintainTimeStats=false`). No config change.
- Hot-path micro-optimizations: `lengthSquared()` zero-velocity guard, cached projectile reference, cached `isH2` boolean, pre-sorted tab-completion pools, pattern-matching `instanceof`.

### Fixed
- Folia regional-thread safety on `/stormtrooperx reload`: `entityConfigs` and `debug` are now `volatile`, and `loadConfiguration` publishes a fresh `EnumMap` in a single reference write instead of mutating the live map (which could expose a partially populated map to a regional `EntityShootBowEvent` thread).

### Security
- Hardened the `UpdateChecker` release-tag flow against CWE-117 log injection. GitHub release tags are validated against `^v?\d+(\.\d+){0,3}([-+][A-Za-z0-9.-]+)?$` by a new `validateReleaseTag` barrier before reaching any logger or `compareVersions`; malformed tags are dropped at the source.
- Closed the `java/log-injection` CodeQL alert on `StormtrooperX.checkForUpdates`. A new `sanitizeForLog` helper reduces the logged version strings to `[A-Za-z0-9._+-]` at the sink — an allowlist `replaceAll`, the negated-character-class form CodeQL recognizes as a sanitizer (a `[\r\n]` denylist is not).
- `DatabaseManager` validates MySQL `properties` against an allowlist (`SAFE_MYSQL_PROPERTY_KEYS`) and URL-encodes values, closing a parameter-smuggling vector that could otherwise enable Connector/J flags with RCE / file-read history (`allowLoadLocalInfile`, `autoDeserialize`, `queryInterceptors`, ...). Unsupported keys throw `IllegalArgumentException` at `initialize()`.
- Defense-in-depth Connector/J defaults set as HikariCP `dataSourceProperties`: `allowLoadLocalInfile`, `allowUrlInLocalInfile`, `autoDeserialize`, and `allowPublicKeyRetrieval` are all forced `false`.
- Player names echoed by the offline branch of `/stormtrooperx optout|optin <player>` are sanitized via a new `sanitizeNameForEcho` helper (strips control chars and the `§` color sign, caps at 16 chars), closing a low-severity chat-injection vector on offline-mode servers.
- Pinned the AssertJ test dependency to `assertj-core` 3.27.7, clear of CVE-2026-24400 (GHSA-rqfh-9r24-8c9r — XXE in `isXmlEqualTo`; test-scope only and the method is unused).

### Documentation
- `OptOutManager#isOptedOut`, `StormtrooperXExpansion`, and the README PlaceholderAPI section now document the **online-only** semantics of `%stormtrooperx_optout%`: offline players resolve to `false` regardless of persisted state. Behavior unchanged.
- `DatabaseManager.initializeH2` Javadoc warns against `AUTO_SERVER=TRUE` / H2 server mode without revisiting the hardcoded `sa`/empty credentials.
- `UpdateChecker.extractJsonValue` Javadoc warns it is `tag_name`-only; other fields need a real JSON parser.

### Tests
- Added AssertJ (`assertj-core`) for fluent assertions and richer failure messages.
- Added a Jacoco package-level branch-coverage rule (`BRANCH COVEREDRATIO >= 0.50`) alongside the line-coverage rule. Current: 72% line / 75% branch.
- `build.yml` and `release.yml` run `mvn clean verify` instead of `package`, so the Jacoco gate (bound to `verify`) is actually enforced in CI.
- Reorganized large test classes with `@Nested` / `@DisplayName` groups and converted repeat-shape cases to `@ParameterizedTest`.
- New `support/` test package: `TestSupport` (reflective field/method access) and `InlinePluginScheduler` (shared inline scheduler), replacing duplicated boilerplate.
- New `ProjectileNerfTest` for the extracted pure function — speed preservation, zero-velocity guard, mutation semantics, accuracy clamping.
- New YAML fixtures (`config-v2.yml`, `config-v3.yml`); `ConfigMigrationTest` exercises a real load → migrate → assert.
- Added logger verification on `OptOutManager` exception paths so silently-swallowed DB failures fail the test.
- Expanded `UpdateCheckerTest` to cover `validateReleaseTag` accept/reject sets (semver shapes, CR/LF injection, metacharacters, segment counts).

### Build
- Pinned all `release.yml` actions to commit SHAs (with `# vX.Y.Z` trailers) instead of mutable major tags — the release job holds `id-token`/`attestations: write` scope. No functional change; Dependabot keeps the pins current.
- Added a CycloneDX SBOM (`cyclonedx-maven-plugin`) on the `verify` phase; `release.yml` uploads `bom.xml`/`bom.json` alongside the JAR.
- `DatabaseManager` uses `Locale.ROOT` for the `databaseType` case fold.
- Removed the deprecated `reviewers:` field from `dependabot.yml`.

## [1.9.0] - 2026-05-13

### Added
- Folia support via a runtime-detected `PluginScheduler` abstraction. On Folia, async work routes through `AsyncScheduler` / `GlobalRegionScheduler` (via reflection so the Spigot API 1.18 compile dependency is unchanged); on Spigot/Paper the legacy `BukkitScheduler` is used. Adds `folia-supported: true` to `plugin.yml`.
- If Folia is detected but its scheduler API cannot be bound (e.g., a future signature change), `PluginScheduler.create` now logs a WARNING and falls back to the legacy scheduler instead of failing plugin enable.

### Changed
- `OptOutManager` constructor signature: replaced `Plugin` parameter with `PluginScheduler`.
- `UpdateChecker` constructor signature: added `PluginScheduler` parameter. The update-check callback is now invoked on the async worker thread (logging only — `java.util.logging` is thread-safe); the previous main-thread hop was unnecessary on Spigot/Paper and forbidden on Folia.
- `DatabaseManager` now serializes H2 operations through an internal lock. JDBC `Connection` instances are not thread-safe, and Folia's regional async pool can dispatch concurrent opt-out reads/writes onto the single shared H2 connection. The MySQL path is unchanged (HikariCP gives each thread its own pooled connection).

### Security
- Tightened `UpdateChecker` `githubRepo` validation to a strict `^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$` regex (defense-in-depth against path traversal, CRLF injection, and arbitrary URL-component characters even though the call site is hardcoded).

### Tests
- New `PluginSchedulerTest` (7 tests) covering the legacy scheduler delegation and the factory's non-Folia branch.
- New `UpdateChecker` constructor tests (5 tests) covering semicolon injection, multi-segment paths, path traversal, CRLF injection, and the valid-character allowlist.

## [1.8.0] - 2026-05-12

### Added
- Support for the Parched mob (Minecraft 1.21.11+, Mounts of Mayhem). Parched fires Arrows of Weakness with a bow and is treated like other bow users for accuracy nerfing. Gracefully disabled on servers running older versions.

### Changed
- Tightened Jacoco package line-coverage minimum from 0.50 to 0.60

### Tests
- New `EntityConfigLoadingTest` (10 tests) covering both `loadEntityConfig` variants, the version-gated `IllegalArgumentException` fallback (the path Parched takes on older servers), and the `displayEntityStatus` Disabled branch

### Dependencies
- Bump JUnit Jupiter from 5.14.3 to 6.0.3 (major version)

## [1.7.0] - 2026-04-25

### Tests
- New `BowShootEventTest` (8 tests) covering `onBowShoot()` early-return paths, the zero-velocity guard, speed-preservation invariant, and opt-out/target scenarios
- New `CommandHandlerTest` (11 tests) covering command dispatch, permission checks, opt-out toggling, and unknown subcommands
- New `ConfigMigrationTest` (4 tests) covering v2 → v3 migration (`config-version`, database defaults, pool defaults, save)
- Expand `OptOutManagerTest` with 2 async exception tests verifying `setOptOut()` swallows DB write failures without breaking cache consistency
- Expand `PluginResourceTest` with an `api-version` assertion

### Dependencies
- Bump `mysql-connector-j` from 9.6.0 to 9.7.0
- Bump GitHub Actions: `codecov/codecov-action` v5 → v6, `softprops/action-gh-release` v2 → v3 (Node 20 → 24)

### Breaking
- **Drop Java 11 support; minimum is now Java 17.** CI matrix now tests Java 17, 21, and 25
- **Drop Minecraft 1.13–1.17 support; minimum is now Minecraft 1.18.** `plugin.yml` `api-version` updated from `1.13` to `1.18`

## [1.6.3] - 2026-03-17

### Added
- Automated Modrinth publishing in the release workflow
- Modrinth downloads badge and installation link in README
- Stale issue/PR management workflow

### Dependencies
- Bump `org.jetbrains:annotations` from 26.0.2 to 26.1.0
- Bump `org.bstats:bstats-bukkit` from 3.1.0 to 3.2.1
- Bump `mysql-connector-j` from 9.5.0 to 9.6.0
- Bump `maven-compiler-plugin` from 3.14.1 to 3.15.0
- Bump `maven-surefire-plugin` from 3.5.4 to 3.5.5
- Bump `maven-shade-plugin` from 3.6.1 to 3.6.2
- Bump JUnit Jupiter from 5.12.2 to 5.14.3
- Bump Mockito (core + junit-jupiter) from 5.20.0 to 5.23.0
- Bump GitHub Actions: `attest-build-provenance` v2 → v4, `upload-artifact` v5 → v7, `cache` v4 → v5
- Pin Dependabot to ignore JUnit Jupiter 6.x

## [1.6.2] - 2025-12-01

### Performance
- Use EnumMap for faster entity config lookups (~30% improvement)

### Fixed
- Guard against zero-length velocity vectors (prevents NaN in edge cases)
- Validate MySQL pool configuration values with clear warnings for invalid settings

### Changed
- Document valid ranges for MySQL pool settings in config.yml

### Dependencies
- Update Mockito from 5.15.2 to 5.20.0

## [1.6.1] - 2025-11-30

### Security
- Add build provenance attestation using GitHub Actions and Sigstore
- All release artifacts are now cryptographically signed for supply chain security

### Added
- Release verification documentation in README
- Wiki page for verifying release authenticity

## [1.6.0] - 2025-11-29

### Added
- MySQL database support with HikariCP connection pooling
- Player opt-out system with in-memory caching for performance
- Async database operations to avoid blocking main thread
- OptOutManager class for coordinating cache and database layers
- Config version 3 with database configuration section
- Automatic config migration from v2 to v3

### Changed
- Database configuration now supports both H2 (embedded) and MySQL
- README updated with MySQL configuration documentation
- SpigotMC badges added to README

### Security
- MySQL connection parameter validation to prevent JDBC URL injection
- SSL configuration documentation for production MySQL connections

## [1.5.1] - 2025-11-08

### Security
- Added HTTP response size limit (1MB) to prevent memory exhaustion from malicious GitHub API responses
- Implemented runtime validation during response reading with abort on oversized responses
- Fixed log injection vulnerability by removing user-controlled data from log output in version parsing
- Added input validation helper `validateDatabaseOperation(UUID)` to prevent null pointer exceptions

### Fixed
- Fixed resource leak in `UpdateChecker.fetchLatestVersion()` by using try-with-resources for BufferedReader
- Fixed inverted assertions in concurrent access tests
- Added explicit unknown subcommand handling with user feedback

### Changed
- Database now uses `FILE_LOCK=SOCKET` to prevent data corruption from concurrent access
- Improved error logging consistency for null UUIDs and uninitialized database connections
- Refactored DatabaseManager to eliminate duplicated null checks

### Added
- Security-focused test cases for null input handling, large input handling, and malicious content parsing
- Test for parseVersionPart overflow handling
- Comprehensive security review documentation

## [1.5.0] - 2025-11-08

### Changed
- Migrated minimum Java version from Java 8 to Java 11
- Improved code quality and removed unused parameters

## [1.4.0] - 2025-10-31

### Added
- Per-entity accuracy configuration system - each mob type can now have its own accuracy value
- Config versioning system with `config-version` field for tracking config format
- Automatic migration from v1 (global accuracy) to v2 (per-entity accuracy) config format
- EntityConfig inner class for encapsulating entity-specific settings
- 8 new unit tests for `capitalize()` method (5 tests) and `EntityConfig` class (3 tests)
- Comprehensive test coverage for edge cases (null strings, empty strings, single character)

### Changed
- Config structure changed from flat format to nested `entities` section
- Each entity now has individual `enabled` and `accuracy` settings
- Command output (`/stormtrooperx`) now displays per-entity accuracy values
- `capitalize()` method changed to static for better utility usage
- Internal storage changed from `Set<EntityType>` to `Map<EntityType, EntityConfig>`
- Config loading now uses entity-specific accuracy values instead of global setting

### Migration Notes
- **Breaking config format change** - automatically handled by migration system
- Upgrading from v1.3.0 or earlier will trigger automatic migration
- Old global `accuracy` value is applied to all entities during migration
- Old entity enable/disable settings are preserved
- Migration is logged to console for transparency
- Original config settings are fully preserved, just reorganized

### Technical
- Added `migrateConfigToV2()` method for seamless config upgrades
- Config version detection prevents repeated migrations
- Per-entity accuracy allows future fine-tuning (e.g., crossbow mobs may need different values than bow mobs)

## [1.3.0] - 2025-10-30

### Added
- Support for crossbow-wielding mobs (Pillager, Piglin)
- Version-safe handling of Pillager (1.14+) and Piglin (1.16+)
- Configuration options for `pillager` and `piglin`
- Organized entity display in `/stormtrooperx` command by weapon type

### Changed
- Config file now categorizes entities by weapon type (Bow Users vs Crossbow Users)
- Plugin description updated to mention all supported mobs
- Command output groups entities by weapon type for better clarity
- Updated documentation with new supported mobs

## [1.2.2] - 2025-10-30

### Fixed
- Fixed plugin.yml version being hardcoded to "1.0" instead of using Maven version
- Plugin now correctly reports its actual version (1.2.2) instead of "1.0"
- UpdateChecker will now display accurate version comparisons

### Added
- PluginResourceTest to verify Maven resource filtering works correctly
- Test coverage to prevent version synchronization regressions

## [1.2.1] - 2025-10-30

### Changed
- Migrated repository from mdw19873/stormtrooperx to GooberCraft/StormtrooperX organization
- Updated UpdateChecker to query new organization for releases
- Updated all repository references in documentation (README, CHANGELOG, plugin.yml)
- Updated GitHub issue templates with new repository URLs

### Added
- CONTRIBUTING.md with comprehensive contributor guidelines
- SECURITY.md with vulnerability reporting policy and security best practices

## [1.2.0] - 2025-10-29

### Added
- Player opt-out system allowing individual players to disable mob accuracy nerfs
- H2 database integration for persistent storage of player preferences
- `/stormtrooperx optout` command for players to toggle their opt-out status
- `stormtrooperx.optout` permission (default: true)
- DatabaseManager class for H2 operations
- Shaded H2 dependency to avoid conflicts with other plugins
- Target player checking in bow shoot event handler

### Changed
- Event handler now checks if target player has opted out before applying nerfs
- Command usage updated to include optout option

## [1.1.0] - 2025-10-29

### Added
- Automatic update checker that queries GitHub Releases API
- bStats metrics integration (plugin ID: 27782) for anonymous usage statistics
- `check-for-updates` configuration option (default: true)
- Console notifications when updates are available
- UpdateChecker utility class with async version checking
- Semantic version comparison for update detection

### Changed
- README updated with metrics information and opt-out instructions

## [1.0.0] - 2025-10-29

### Added
- Initial release of StormtrooperX (refactored from [Stormtrooper](https://github.com/byteful/Stormtrooper) by byteful)
- Accuracy nerfs for Skeleton, Stray, and Bogged mobs
- Configurable accuracy modifier (0.0 - 1.0 range)
- Per-entity enable/disable configuration
- Debug mode for troubleshooting
- `/stormtrooperx` command with reload functionality and configuration display
- Permission system for commands
- Comprehensive README with installation and configuration instructions
- MIT License
- Backward compatibility with Minecraft 1.13+

### Features
- Event-driven arrow accuracy modification
- Lightweight performance footprint
- In-game configuration reload without server restart
- Color-coded console messages for better readability
- Detailed logging for enabled entities
- Version-safe handling of newer mob types (Bogged gracefully disabled on versions < 1.21)
- Arrow speed preservation (only direction is modified, not velocity magnitude)

---

[1.6.2]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.6.2
[1.6.1]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.6.1
[1.6.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.6.0
[1.5.1]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.5.1
[1.5.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.5.0
[1.4.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.4.0
[1.3.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.3.0
[1.2.2]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.2.2
[1.2.1]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.2.1
[1.2.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.2.0
[1.1.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.1.0
[1.0.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.0.0
