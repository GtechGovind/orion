import { Agent, ModelRegistry, OpenAICompatibleAdapter, Runner } from "../sdks/javascript/src/index.ts";

const agent = new Agent({
  id: "weather", name: "Weather", instructions: "Use the weather tool and answer briefly.",
  model: "openai:gpt-5-mini", tools: [{
    name: "weather", description: "Get temperature",
    inputSchema: { type: "object", properties: { city: { type: "string" } }, required: ["city"] },
    execute: (arguments_: any) => ({ city: arguments_.city, temperature_c: 31 }),
  }],
});
const result = await new Runner(new ModelRegistry([new OpenAICompatibleAdapter()])).run(agent, "Weather in Delhi?");
console.log(result.output);
