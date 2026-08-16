# Security Policy

## Supported versions

Orion has no supported stable release yet. The executable `0.1` pilot receives
security fixes on the `main` branch, but its APIs and native package formats may
change before the first published release.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Report it privately
through the repository's
[GitHub security advisory form](https://github.com/GtechGovind/orion/security/advisories/new).
Include affected versions or commits, reproduction steps, impact, and any known
mitigation without attaching live credentials or sensitive model data.

## Security principles

- Model output and tool arguments are untrusted input.
- Durable data is explicit, versioned, and safe to deserialize by default.
- Secrets are excluded from events and telemetry by default.
- External side effects require identity and replay policy.
- Code execution is never part of the default core installation.
- Host callbacks and native handles never cross the FFI boundary implicitly.
