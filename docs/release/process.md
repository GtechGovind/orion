# Release process

Orion `0.x` releases are coordinated native SDK releases. Publishing remains
blocked until the registry ownership and protected environments described in
the [publishing guide](publishing.md) are configured.

Each release must:

1. pass formatting, linting, unit, conformance, packaging, and security checks;
2. verify the changelog, coordinated versions, bindings, schemas, and lockfiles;
3. build native artifacts in isolated jobs for every supported target;
4. install each artifact into a clean external consumer and execute a native
   smoke test;
5. sign artifacts and generate checksums and a software bill of materials;
6. publish through trusted or protected registry identities;
7. create a signed source tag and GitHub release; and
8. verify clean installations from PyPI, npm, and Maven Central.

If a registry rejects one publication after the artifacts have passed every
release gate, use the guarded `release-recovery.yml` workflow. It verifies the
original tag, commit, workflow run, and SHA-scoped artifacts before continuing.
It publishes missing npm packages in dependency order, reuses the original
native libraries for Maven Central, and creates or repairs the checksummed
GitHub release. Never move the tag or rebuild an already published version.

Until those one-time registry prerequisites exist, maintainers may build and
consume local artifacts using the [installation guide](../guides/installation.md),
but must not describe them as public releases.
