# SDK installation

Orion `0.0.1` is published to PyPI, npm, and Maven Central. The coordinated
release supports macOS arm64, Linux x86-64 glibc, and Windows x86-64. Every
registry artifact is also attached to the
[`v0.0.1` GitHub release](https://github.com/GtechGovind/orion/releases/tag/v0.0.1)
with SHA-256 checksums.

## Install from public registries

Python 3.10 or newer:

```bash
python -m pip install orion-agent-sdk==0.0.1
```

Node.js 20.17 or newer:

```bash
npm install @orion-runtime/sdk@0.0.1
```

Kotlin/JVM 17:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.gtechgovind:orion-kotlin-sdk:0.0.1")
}
```

The npm root package automatically selects its exact optional native package.
The Maven artifact embeds every supported JNI library, so consumers do not set
`java.library.path`. Maven Central mirrors can take a short time to synchronize
after a new release is accepted.

## Build from a local checkout

The commands below use these example locations:

```bash
ORION_REPO=/absolute/path/to/orion
APPLICATION=/absolute/path/to/application
```

### Python

Python 3.10 or newer can install a locally built abi3 wheel. Maturin packages the
PyO3 extension and `pip` installs the SDK's Pydantic dependency.

```bash
cd "$ORION_REPO/sdks/python"
uvx maturin build --release --out dist/local

cd "$APPLICATION"
python3.10 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install "$ORION_REPO"/sdks/python/dist/local/orion_agent_sdk-0.0.1-*.whl
```

For development against the checkout instead of a wheel, activate the target
project's virtual environment, install Maturin there, and install the SDK with
`develop`:

```bash
source "$APPLICATION/.venv/bin/activate"
python -m pip install maturin
cd "$ORION_REPO/sdks/python"
maturin develop --release
```

### TypeScript and JavaScript

`package:local` creates the public root package and matching optional native
package for testing unpublished changes on the current machine:

```bash
cd "$ORION_REPO/sdks/javascript"
npm ci
npm run package:local

cd "$APPLICATION"
npm install \
  "$ORION_REPO/sdks/javascript/local-packages/orion-runtime-sdk-<platform>-0.0.1.tgz" \
  "$ORION_REPO/sdks/javascript/local-packages/orion-runtime-sdk-0.0.1.tgz"
```

Replace `<platform>` with `darwin-arm64`, `linux-x64-gnu`, or
`win32-x64-msvc`. Generate the pair on the target platform; do not copy a native
tarball to a different operating system or architecture.

### Kotlin/JVM

The Kotlin SDK targets JVM 17. Its Maven artifact embeds the JNI library under
`META-INF/orion/native/<os>/<arch>/`, extracts the matching resource securely,
and loads it without application JVM flags:

```bash
cd "$ORION_REPO/sdks/kotlin"
./gradlew clean test publishToMavenLocal --no-daemon
```

Add Maven Local before Maven Central and use the locally published coordinate
in the consuming Gradle project:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.gtechgovind:orion-kotlin-sdk:0.0.1")
}
```

No `java.library.path` configuration is required. Verify the same consumer path
from the checkout with:

```bash
cd "$ORION_REPO/sdks/kotlin"
./gradlew -p consumer-smoke clean run --no-daemon
```

The locally published artifact contains the current host native library. Public
releases are assembled and tested with the complete supported native matrix.

## Provider configuration and canonical API

The built-in `OpenAI` model reads its credential from the environment:

```bash
export OPENAI_API_KEY="your-key"
```

Applications use the same provider model → typed function → `Agent` workflow in
each language. For example, Python derives the tool and output contracts from
annotations:

```python
import asyncio
from dataclasses import dataclass

from orion_sdk import Agent, OpenAI


@dataclass
class Answer:
    message: str


async def lookup(query: str) -> str:
    """Look up one application-owned value."""
    return query


async def main() -> None:
    agent = Agent(
        model=OpenAI("gpt-5-mini"),
        tools=[lookup],
        output=Answer,
    )

    result = await agent.run("Answer the question using lookup.")
    print(result.output.message)


if __name__ == "__main__":
    asyncio.run(main())
```

See the [complete cross-language examples](../../examples/README.md) and the
[public API contract](../contracts/public-api.md).
