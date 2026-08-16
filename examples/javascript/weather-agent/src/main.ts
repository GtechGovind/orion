import {createWeatherAgent} from "./agent.js";

const controller = new AbortController();

try {
  for await (const item of createWeatherAgent().stream(
    "What is the weather in Delhi?",
    {signal: controller.signal},
  )) {
    if ("events" in item) {
      console.log(`answer: ${item.output.summary}`);
      console.log(`turns: ${item.turns}; output tokens: ${item.usage.outputTokens}`);
    } else {
      console.log(`event ${item.sequence}: ${item.type}`);
    }
  }
} finally {
  controller.abort();
}
