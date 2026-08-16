import type {Json, JsonObject, JsonSchema} from "../model/json.js";
import {zodCodec} from "../model/codec.js";
import type {ZodType} from "zod";

/** Host function exposed to a model during an Orion run. */
export interface ToolDefinition {

  /** Stable name referenced by model tool calls. */
  readonly name: string;

  /** Human-readable model guidance. */
  readonly description: string;

  /** JSON Schema used to validate model-supplied arguments. */
  readonly inputSchema: JsonSchema;

  /** Executes the tool after validating its domain fields and optional cancellation. */
  execute(arguments_: JsonObject, signal?: AbortSignal): Json | Promise<Json>;

}

/** Construction options for a tool backed by typed runtime codecs. */
export interface ToolOptions<Arguments, Result> {

  /** Stable name referenced by model tool calls. */
  readonly name: string;

  /** Human-readable model guidance. */
  readonly description: string;

  /** Runtime argument schema used for type inference and Rust validation. */
  readonly input: ZodType<Arguments>;

  /** Runtime result schema used to validate application tool output. */
  readonly output: ZodType<Result>;

  /** Typed application handler with optional cancellation. */
  execute(arguments_: Arguments, signal?: AbortSignal): Result | Promise<Result>;

}

/** Creates the single supported typed tool declaration. */
export function tool<Arguments, Result>(
  options: ToolOptions<Arguments, Result>,
): ToolDefinition {

  const input = zodCodec(options.input);
  const output = zodCodec(options.output);
  if (input.schema["type"] !== "object") {
    throw new TypeError("typed tool arguments must define an object JSON Schema");
  }

  return {
    name: options.name,
    description: options.description,
    inputSchema: input.schema,
    execute: async (rawArguments, signal) => {

      const arguments_ = input.decode(rawArguments);
      const result = await options.execute(arguments_, signal);

      return output.encode(result);

    },
  };

}
