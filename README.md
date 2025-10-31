# StormtrooperX

[![Build Status](https://github.com/GooberCraft/StormtrooperX/workflows/Build/badge.svg)](https://github.com/GooberCraft/StormtrooperX/actions)
[![License](https://img.shields.io/github/license/GooberCraft/StormtrooperX)](LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/GooberCraft/StormtrooperX)](https://github.com/GooberCraft/StormtrooperX/releases)
[![bStats Servers](https://img.shields.io/bstats/servers/27782)](https://bstats.org/plugin/bukkit/StormtrooperX/27782)
[![bStats Players](https://img.shields.io/bstats/players/27782)](https://bstats.org/plugin/bukkit/StormtrooperX/27782)

A lightweight Minecraft Spigot plugin that nerfs the accuracy of ranged mobs like Skeletons, Strays, Bogged, Pillagers, and Piglins - because they aim like stormtroopers!

> **Note:** This is a refactored and enhanced version of [Stormtrooper](https://github.com/byteful/Stormtrooper) by byteful, with added features including multi-mob support, version compatibility, and improved velocity handling.

## Features

- Reduces accuracy of configurable ranged mobs
- Per-entity configuration (enable/disable specific mobs)
- Adjustable accuracy modifier (0.0 = perfect aim, 1.0 = complete chaos)
- **Player opt-out system** - individual players can choose normal mob accuracy
- In-game reload command
- Automatic update checker (checks GitHub releases on startup)
- Anonymous usage statistics via [bStats](https://bstats.org/plugin/bukkit/StormtrooperX/27782)
- Persistent storage using H2 database
- Debug mode for troubleshooting
- Lightweight with minimal performance impact

## Supported Mobs

### Bow Users
- Skeleton (1.13+)
- Stray (1.13+)
- Bogged (1.21+ only)

### Crossbow Users
- Pillager (1.14+ only)
- Piglin (1.16+ only)

## Installation

1. Download the latest release from the [Releases](../../releases) page
2. Place `StormtrooperX.jar` in your server's `plugins` folder
3. Restart your server or use a plugin manager to load it
4. Configure the plugin in `plugins/StormtrooperX/config.yml`

## Configuration

The plugin generates a `config.yml` file in `plugins/StormtrooperX/`:

```yaml
# StormtrooperX Configuration
config-version: 2

# Per-Entity Configuration
# Each entity can have individual accuracy settings
# accuracy: 0.0 = perfect aim, 1.0+ = very inaccurate

entities:
  # Bow Users (1.13+)
  skeleton:
    enabled: true
    accuracy: 0.7
  stray:
    enabled: true
    accuracy: 0.7
  bogged:  # 1.21+ only
    enabled: true
    accuracy: 0.7

  # Crossbow Users
  pillager:  # 1.14+ only
    enabled: true
    accuracy: 0.7
  piglin:  # 1.16+ only
    enabled: true
    accuracy: 0.7

# Check for updates on plugin startup
check-for-updates: true

# Debug Mode
debug: false
```

### Configuration Options

**Per-Entity Settings:**
- Each entity has two settings:
  - **enabled** (true/false): Whether to nerf this entity type
  - **accuracy** (0.0 - 1.0+): How much deviation to add
    - 0.0 = perfect aim (no nerf)
    - 0.7 = default balanced nerf
    - 1.0+ = very inaccurate

**Entities:**
- **skeleton**: Skeleton mobs. Default: enabled, accuracy 0.7
- **stray**: Stray mobs. Default: enabled, accuracy 0.7
- **bogged**: Bogged mobs (1.21+ only). Default: enabled, accuracy 0.7
- **pillager**: Pillager mobs (1.14+ only). Default: enabled, accuracy 0.7
- **piglin**: Piglin mobs (1.16+ only). Default: enabled, accuracy 0.7

**Other Settings:**
- **config-version**: Config format version (DO NOT MODIFY - used for automatic migrations)
- **check-for-updates**: Automatically check for updates on startup. Default: true
- **debug**: Enable detailed logging for troubleshooting. Default: false

### Config Migration

**Upgrading from v1.3.0 or earlier:**
The plugin automatically migrates old config formats to the new per-entity format. Your settings will be preserved:
- Old global `accuracy` value → applied to all entities
- Old entity enable/disable settings → preserved
- Migration happens automatically on first load

## Commands

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/stormtrooperx` | `/stx`, `/stormtrooper` | Show plugin info | `stormtrooperx.use` |
| `/stormtrooperx reload` | - | Reload configuration | `stormtrooperx.reload` |
| `/stormtrooperx optout` | `/stx toggle` | Toggle mob accuracy nerfs for yourself | `stormtrooperx.optout` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `stormtrooperx.use` | Access to main command | op |
| `stormtrooperx.reload` | Reload plugin configuration | op |
| `stormtrooperx.optout` | Opt-out of mob accuracy nerfs | true |

## Building from Source

### Prerequisites

- Java 8 or higher
- Maven 3.6 or higher

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/GooberCraft/StormtrooperX.git
   cd StormtrooperX
   ```

2. Build with Maven:
   ```bash
   mvn clean package
   ```

3. The compiled JAR will be in `target/StormtrooperX-1.0.jar`

### Automated Builds

This repository includes GitHub Actions workflows:

- **Build Workflow** (`.github/workflows/build.yml`): Automatically builds the plugin on every push to master/develop and pull requests. Tests against Java 8, 11, 17, and 21 to ensure compatibility. Includes automated testing with JUnit and code coverage reporting. Artifacts are available in the Actions tab.
- **Release Workflow** (`.github/workflows/release.yml`): Automatically builds and attaches the JAR file to GitHub releases when you create a new release.

## Compatibility

- **Minecraft Version**: 1.13 - 1.21.x (and future versions)
- **Server Software**: Spigot, Paper, Purpur, or any Spigot-based server
- **Java Version**: 8+ (tested on Java 8, 11, 17, and 21)

**Version Notes**:
- Minecraft 1.13-1.16.5: Java 8+
- Minecraft 1.17-1.17.1: Java 16+
- Minecraft 1.18+: Java 17+
- The Bogged mob type is only available in Minecraft 1.21+. On earlier versions, the plugin will work normally but Bogged will be unavailable.

## Support

If you encounter any issues or have suggestions:
1. Check the [Issues](../../issues) page to see if it's already reported
2. Open a new issue with details about your setup and the problem

## Metrics

This plugin uses [bStats](https://bstats.org/) to collect anonymous usage statistics. This helps understand how the plugin is being used and guides future development.

**Data collected includes:**
- Server software and version
- Plugin version
- Java version
- Server location (country)
- Player count

You can view the statistics at: https://bstats.org/plugin/bukkit/StormtrooperX/27782

To opt-out of metrics collection, set `enabled: false` in `plugins/bStats/config.yml`.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please check the [Code of Conduct](CODE_OF_CONDUCT.md) and review existing [Issues](https://github.com/GooberCraft/StormtrooperX/issues) and [Pull Requests](https://github.com/GooberCraft/StormtrooperX/pulls) before contributing.

Ways to contribute:
- 🐛 [Report bugs](https://github.com/GooberCraft/StormtrooperX/issues/new?template=bug_report.yml)
- 💡 [Suggest features](https://github.com/GooberCraft/StormtrooperX/issues/new?template=feature_request.yml)
- 📖 Improve documentation
- 🧪 Write tests
- 💻 Submit pull requests

## Credits

- **Original Plugin**: [Stormtrooper](https://github.com/byteful/Stormtrooper) by [byteful](https://github.com/byteful)
- **Refactor & Enhancements**: GooberCraft

Made with :heart: by GooberCraft
