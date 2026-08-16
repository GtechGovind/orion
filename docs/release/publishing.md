# SDK registry publishing

Orion coordinates one version across the Python, JavaScript/TypeScript, and
Kotlin SDKs. A release is complete only when every supported native artifact is
available, installable in a clean project, and points to the same reviewed Git
tag. Never publish an SDK directly from an unmerged pull-request branch.

## Intended public coordinates

| Ecosystem | Coordinate | Registry |
|---|---|---|
| Python | `orion-agent-sdk` | PyPI |
| JavaScript/TypeScript | `@orion-runtime/sdk` plus target packages | npm |
| Kotlin/JVM | `dev.orion.runtime:orion-kotlin-sdk` | Maven Central |

These coordinates are not public until their registry pages and ownership are
verified. A name returning “not found” is not proof that the maintainer owns the
name or namespace.

## One-time registry setup

### PyPI

Create the `orion-agent-sdk` project or a pending trusted publisher for
`GtechGovind/orion`, workflow `release.yml`, and environment `pypi`. The publish
job must receive only `id-token: write`; no long-lived PyPI token belongs in the
repository. PyPI documents this flow in
[Publishing with a Trusted Publisher](https://docs.pypi.org/trusted-publishers/using-a-publisher/).

### npm

Confirm ownership of the `orion-runtime` scope and bootstrap the root package
before configuring trusted publishing. Authorize `GtechGovind/orion`, workflow
`release.yml`, environment `npm`, for `npm publish`. The controlled release job
must publish every napi-rs target package before the root package. npm trusted
publishing currently requires Node.js 22.14 or newer, npm 11.5.1 or newer, and
`id-token: write`; see [npm trusted publishing](https://docs.npmjs.com/trusted-publishers/)
and the [napi-rs native release model](https://napi.rs/docs/deep-dive/release).

### Maven Central

Verify ownership of the `dev.orion.runtime` namespace in the Central Publisher
Portal, create a publishing user token, and configure signing material through
a protected `maven-central` GitHub environment. Maven publications require the
main artifact, sources, API documentation, complete POM metadata, and signatures.
The authoritative onboarding and deployment process is the
[Central Publisher Portal guide](https://central.sonatype.org/publish/publish-portal-guide/).

## Release gates

Before creating a release tag:

1. Merge the reviewed change to `main` and confirm blocking CI is green.
2. Synchronize the version in `pyproject.toml`, `package.json`, Gradle, Cargo,
   the changelog, and generated package metadata.
3. Build and smoke-test every claimed OS/architecture artifact on its matching
   runner. A target listed in metadata without a runtime test is unsupported.
4. Inspect Python wheels, npm root/target tarballs, and the Kotlin JARs for
   secrets, local paths, debug binaries, and missing native resources.
5. Install each package into an empty consumer project and execute a native
   deterministic run before registry upload.
6. Publish from the protected `release.yml` workflow using immutable artifacts.
7. Query all three registries and repeat clean-project installation tests from
   the public coordinates.
8. Create release notes containing checksums, supported targets, compatibility
   status, known limitations, and links to the exact workflow run.

## Failure handling

Registry versions are immutable and a multi-platform release is not
transactional. If publication stops partway through, inventory every registry
artifact before retrying. Reuse the exact original build outputs for the same
version; never rebuild different bytes under a partially published version.
Publish missing target packages first, publish the root package last, and
deprecate a broken npm root version if its complete native target set cannot be
restored.

Repository and environment secrets must never appear in Gradle properties,
`.npmrc`, `.pypirc`, workflow logs, package archives, or example configuration.
