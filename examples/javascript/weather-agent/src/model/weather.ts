import {z} from "../../../../../sdks/javascript/dist/index.js";

/** Runtime schema for model-produced weather tool arguments. */
export const WeatherArguments = z.object({city: z.string().min(1)});

/** Runtime schema for the application tool result. */
export const WeatherResult = z.object({
  city: z.string(),
  temperature_c: z.number().int(),
});

/** Runtime schema for the model's terminal structured output. */
export const WeatherAnswer = z.object({
  city: z.string(),
  temperature_c: z.number().int(),
  summary: z.string(),
});

/** Typed terminal answer inferred from the runtime schema. */
export type WeatherAnswerValue = z.infer<typeof WeatherAnswer>;

/** Typed result inferred from the runtime schema. */
export type WeatherResultValue = z.infer<typeof WeatherResult>;
