# Security Policy

## Supported Versions

We actively provide security updates for the following versions:

| Version | Supported          | Minecraft Version | Notes |
| ------- | ------------------ | ----------------- | ----- |
| 1.2.x   | :white_check_mark: | 1.13 - 1.21.x     | Current stable release |
| 1.1.x   | :white_check_mark: | 1.13 - 1.21.x     | Security fixes only |
| 1.0.x   | :x:                | 1.13 - 1.21.x     | No longer supported |
| < 1.0   | :x:                | N/A               | No longer supported |

**Note**: While the plugin is compatible with Minecraft 1.13+, we recommend using the latest stable Minecraft version and keeping the plugin updated.

## Security Considerations

### Data Storage
StormtrooperX stores player opt-out preferences in a local H2 database:
- Database location: `plugins/StormtrooperX/playerdata.mv.db`
- Data stored: Player UUIDs and opt-out status (boolean)
- No personally identifiable information (PII) is stored beyond UUIDs

### External Communications
The plugin makes outbound HTTPS requests to:
- **GitHub API** (`api.github.com`) - For update checking
  - Can be disabled via `check-for-updates: false` in config.yml
- **bStats** (`bstats.org`) - For anonymous usage statistics
  - Can be disabled in `plugins/bStats/config.yml`

### Permissions
The plugin defines the following permissions:
- `stormtrooperx.use` (default: op) - View plugin information
- `stormtrooperx.reload` (default: op) - Reload configuration
- `stormtrooperx.optout` (default: true) - Toggle personal opt-out status

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue, please follow these steps:

### 1. **DO NOT** Open a Public Issue
Please do not disclose security vulnerabilities publicly until they have been addressed.

### 2. Report Privately

**Preferred Method**: Use GitHub's private vulnerability reporting feature:
1. Go to the [Security tab](https://github.com/GooberCraft/StormtrooperX/security)
2. Click "Report a vulnerability"
3. Fill out the advisory form

**Alternative Method**: If private reporting is unavailable, email the maintainers directly through their GitHub profiles.

### 3. Include Detailed Information

When reporting, please include:
- **Description**: Clear description of the vulnerability
- **Impact**: What could an attacker accomplish?
- **Affected Versions**: Which versions are vulnerable?
- **Steps to Reproduce**: Detailed steps to reproduce the issue
- **Proof of Concept**: Code or configuration demonstrating the issue (if applicable)
- **Suggested Fix**: If you have ideas for remediation
- **Environment**: Server software, Minecraft version, Java version

### Example Report Format

```
## Vulnerability Summary
[Brief description of the vulnerability]

## Impact
[What could an attacker do with this vulnerability?]

## Affected Versions
- StormtrooperX: [version range]
- Minecraft: [version range if relevant]

## Steps to Reproduce
1. [Step 1]
2. [Step 2]
3. [Step 3]

## Proof of Concept
[Code, configuration, or commands demonstrating the issue]

## Suggested Remediation
[Your suggestions for fixing the issue]

## Environment
- Server Software: [Spigot/Paper/Purpur version]
- Minecraft Version: [version]
- Java Version: [version]
- StormtrooperX Version: [version]
```

## Response Timeline

We aim to respond to security reports according to the following timeline:

| Stage | Timeline |
|-------|----------|
| **Initial Response** | Within 48 hours |
| **Vulnerability Confirmation** | Within 7 days |
| **Fix Development** | Depends on severity |
| **Security Release** | As soon as possible after fix |
| **Public Disclosure** | After fix is released and users have time to update |

### Severity Levels

- **Critical**: Remote code execution, privilege escalation, data breach
  - Fix target: Within 7 days
- **High**: Authentication bypass, significant data leak
  - Fix target: Within 14 days
- **Medium**: Denial of service, information disclosure
  - Fix target: Within 30 days
- **Low**: Minor issues with limited impact
  - Fix target: Next regular release

## Security Update Process

When a security vulnerability is confirmed:

1. **Private Fix**: Develop and test the fix in a private branch
2. **Security Advisory**: Create a GitHub Security Advisory
3. **Release**: Publish a patch release with the fix
4. **Notification**: Update the Security Advisory with details
5. **CHANGELOG**: Document the fix in `CHANGELOG.md`
6. **Disclosure**: Publicly disclose details after users have had time to update (typically 2 weeks)

## Security Best Practices for Server Owners

### Plugin Configuration
- Review configuration options in `config.yml`
- Consider disabling update checking if you manage updates manually
- Use permission plugins to restrict `stormtrooperx.reload` to trusted administrators

### Server Hardening
- Keep your server software (Spigot/Paper) updated
- Use the latest Java LTS version (Java 17 or 21 recommended)
- Regularly update all plugins, including StormtrooperX
- Review server logs for unusual activity
- Use proper file permissions for the `plugins/` directory

### Database Security
- The H2 database file is stored locally and not exposed externally
- Ensure proper file system permissions on `plugins/StormtrooperX/playerdata.mv.db`
- Include the database file in your backup strategy

### Network Security
- StormtrooperX does not open any ports
- All outbound connections use HTTPS
- Consider firewall rules if you want to restrict outbound connections

## Scope

### In Scope
- Authentication/authorization bypasses
- Remote code execution vulnerabilities
- SQL injection or database vulnerabilities
- Privilege escalation
- Information disclosure of sensitive data
- Denial of service vulnerabilities
- Configuration issues leading to security problems

### Out of Scope
- Issues in Minecraft, Spigot, Paper, or Java itself
- Issues in third-party dependencies (report to respective projects)
- Gameplay exploits that don't involve security vulnerabilities
- Performance issues without security implications
- Social engineering attacks
- Issues requiring physical access to the server

## Security Hall of Fame

We appreciate security researchers who help keep StormtrooperX safe. With your permission, we'll list contributors who responsibly disclose vulnerabilities:

*No security vulnerabilities have been reported yet.*

## Additional Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [GitHub Security Best Practices](https://docs.github.com/en/code-security)
- [Spigot Plugin Security Best Practices](https://www.spigotmc.org/wiki/plugin-security/)

## Questions?

If you have questions about this security policy or the security of StormtrooperX:
- Review our [documentation](README.md)
- Check existing [security advisories](https://github.com/GooberCraft/StormtrooperX/security/advisories)
- Ask in a [new issue](https://github.com/GooberCraft/StormtrooperX/issues/new?template=question.yml) (for non-security questions)

---

Thank you for helping keep StormtrooperX and its users safe! 🔒
