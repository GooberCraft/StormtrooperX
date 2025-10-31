# Contributing to StormtrooperX

Thank you for your interest in contributing to StormtrooperX! This document provides guidelines and instructions for contributing to the project.

## Code of Conduct

This project adheres to a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

## How Can I Contribute?

### Reporting Bugs

Before submitting a bug report:
- Check the [existing issues](https://github.com/GooberCraft/StormtrooperX/issues) to avoid duplicates
- Ensure you're using the latest version of the plugin
- Test with only StormtrooperX installed to rule out conflicts

When submitting a bug report, use the [bug report template](https://github.com/GooberCraft/StormtrooperX/issues/new?template=bug_report.yml) and include:
- Minecraft version
- Server software (Spigot/Paper/Purpur) and version
- Java version
- Plugin version
- Detailed steps to reproduce
- Expected vs. actual behavior
- Server logs (if applicable)
- Configuration file contents

### Suggesting Features

Feature requests are welcome! Use the [feature request template](https://github.com/GooberCraft/StormtrooperX/issues/new?template=feature_request.yml) and:
- Clearly describe the feature and its use case
- Explain why it would be useful to most users
- Consider how it fits with the plugin's scope (mob accuracy nerfs)

### Asking Questions

Have a question? Use the [question template](https://github.com/GooberCraft/StormtrooperX/issues/new?template=question.yml) or check existing issues first.

## Development Setup

### Prerequisites

- **Java**: JDK 11 or higher (we test on Java 11, 17, and 21)
- **Maven**: 3.6 or higher
- **Git**: For version control
- **IDE**: IntelliJ IDEA or Eclipse recommended

### Setting Up Your Development Environment

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR-USERNAME/StormtrooperX.git
   cd StormtrooperX
   ```

3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/GooberCraft/StormtrooperX.git
   ```

4. Build the project:
   ```bash
   mvn clean package
   ```

5. The compiled JAR will be in `target/StormtrooperX-<version>.jar`

### Running Tests

Run all tests:
```bash
mvn clean test
```

Run tests with coverage:
```bash
mvn clean test jacoco:report
```

Coverage report will be in `target/site/jacoco/index.html`

### Testing Your Changes

1. Build the plugin: `mvn clean package`
2. Copy the JAR from `target/` to your test server's `plugins/` folder
3. Start your test server (Minecraft 1.13+ recommended)
4. Test your changes thoroughly
5. Check the console for errors
6. Use `/stormtrooperx` to verify configuration

## Code Style Guidelines

### Java Code Style

- **Indentation**: 4 spaces (no tabs)
- **Line Length**: Maximum 120 characters
- **Braces**: Required for all control structures (if, for, while)
- **Naming Conventions**:
  - Classes: `PascalCase`
  - Methods/Variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
- **Comments**: Use Javadoc for public methods and classes

Example:
```java
/**
 * Checks if a player has opted out of mob accuracy nerfs.
 *
 * @param uuid The player's UUID
 * @return true if the player has opted out, false otherwise
 */
public boolean isOptedOut(UUID uuid) {
    // Implementation
}
```

### Commit Message Guidelines

Follow conventional commit format:

```
<type>: <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `chore`: Maintenance tasks

**Examples:**
```
feat: Add support for Pillager mob type

Adds configuration option and event handling for Pillager ranged attacks.
Includes version detection for 1.14+ servers.

Closes #42
```

```
fix: Prevent NPE when target is not a player

Added null check in bow shoot event handler to handle cases where
mob targets are other entities instead of players.

Fixes #38
```

## Pull Request Process

### Before Submitting

1. Ensure your code builds: `mvn clean package`
2. Run all tests: `mvn clean test`
3. Test manually on a Minecraft server
4. Update documentation if needed (README, config examples)
5. Add/update tests for your changes
6. Keep commits focused and atomic

### Submitting a Pull Request

1. Create a feature branch:
   ```bash
   git checkout -b feat/your-feature-name
   ```
   or
   ```bash
   git checkout -b fix/bug-description
   ```

2. Make your changes and commit:
   ```bash
   git add .
   git commit -m "feat: Add new feature"
   ```

3. Push to your fork:
   ```bash
   git push origin feat/your-feature-name
   ```

4. Open a pull request on GitHub

5. In your PR description:
   - Reference related issues (e.g., "Closes #42")
   - Describe what changed and why
   - Include testing steps
   - Note any breaking changes

### PR Review Process

- Maintainers will review your PR
- CI/CD checks must pass (build on Java 11, 17, 21)
- Address any feedback or requested changes
- Once approved, a maintainer will merge your PR

## Project Structure

```
StormtrooperX/
├── .github/
│   ├── workflows/          # GitHub Actions CI/CD
│   └── ISSUE_TEMPLATE/     # Issue templates
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/goobercraft/stormtrooperx/
│   │   │       ├── StormtrooperX.java      # Main plugin class
│   │   │       ├── DatabaseManager.java    # H2 database operations
│   │   │       └── UpdateChecker.java      # GitHub release checker
│   │   └── resources/
│   │       ├── config.yml                  # Default configuration
│   │       └── plugin.yml                  # Plugin metadata
│   └── test/
│       └── java/                           # JUnit tests
├── pom.xml                 # Maven configuration
├── README.md
├── CHANGELOG.md
├── LICENSE
├── CODE_OF_CONDUCT.md
├── CONTRIBUTING.md
└── SECURITY.md
```

## Compatibility Requirements

### Java Version
- **Minimum**: Java 11
- **Recommended**: Java 17+
- Test on multiple versions when possible

### Minecraft Version
- **Minimum**: 1.13
- **Current**: 1.21.x
- Handle version-specific features gracefully (see Bogged mob handling)

### Server Software
- Must work on Spigot, Paper, and Purpur
- Use Bukkit API when possible for maximum compatibility

## Testing Guidelines

### Unit Tests
- Write tests for new features
- Maintain or improve code coverage
- Use JUnit 5
- Mock Bukkit objects when needed

Example test structure:
```java
@Test
void testPlayerOptOut() {
    DatabaseManager db = new DatabaseManager(logger, dataFolder);
    UUID playerId = UUID.randomUUID();

    // Initially not opted out
    assertFalse(db.isOptedOut(playerId));

    // Toggle to opted out
    assertTrue(db.toggleOptOut(playerId));
    assertTrue(db.isOptedOut(playerId));

    // Toggle back
    assertFalse(db.toggleOptOut(playerId));
    assertFalse(db.isOptedOut(playerId));
}
```

### Integration Tests
- Test on an actual Minecraft server
- Verify configuration loading
- Test commands in-game
- Verify mob behavior changes

## Version Compatibility

When adding features that depend on specific Minecraft versions:

```java
// Example: Handle version-specific mob types
if (getConfig().getBoolean("pillager", true)) {
    try {
        EntityType pillagerType = EntityType.valueOf("PILLAGER");
        entities.add(pillagerType);
        logger.info("Entity 'Pillager' will be nerfed!");
    } catch (IllegalArgumentException e) {
        logger.info("Entity 'Pillager' is not available in this version");
    }
}
```

## Dependencies

### Adding New Dependencies
- Keep dependencies minimal
- Ensure compatibility with Java 11
- Use Maven shade plugin for any non-Bukkit dependencies
- Relocate packages to avoid conflicts

Example in `pom.xml`:
```xml
<relocation>
    <pattern>org.h2</pattern>
    <shadedPattern>com.goobercraft.stormtrooperx.libs.h2</shadedPattern>
</relocation>
```

## Documentation

### Updating Documentation
When making changes, update:
- `README.md` - For user-facing features
- `CHANGELOG.md` - Following Keep a Changelog format
- Javadoc comments - For API changes
- `config.yml` - For new configuration options
- Example configurations - For complex features

## Release Process

Releases are handled by maintainers:
1. Version bump in `pom.xml`
2. Update `CHANGELOG.md`
3. Create GitHub release
4. GitHub Actions builds and attaches JAR

## Questions?

- Check existing [issues](https://github.com/GooberCraft/StormtrooperX/issues) and [discussions](https://github.com/GooberCraft/StormtrooperX/discussions)
- Ask in a [new issue](https://github.com/GooberCraft/StormtrooperX/issues/new?template=question.yml)

## License

By contributing to StormtrooperX, you agree that your contributions will be licensed under the [MIT License](LICENSE).

---

Thank you for contributing to StormtrooperX! 🎯
