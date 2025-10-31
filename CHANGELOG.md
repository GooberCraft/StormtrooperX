# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- Support for additional ranged mobs (e.g., Pillagers, Piglins, Drowned)
- Per-mob accuracy settings
- Configurable accuracy ranges per entity type

---

[1.2.2]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.2.2
[1.2.1]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.2.1
[1.2.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.2.0
[1.1.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.1.0
[1.0.0]: https://github.com/GooberCraft/StormtrooperX/releases/tag/v1.0.0
