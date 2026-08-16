# Orion documentation

This directory is organized by the kind of decision or task a reader is trying
to understand. Use this index instead of adding README files to placeholder
subdirectories.

## Architecture

- [Overview](architecture/overview.md) — layers, runtime states, and invariants
- [Runtime boundary](architecture/runtime-boundary.md) — Rust and host-language ownership
- [Protocol outline](architecture/protocol.md) — commands, effects, results, and events
- [Durability model](architecture/durability.md) — checkpoints, receipts, and replay safety

## Contracts

- [Public API contract](contracts/public-api.md) — shared concepts and behavior
- [Host SDK contract](contracts/host-sdk.md) — common semantics expressed idiomatically per language

## Guides

- [Pilot guide](guides/pilot.md) — build, test, and supported boundary
- [LLM connectivity](guides/llm-connectivity.md) — adapters, credentials, and endpoints

## Decisions

- [ADR guide](decisions/README.md) — statuses and decision workflow
- [ADR template](decisions/0000-template.md) — required evidence and validation structure
- [ADR-0001](decisions/0001-rust-semantic-kernel.md) — proposed Rust semantic kernel

## Development

- [Repository layout and file guide](development/repository-layout.md) — ownership and use case of every maintained path
- [Contributing](../.github/CONTRIBUTING.md) — local checks, design rules, and pull requests

## Releases and compatibility

- [Release process](release/process.md) — future gated publishing workflow
- [Versioning](release/versioning.md) — compatibility areas that still require decisions

## Project direction and policy

- [Roadmap](planning/roadmap.md)
- [Governance](policy/governance.md)
- [Code of Conduct](../.github/CODE_OF_CONDUCT.md)
- [Security policy](../.github/SECURITY.md)
- [Support](../.github/SUPPORT.md)
