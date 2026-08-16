import {Agent, OpenAI} from "../../../../sdks/javascript/dist/index.js";

import {WeatherAnswer, type WeatherAnswerValue} from "./model/weather.js";
import {getWeather} from "./tool/weather.js";

/** Builds the immutable agent and its typed structured-output contract. */
export function createWeatherAgent(): Agent<WeatherAnswerValue> {

  return new Agent({
    id: "weather",
    name: "Weather assistant",
    instructions: [
      "Use the weather tool.",
      "Return only the requested structured JSON with city, temperature_c, and a concise summary.",
    ].join(" "),
    model: new OpenAI("gpt-5-mini"),
    tools: [getWeather],
    output: WeatherAnswer,
    maxTurns: 4,
  });

}
