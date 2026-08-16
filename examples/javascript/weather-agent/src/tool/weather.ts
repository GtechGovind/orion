import {tool} from "../../../../../sdks/javascript/dist/index.js";

import {WeatherArguments, WeatherResult, type WeatherResultValue,} from "../model/weather.js";

/** Typed application tool exposed directly to the weather agent. */
export const getWeather = tool({
  name: "weather",
  description: "Get the current temperature for a city.",
  input: WeatherArguments,
  output: WeatherResult,
  execute: async (arguments_, signal): Promise<WeatherResultValue> => {
    signal?.throwIfAborted();
    return {city: arguments_.city.trim(), temperature_c: 31};
  },
});
