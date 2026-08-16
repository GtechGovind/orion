# Governance

Orion currently uses a maintainer-led, consensus-seeking model.

## Roles

- **Contributors** participate through issues, discussions, documentation,
  implementation, testing, and review.
- **Reviewers** have demonstrated sustained knowledge in a subsystem and may
  approve changes within that subsystem.
- **Maintainers** manage releases, security response, repository policy, and
  cross-subsystem architectural decisions.

The initial maintainer list will be recorded in `CODEOWNERS` when repository
ownership is established.

## Decisions

Routine changes are decided through pull-request review. Changes affecting the
public SDK contract, execution semantics, durable formats, FFI, security model,
or compatibility policy require an ADR.

Maintainers seek consensus. When consensus cannot be reached, the decision,
alternatives, and dissenting concerns must be recorded in the ADR.

## Releases

Until the project reaches an accepted compatibility milestone, all releases are
experimental and use `0.x` versions. Publishing rights are limited to
maintainers and automated trusted-publishing workflows.

## Changes to governance

Material governance changes require a public pull request and a minimum
seven-day review period once the project has external contributors.
