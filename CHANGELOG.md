# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Extracted the projectile-perturbation math out of `StormtrooperX.onBowShoot` into a package-private `ProjectileNerf.perturb(Vector, double, Supplier<Vector>)` helper. The random source is now injectable, making the speed-preserving math testable without a Bukkit event mock. Behavior is unchanged.
- `EntityConfig` widened from `private` to package-private so tests can construct it directly instead of through reflection.

### Tests
- Added AssertJ (`assertj-core 3.27.3`) as a test dependency for fluent assertions and richer failure messages.
- Added a Jacoco package-level **branch coverage** rule (`BRANCH COVEREDRATIO >= 0.50`) alongside the existing line-coverage rule. Current coverage: 72% line / 75% branch.
- `.github/workflows/build.yml` and `release.yml` now run `mvn clean verify` instead of `mvn clean package` so the Jacoco gate is actually enforced in CI (`jacoco:check` is bound to the `verify` phase by default — the previous `package` runs never invoked it).
- Reorganized large test classes with JUnit 5 `@Nested` groups and `@DisplayName` for readable reports (Surefire, IDE).
- Converted obvious repeat-shape tests in `UpdateCheckerTest`, `DatabaseManagerMySQLTest`, and `StormtrooperXTest` to `@ParameterizedTest` (`@CsvSource` / `@ValueSource` / `@NullAndEmptySource`).
- New `ProjectileNerfTest` for the extracted pure function — verifies speed preservation, zero-velocity guard, mutation semantics, and accuracy clamping behavior with a deterministic random source.
- New `support/` test package: `TestSupport` (reflective field injection + private-method invocation helper) and `InlinePluginScheduler` (shared inline scheduler stub) replace duplicated boilerplate across four test classes.
- Added `src/test/resources/fixtures/config-v2.yml` and `config-v3.yml`; `ConfigMigrationTest` now exercises real YAML load -> migrate -> assert in addition to the existing mock-based verifications.
- Added missing logger verification on `OptOutManager` exception paths so silently-swallowed DB failures will fail the test.
- New `UpdateCheckerTest` cases (13 total) covering `validateReleaseTag` accept and reject sets — semver shape with optional `v` prefix, pre-release / build suffixes, 1–4 segment counts, and rejection of null/empty input, CR/LF injection, whitespace, HTML and shell metacharacters, 5+ segments, and non-numeric leading segments.

### Added
- Permission-aware tab completion for `/stormtrooperx`. The first argument completes to only the subcommands the sender is allowed to run, and the second argument of `optout`/`optin` completes to online player names for admins with `stormtrooperx.optout.others`.
- `/stormtrooperx help` subcommand that lists the commands the sender is permitted to run.
- `/stormtrooperx optin` as the idempotent counterpart to `optout`. `optout` and `optin` are now idempotent (always set the named state); `toggle` keeps the flip behavior. Running an already-set command reports "you are already opted out/in" rather than performing a no-op write.
- Admin opt-out management: `/stormtrooperx optout <player>` and `/stormtrooperx optin <player>` for online players, gated by the new `stormtrooperx.optout.others` permission. Targets are notified that an admin changed their state.
- `stormtrooperx.admin` permission required for `/stormtrooperx reload`; also grants `stormtrooperx.optout.others` via permission children. The previously-planned separate `stormtrooperx.reload` permission node has been folded into `stormtrooperx.admin` — there is no granular reload-only permission.
- Join-time reminder: players who are opted out now receive a chat message after their state loads, so they remember the preference persists across sessions.
- PlaceholderAPI soft dependency. When PAPI is installed, `%stormtrooperx_optout%` resolves to `true`/`false` for the requesting player. The expansion persists across `/papi reload`.

### Changed
- `stormtrooperx.use` default changed from `op` to `true` so non-op players can actually invoke `/stormtrooperx optout` (which is itself `default: true`). Previously the command itself was gated at the Bukkit layer, making the `optout` permission unreachable for non-ops.
- `/stormtrooperx optout` no longer toggles. It now always sets the player to opted-out (idempotent). Use `/stormtrooperx toggle` for the previous flip behavior; `toggle` was already an alias for `optout` so existing muscle memory still works.
- `onBowShoot` now declares `@EventHandler(ignoreCancelled = true)` so the projectile-perturbation work is skipped when an upstream plugin (region protection, anti-cheat, etc.) cancels `EntityShootBowEvent`.
- HikariCP MySQL pool now sets the MySQL Connector/J performance properties recommended by the HikariCP wiki: `useServerPrepStmts`, `useLocalSessionState`, `rewriteBatchedStatements`, `cacheResultSetMetadata`, `cacheServerConfiguration`, `elideSetAutoCommits`, and `maintainTimeStats=false`. No config-file change required; the existing `cachePrepStmts` / `prepStmtCacheSize` / `prepStmtCacheSqlLimit` are kept.
- Internal hot-path micro-optimizations: zero-velocity guard in `onBowShoot` uses `lengthSquared()` to skip an unnecessary `sqrt`; the projectile reference is cached for its two uses; `DatabaseManager` caches a `boolean isH2` field at construction to replace per-operation `String#equals` against `databaseType`; tab completion assembles candidates from pre-sorted permission-tier pools and drops `Collections.sort`; pattern-matching `instanceof` replaces the explicit `Mob` / `Player` / `Player`-from-`CommandSender` casts.

### Fixed
- Folia regional-thread safety on `/stormtrooperx reload`. `entityConfigs` and `debug` are now `volatile`, and `loadConfiguration` builds a fresh `EnumMap` and assigns it as a single reference write rather than mutating the published map. Previously the `clear()` + per-entity `put()` sequence created a window where a Folia regional thread handling `EntityShootBowEvent` could observe a partially populated map and either skip nerfing or read stale state.

