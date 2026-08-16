import assert from "node:assert/strict";
import test from "node:test";
import { Agent, ModelRegistry, Runner, type ModelAdapter } from "../dist/index.js";

test("tool loop executes through the Rust kernel", async () => {
  let calls = 0;
  const model: ModelAdapter = { provider: "fake",
    profile: () => ({ streaming: "unsupported", toolCalling: "native", structuredOutput: "unknown", parallelToolCalls: "unknown" }),
    complete: async () => ++calls === 1
    ? { toolCalls: [{ id: "c1", name: "weather", arguments: { city: "Delhi" } }], finishReason: "tool_calls" }
    : { content: "Delhi is 31 C" } };
  const agent = new Agent({ id: "weather", name: "Weather", instructions: "Be concise", model: "fake:test",
    tools: [{ name: "weather", description: "Get weather", inputSchema: { type: "object" }, execute: () => ({ temperature: 31 }) }] });
  const result = await new Runner(new ModelRegistry([model])).run(agent, "Weather?");
  assert.equal(result.output, "Delhi is 31 C");
  assert.equal(result.turns, 2);
  assert.equal(result.events.at(-1)?.type, "run_completed");
});
