import type {ZodType} from "zod";

import {zodCodec} from "../model/codec.js";
import type {Model} from "../model/configured.js";
import {ModelRegistry} from "../model/registry.js";
import {AgentDefinition} from "./agent.js";
import {OrionError} from "./orion-error.js";
import type {RunEvent} from "./run-event.js";
import type {RunResult} from "./run-result.js";
import {Runner, type RunOptions} from "./runner.js";
import type {ToolDefinition} from "./tool.js";

/** Construction options for the single supported application agent API. */
export interface AgentOptions<Output> {

  /** Configured provider model used by every run. */
  readonly model: Model;

  /** Typed application tools available to the model. */
  readonly tools?: readonly ToolDefinition[];

  /** Runtime schema that determines the typed terminal output. */
  readonly output: ZodType<Output>;

  /** System-level behavior instructions. */
  readonly instructions?: string;

  /** Optional application identity used in lifecycle metadata. */
  readonly id?: string;

  /** Optional human-readable agent name. */
  readonly name?: string;

  /** Positive model-turn limit. */
  readonly maxTurns?: number;

}

/** Successful typed output with usage and lifecycle metadata. */
export interface AgentResult<Output> {

  /** Structured output validated by Rust and decoded through the runtime schema. */
  readonly output: Output;

  /** Stable identifier for this run. */
  readonly runId: string;

  /** Provider-reported token usage. */
  readonly usage: RunResult["usage"];

  /** Number of completed model turns. */
  readonly turns: number;

  /** Complete ordered event trace. */
  readonly events: readonly RunEvent[];

}

/** Low-ceremony typed agent backed by the Rust runtime. */
export class Agent<Output> {

  readonly #codec;

  readonly #definition: AgentDefinition;

  readonly #runner: Runner;

  /** Creates an agent while keeping registries, runners, and codecs internal. */
  constructor(options: AgentOptions<Output>) {

    this.#codec = zodCodec(options.output);
    this.#definition = new AgentDefinition({
      id: options.id ?? "assistant",
      name: options.name ?? "Assistant",
      instructions: options.instructions ?? "You are a helpful assistant.",
      model: options.model.ref,
      outputSchema: this.#codec.schema,
      ...(options.tools ? {tools: options.tools} : {}),
      ...(options.maxTurns === undefined ? {} : {maxTurns: options.maxTurns}),
    });
    this.#runner = new Runner(new ModelRegistry([options.model.adapter]));

  }

  /** Runs to completion and returns the decoded application output. */
  async run(input: string, options: RunOptions = {}): Promise<AgentResult<Output>> {

    const result = await this.#runner.run(this.#definition, input, options);

    return this.#convertResult(result);

  }

  /** Yields lifecycle events followed by one typed terminal result. */
  async *stream(
    input: string,
    options: RunOptions = {},
  ): AsyncGenerator<RunEvent | AgentResult<Output>> {

    for await (const item of this.#runner.runStream(this.#definition, input, options)) {
      yield isRunResult(item) ? this.#convertResult(item) : item;
    }

  }

  #convertResult(result: RunResult): AgentResult<Output> {

    try {
      return {
        output: this.#codec.decodeJson(result.output),
        runId: result.runId,
        usage: result.usage,
        turns: result.turns,
        events: result.events,
      };
    } catch (error: unknown) {
      throw new OrionError("validated terminal output could not be decoded", {
        code: "malformed_response",
        cause: error,
      });
    }

  }

}

function isRunResult(item: RunEvent | RunResult): item is RunResult {

  return "output" in item;

}
