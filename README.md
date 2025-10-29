# StormtrooperX

A lightweight Minecraft Spigot plugin that nerfs the accuracy of ranged mobs like Skeletons, Strays, and Bogged - because they aim like stormtroopers!

> **Note:** This is a refactored and enhanced version of [Stormtrooper](https://github.com/byteful/Stormtrooper) by byteful, with added features including multi-mob support, version compatibility, and improved velocity handling.

## Features

- Reduces accuracy of configurable ranged mobs
- Per-entity configuration (enable/disable specific mobs)
- Adjustable accuracy modifier (0.0 = perfect aim, 1.0 = complete chaos)
- In-game reload command
- Automatic update checker (checks GitHub releases on startup)
- Anonymous usage statistics via [bStats](https://bstats.org/plugin/bukkit/StormtrooperX/27782)
- Debug mode for troubleshooting
- Lightweight with minimal performance impact

## Supported Mobs

- Skeleton (1.13+)
- Stray (1.13+)
- Bogged (1.21+ only)

## Installation

1. Download the latest release from the [Releases](../../releases) page
2. Place `StormtrooperX.jar` in your server's `plugins` folder
3. Restart your server or use a plugin manager to load it
4. Configure the plugin in `plugins/StormtrooperX/config.yml`

## Configuration

The plugin generates a `config.yml` file in `plugins/StormtrooperX/`:

```yaml
# Accuracy modifier (0.0 - 1.0)
# Lower values = more accurate shots
# Higher values = less accurate shots
accuracy: 0.7

# Enable/disable specific mob types
skeleton: true
stray: true
bogged: true

# Check for updates on plugin startup
check-for-updates: true

# Enable debug logging
debug: false
```

### Configuration Options

- **accuracy** (0.0 - 1.0): Controls how much random deviation is added to mob arrows. Default: 0.7
- **skeleton**: Enable nerfs for Skeletons. Default: true
- **stray**: Enable nerfs for Strays. Default: true
- **bogged**: Enable nerfs for Bogged. Default: true
- **check-for-updates**: Automatically check for updates on startup from GitHub releases. Default: true
- **debug**: Enable detailed logging for troubleshooting. Default: false

## Commands

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/stormtrooperx` | `/stx`, `/stormtrooper` | Show plugin info | `stormtrooperx.use` |
| `/stormtrooperx reload` | - | Reload configuration | `stormtrooperx.reload` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `stormtrooperx.use` | Access to main command | op |
| `stormtrooperx.reload` | Reload plugin configuration | op |

## Building from Source

### Prerequisites

- Java 8 or higher
- Maven 3.6 or higher

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/StormtrooperX.git
   cd StormtrooperX
   ```

2. Build with Maven:
   ```bash
   mvn clean package
   ```

3. The compiled JAR will be in `target/StormtrooperX-1.0.jar`

### Automated Builds

This repository includes GitHub Actions workflows:

- **Build Workflow** (`.github/workflows/build.yml`): Automatically builds the plugin on every push to master/develop and pull requests. Tests against Java 8, 17, and 21 to ensure compatibility. Artifacts are available in the Actions tab.
- **Release Workflow** (`.github/workflows/release.yml`): Automatically builds and attaches the JAR file to GitHub releases when you create a new release.

## Compatibility

- **Minecraft Version**: 1.13 - 1.21.x (and future versions)
- **Server Software**: Spigot, Paper, Purpur, or any Spigot-based server
- **Java Version**: 8+ (tested on Java 8, 17, and 21)

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

Contributions are welcome! Feel free to:
- Report bugs
- Suggest new features
- Submit pull requests

## Credits

- **Original Plugin**: [Stormtrooper](https://github.com/byteful/Stormtrooper) by [byteful](https://github.com/byteful)
- **Refactor & Enhancements**: GooberCraft

Made with :heart: by GooberCraft
