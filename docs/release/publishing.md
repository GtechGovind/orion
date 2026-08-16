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
| Kotlin/JVM | `io.github.gtechgovind:orion-kotlin-sdk` | Maven Central |

These coordinates are not public until their registry pages and ownership are
verified. A name returning “not found” is not proof that the maintainer owns the
name or namespace.

The tag workflow always builds, verifies, and attaches the complete package set
and checksums to its GitHub release. Public registry jobs are disabled by
default. Enable each one only after its setup below is complete by setting the
corresponding repository variable to the exact value `true`:

| Repository variable | Enables |
|---|---|
| `PUBLISH_PYPI` | PyPI trusted publication |
| `PUBLISH_NPM` | npm trusted publication |
| `PUBLISH_MAVEN_CENTRAL` | signed Maven Central publication |

A GitHub release with registry jobs disabled is a usable native artifact
release, but it is not a registry-complete release. Never enable a variable to
probe ownership or credentials during a production tag run.

## One-time registry setup

### PyPI

Create the `orion-agent-sdk` project or a pending trusted publisher for
`GtechGovind/orion`, workflow `release.yml`, and environment `pypi`. The publish
job must receive only `id-token: write`; no long-lived PyPI token belongs in the
repository. PyPI documents this flow in
[Publishing with a Trusted Publisher](https://docs.pypi.org/trusted-publishers/using-a-publisher/).

### npm

Confirm ownership of the `orion-runtime` scope. The protected `npm` environment
provides a granular `NPM_TOKEN` with organization package read/write access and
the **bypass two-factor authentication** option for the first automated
publication. A classic token or granular token without that option will be
rejected even when the account itself does not require two-factor
authentication. After the packages exist, authorize `GtechGovind/orion`,
workflow `release.yml`, environment `npm`, for trusted publishing and retire
the bootstrap token. The controlled release job publishes every napi-rs target
package before the root package. npm trusted publishing currently requires
Node.js 22.14 or newer, npm 11.5.1 or newer, and `id-token: write`; see
[npm trusted publishing](https://docs.npmjs.com/trusted-publishers/) and the
[napi-rs native release model](https://napi.rs/docs/deep-dive/release).

### Maven Central

Verify ownership of the `io.github.gtechgovind` namespace in the Central Publisher
Portal, create a publishing user token, and configure signing material through
a protected `maven-central` GitHub environment. Maven publications require the
main artifact, sources, API documentation, complete POM metadata, and signatures.
Store `MAVEN_SIGNING_KEY` as the ASCII-armored private key. The workflow also
accepts that armor encoded as Base64 or with escaped newline characters, but it
rejects public keys and malformed values without logging the secret.
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

For an interrupted coordinated release, dispatch `release-recovery.yml` from
the protected `main` branch with the original production workflow run ID, tag,
and full commit SHA. Disable an ecosystem input only after confirming that its
registry publication is complete. The recovery workflow intentionally cannot
republish PyPI because Python versions are immutable and PyPI trusted
publication is already independently retryable.

Repository and environment secrets must never appear in Gradle properties,
`.npmrc`, `.pypirc`, workflow logs, package archives, or example configuration.
