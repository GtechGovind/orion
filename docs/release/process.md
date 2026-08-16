# Release process

Publishing is disabled during M0.

The future release process should:

1. Pass formatting, linting, unit, conformance, packaging, and security checks.
2. Verify changelog, versions, generated bindings, schemas, and lockfiles.
3. Build native artifacts in isolated jobs for every supported target.
4. Sign artifacts and generate checksums and a software bill of materials.
5. Publish through trusted publishing with protected environments.
6. Create a signed source tag and GitHub release.
7. Run post-publish installation smoke tests from public registries.

The included release workflow performs preparation checks only and cannot
publish packages.