### Security
- Hardened the `UpdateChecker` release-tag flow against CWE-117 log injection. Release tags returned by the GitHub Releases API are now validated against a strict `^v?\d+(\.\d+){0,3}([-+][A-Za-z0-9.-]+)?$` shape by a new `validateReleaseTag` barrier before they can reach any logger or `compareVersions`. A tampered release page (or a successful TLS interception) can no longer forge log entries via CR/LF in `tag_name`; malformed tags are dropped at the source and the update check logs a generic warning instead of the offending value. As a side effect, `parseVersionPart` is no longer reachable with malformed input.
- Closed the `java/log-injection` CodeQL alert on `StormtrooperX.checkForUpdates`. The callback logged `currentVersion` / `latestVersion` directly; both are shape-validated upstream — `latestVersion` via `UpdateChecker.validateReleaseTag`, `currentVersion` from `plugin.yml` — but the guard sits across the `UpdateChecker` callback boundary where CodeQL cannot trace it. A new `sanitizeForLog` helper reduces the value to `[A-Za-z0-9._+-]` at the logging sink itself: an allowlist `replaceAll` (the negated-character-class form CodeQL's `java/log-injection` query recognizes as a sanitizer — a `[\r\n]` denylist is *not* recognized), which drops CR/LF and any other unexpected character. (The earlier CHANGELOG wording crediting `validateReleaseTag` with closing this alert was inaccurate — that change sanitized the source side, not the `StormtrooperX` sink.)
- `DatabaseManager` MySQL `properties` are now validated against a curated allowlist (`SAFE_MYSQL_PROPERTY_KEYS`) before being appended to the JDBC URL, and values are URL-encoded. Closes a parameter-smuggling vector where an admin (or a compromised config) could enable Connector/J flags with a known RCE / file-read history (`allowLoadLocalInfile`, `autoDeserialize`, `queryInterceptors`, `propertiesTransform`, `allowMultiQueries`, etc.) by including them as a property key, or by embedding `&key=value` in another property's value. Allowlisted keys cover SSL/TLS, time/encoding, network behavior, and the `cachePrepStmts` family. Unsupported keys now throw `IllegalArgumentException` at `initialize()` with a pointer to file an issue if the allowlist needs widening.
- Defense-in-depth Connector/J defaults: `allowLoadLocalInfile=false`, `allowUrlInLocalInfile=false`, `autoDeserialize=false`, and `allowPublicKeyRetrieval=false` are now set as HikariCP `dataSourceProperties` so a future Connector/J default change cannot silently re-enable them. URL-side params win on conflict, but the new allowlist already prevents these keys from appearing there.
- Player names echoed by the offline branch of `/stormtrooperx optout|optin <player>` are sanitized through a new `sanitizeNameForEcho` helper that replaces ASCII control characters and the Bukkit color section sign (`§`) with `?` and caps the echoed name at Mojang's 16-character username limit. Closes a low-severity injection where a hostile admin on an offline-mode server could supply a "name" containing CR/LF or color codes to inject styled content into another admin's chat. The online-target branch echoes `Player#getName()`, which is server-validated, and is unchanged.
- Bumped `org.assertj:assertj-core` from `3.27.3` to `3.27.7` to close Dependabot alert #3 (GHSA-rqfh-9r24-8c9r / CVE-2026-24400 — XXE in the `isXmlEqualTo` assertion). Test-scope only, and the project does not use `isXmlEqualTo`, so there was no exploitable path; the bump keeps the dependency on a non-flagged version.

### Documentation
- `OptOutManager#isOptedOut` and `StormtrooperXExpansion`'s class Javadoc now explicitly document the **online-only** semantics of the cache and the `%stormtrooperx_optout%` placeholder: offline players resolve to `false` even if their persisted state is "opted out". Mirrored in the README PlaceholderAPI section. Behavior is unchanged; this only documents an intentional but previously-implicit invariant.
- `DatabaseManager.initializeH2` has a Javadoc warning against switching to `AUTO_SERVER=TRUE` or H2 server mode without re-evaluating the hardcoded `sa`/empty credentials — they are only safe in the embedded, file-backed mode used today.
- `UpdateChecker.extractJsonValue` has a Javadoc warning that it is single-field-only (the `tag_name` field, which is independently validated by `RELEASE_TAG_PATTERN`). Anything beyond `tag_name` requires a real JSON parser.

### Build
- New CycloneDX SBOM (`cyclonedx-maven-plugin` 2.9.1) bound to the `verify` phase. Outputs `target/bom.xml` and `target/bom.json` documenting the shaded dependency tree (bStats, H2, HikariCP, MySQL Connector/J). The release workflow now uploads both alongside the JAR.
- `.github/workflows/release.yml` actions are now pinned to commit SHAs (with `# vX.Y.Z` trailers) instead of mutable major tags. The release job holds `id-token` + `attestations: write` scope, so a force-pushed tag or compromised action — particularly the third-party `softprops/action-gh-release` and `cloudnode-pro/modrinth-publish` — could otherwise inject code into the signing path. All five actions were already on their current major version; no functional change. Dependabot's `github-actions` ecosystem reads the trailer and bumps both SHA and comment via a reviewed PR.
- `DatabaseManager` now uses `Locale.ROOT` for the `databaseType` lowercase fold (avoids the Turkish `I`/`İ` round-trip that could change `MYSQL` under `tr_TR`).
- Removed the deprecated `reviewers:` field from `.github/dependabot.yml`. Sole maintainer; CODEOWNERS is unnecessary.

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

## [Unreleased]

### Planned
- Support for projectile-throwing mobs (Drowned with tridents, Witches, Ghasts, Blazes, etc.)

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
