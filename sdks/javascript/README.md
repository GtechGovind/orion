# Orion JavaScript/TypeScript SDK

Strict Node.js SDK backed by an in-process Node-API module. TypeScript types are
erased at runtime, so the single supported tool declaration uses Zod for input
and output validation.

```bash
npm install @orion-runtime/sdk
```

```ts
import {Agent, OpenAI, tool, z} from "@orion-runtime/sdk";

const WeatherInput = z.object({city: z.string().min(1)});
const WeatherResult = z.object({city: z.string(), temperatureC: z.number()});
const WeatherAnswer = z.object({
  city: z.string(),
  temperatureC: z.number(),
  summary: z.string(),
});

const getWeather = tool({
  name: "weather",
  description: "Get the current weather for a city.",
  input: WeatherInput,
  output: WeatherResult,
  execute: async ({city}) => ({city, temperatureC: 31}),
});

const agent = new Agent({
  model: new OpenAI("gpt-5-mini"),
  tools: [getWeather],
  output: WeatherAnswer,
  instructions: "Use the weather tool.",
});

const result = await agent.run("What is the weather in Delhi?");
console.log(result.output.summary);
```

Use `agent.stream(input, {signal})` for cancellation-aware lifecycle streaming.
`OpenAI` accepts `apiKey`, `baseUrl`, and a positive `timeoutMs`; provider and
runtime failures reject with an `OrionError` carrying a stable `code`.
Schema codecs, raw schemas, registries, runners, adapters, model references,
protocol DTOs, and native sessions are internal and are not alternate APIs.

See the [complete weather application](../../examples/javascript/weather-agent/src/main.ts).

```bash
cd sdks/javascript
npm ci
npm run check
npm test
npm run check:package
```

For a local application, emit and verify the current-platform packages into the
ignored `local-packages/` directory:

```bash
npm run package:local
npm install ./local-packages/orion-runtime-sdk-<platform>-0.0.1.tgz \
  ./local-packages/orion-runtime-sdk-0.0.1.tgz
```

Replace `<platform>` with `darwin-arm64`, `linux-x64-gnu`, or
`win32-x64-msvc`. Install both tarballs together: the first contains the native
library and the second contains the public JavaScript and TypeScript API.

## Native packages

The root package contains JavaScript and declarations only. npm selects one
exact-version optional native package for the current system:

- `@orion-runtime/sdk-darwin-arm64`
- `@orion-runtime/sdk-linux-x64-gnu`
- `@orion-runtime/sdk-win32-x64-msvc`

These are the platforms built and runtime-tested by the current CI runners.
Adding a target requires both a build job and a same-platform package smoke test.
Consumers do not need Rust, a compiler, or an install-time download script.

## Release preparation

Release jobs collect one verified `.node` artifact per supported target and then
run:

```bash
npm run release:prepare
```

This explicit release-only command creates napi-rs platform directories,
collects artifacts, adds exact optional dependencies to the root metadata, and
verifies the complete set. It passes `--skip-optional-publish` and
`--no-gh-release`; it performs no npm publication or GitHub release write.

`npm run release:preview` and `npm run check:package` are non-mutating dry runs.
There is deliberately no `prepublishOnly`, `prepack`, `prepare`, or other npm
lifecycle hook capable of publishing platform packages during ordinary packing.
Registry publication belongs only in an authenticated, provenance-enabled
release job after every platform tarball passes its external install test.
