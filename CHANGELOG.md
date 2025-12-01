# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
