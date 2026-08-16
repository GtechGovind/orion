# Security Policy

## Supported versions

Orion has no supported release yet. This repository is a non-functional
architecture scaffold.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Use the repository's
private security-advisory channel once hosting is configured. Until then,
security contact details remain intentionally unset rather than directing
reports to an unverified address.

## Security principles

- Model output and tool arguments are untrusted input.
- Durable data is explicit, versioned, and safe to deserialize by default.
- Secrets are excluded from events and telemetry by default.
- External side effects require identity and replay policy.
- Code execution is never part of the default core installation.
- Host callbacks and native handles never cross the FFI boundary implicitly.
