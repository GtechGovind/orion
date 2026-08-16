import assert from "node:assert/strict";
import test from "node:test";

import {Agent, OpenAI, OrionError, tool, z} from "../dist/index.js";
import {Model} from "../dist/model/configured.js";
import type {ModelAdapter} from "../dist/model/model-adapter.js";
import {ModelRef} from "../dist/model/model-ref.js";
import type {ModelResponse} from "../dist/model/model-response.js";

const WeatherInput = z.object({city: z.string().min(1)});
const WeatherResult = z.object({city: z.string(), temperatureC: z.number().int()});
const WeatherAnswer = z.object({
  city: z.string(),
  temperatureC: z.number().int(),
  summary: z.string(),
});

const weather = tool({
  name: "weather",
  description: "Get the current weather for a city.",
  input: WeatherInput,
  output: WeatherResult,
  execute: async ({city}) => ({city, temperatureC: 31}),
});

function fakeModel(): {readonly adapter: ModelAdapter; readonly calls: () => number} {

  let calls = 0;
  const adapter: ModelAdapter = {
    provider: "fake",
    profile: () => ({
      streaming: "unsupported",
      toolCalling: "native",
      structuredOutput: "native",
      parallelToolCalls: "unknown",
    }),
    complete: async (): Promise<ModelResponse> => {

      calls += 1;

      return calls === 1
        ? {
            content: "",
            toolCalls: [{id: "c1", name: "weather", arguments: {city: "Delhi"}}],
            finishReason: "tool_calls",
            usage: {inputTokens: 2, outputTokens: 1},
            providerState: {},
          }
        : {
            content: JSON.stringify({
              city: "Delhi",
              temperatureC: 31,
              summary: "Delhi is 31 C.",
            }),
            toolCalls: [],
            finishReason: "stop",
            usage: {inputTokens: 3, outputTokens: 4},
            providerState: {},
          };

    },
  };

  return {adapter, calls: () => calls};

}

test("function tool and structured output are automatic", async () => {

  const fake = fakeModel();
  const agent = new Agent({
    model: new Model(new ModelRef("fake", "test"), fake.adapter),
    tools: [weather],
    output: WeatherAnswer,
    instructions: "Use the weather tool.",
  });

  const result = await agent.run("Weather?");

  assert.equal(result.output.temperatureC, 31);
  assert.equal(result.output.summary, "Delhi is 31 C.");
  assert.equal(result.turns, 2);
  assert.equal(fake.calls(), 2);

});

test("stream finishes with the same typed result", async () => {

  const fake = fakeModel();
  const agent = new Agent({
    model: new Model(new ModelRef("fake", "test"), fake.adapter),
    tools: [weather],
    output: WeatherAnswer,
  });
  const items = [];

  for await (const item of agent.stream("Weather?")) {
    items.push(item);
  }

  const terminal = items.at(-1);
  assert.ok(terminal && "events" in terminal);
  if (!terminal || !("events" in terminal)) {
    throw new Error("stream did not finish with a typed result");
  }
  assert.equal(terminal.output.city, "Delhi");

});

test("kernel failures preserve their stable public error code", async () => {

  const fake = fakeModel();
  const agent = new Agent({
    model: new Model(new ModelRef("fake", "test"), fake.adapter),
    tools: [weather],
    output: WeatherAnswer,
    maxTurns: 1,
  });

  await assert.rejects(
    agent.run("Weather?"),
    (error: unknown) => error instanceof OrionError
      && error.code === "turn_limit_exceeded"
      && !error.retryable,
  );

});

test("OpenAI request timeout rejects with the timeout category", async () => {

  const originalFetch = globalThis.fetch;
  const hangingFetch: typeof fetch = async (_input, init) => new Promise<Response>(
    (_resolve, reject) => {

      init?.signal?.addEventListener("abort", () => {
        reject(new Error("request aborted"));
      }, {once: true});

    },
  );
  globalThis.fetch = hangingFetch;

  try {
    const agent = new Agent({
      model: new OpenAI("test", {apiKey: "test", timeoutMs: 10}),
      output: WeatherAnswer,
    });

    await assert.rejects(
      agent.run("Weather?"),
      (error: unknown) => error instanceof OrionError && error.code === "timeout",
    );
  } finally {
    globalThis.fetch = originalFetch;
  }

});
