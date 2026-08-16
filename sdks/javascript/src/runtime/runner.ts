import {randomUUID} from "node:crypto";

import {NativeRun, type NativeEffect} from "../internal/native.js";
import type {JsonObject} from "../model/json.js";
import type {ModelResponse} from "../model/model-response.js";
import {ModelRef} from "../model/model-ref.js";
import {ModelRegistry} from "../model/registry.js";
import {AgentDefinition} from "./agent.js";
import {OrionError, orionErrorFromProtocol} from "./orion-error.js";
import type {ProtocolError} from "./protocol-error.js";
import type {RunEvent} from "./run-event.js";
import type {RunResult} from "./run-result.js";

/** Optional identity and cancellation controls for a run. */
export interface RunOptions {

  /** Caller-supplied identity, or a generated UUID-based identity when omitted. */
  readonly runId?: string;

  /** Signal that cancels provider I/O, tools, and the native session. */
  readonly signal?: AbortSignal;

}

/** Coordinates typed host effects while Rust owns mutable run state. */
export class Runner {

  readonly #models: ModelRegistry;

  /** Creates a runner with application-owned model adapters. */
  constructor(models: ModelRegistry) {
    this.#models = models;
  }

  /**
   * Runs an agent to successful completion.
   *
   * @throws OrionError when the native runtime or a host effect fails.
   */
  async run(
    agent: AgentDefinition,
    input: string,
    options: RunOptions = {},
  ): Promise<RunResult> {

    let result: RunResult | undefined;

    for await (const item of this.runStream(agent, input, options)) {
      if (isRunResult(item)) {
        result = item;
      }
    }

    if (!result) {
      throw new OrionError("run ended without a result");
    }

    return result;

  }

  /**
   * Yields ordered lifecycle events followed by one successful result.
   *
   * Breaking iteration early cancels the Rust-owned session.
   */
  async *runStream(
    agent: AgentDefinition,
    input: string,
    options: RunOptions = {},
  ): AsyncGenerator<RunEvent | RunResult> {

    throwIfAborted(options.signal);
    this.validateCapabilities(agent);

    let native: NativeRun;

    try {
      native = new NativeRun({
        run_id: options.runId ?? `run-${randomUUID()}`,
        agent: agent.toWire(),
        input,
      });
    } catch (error: unknown) {
      throw normalizeError(error, "native run rejected the agent definition", "invalid_command");
    }

    const events: RunEvent[] = [];
    let step = native.takeStep();
    let terminal = false;

    try {

      while (!terminal) {

        for (const event of step.events) {
          events.push(event);
          yield event;
        }

        if (step.result) {
          terminal = true;
          yield {...step.result, events: [...events]};
          return;
        }

        if (!step.effect) {
          const failure = findRunFailure(step.events);
          if (failure) {
            terminal = true;
            throw orionErrorFromProtocol(failure);
          }

          if (step.events.some(event => event.type === "run_cancelled")) {
            terminal = true;
            throw new OrionError("run cancelled", {code: "cancelled"});
          }

          throw new OrionError("run suspended without an effect or terminal result");
        }

        const effect = step.effect;
        throwIfAborted(options.signal);

        let effectResult: JsonObject;

        try {
          effectResult = await this.executeEffect(agent, effect, options.signal);
        } catch (error: unknown) {

          if (options.signal?.aborted) {
            throw abortError(options.signal);
          }

          const failure = hostEffectError(effect, error);
          const failureStep = native.fail(failure);

          for (const event of failureStep.events) {
            events.push(event);
            yield event;
          }

          terminal = true;
          throw orionErrorFromProtocol(failure, error);

        }

        try {
          step = native.resume(effectResult);
        } catch (error: unknown) {
          throw normalizeError(error, "native run rejected the host effect result", "invalid_state");
        }

      }

    } finally {

      if (!terminal) {
        native.cancel();
      }

    }

  }

  private validateCapabilities(agent: AgentDefinition): void {
    const selectedModel = agent.options.model;
    const profile = this.#models.resolve(selectedModel).profile(selectedModel);

    if (agent.tools.length && profile.toolCalling === "unsupported") {
      throw new OrionError(
        `model ${selectedModel.provider}:${selectedModel.model} does not support tool calling`,
        {code: "unsupported_capability"},
      );
    }

    if (agent.options.outputSchema && profile.structuredOutput === "unsupported") {
      throw new OrionError(
        `model ${selectedModel.provider}:${selectedModel.model} does not support structured output`,
        {code: "unsupported_capability"},
      );
    }

  }

  private async executeEffect(
    agent: AgentDefinition,
    effect: NativeEffect,
    signal?: AbortSignal,
  ): Promise<JsonObject> {

    if (effect.type === "call_model") {
      const model = new ModelRef(effect.request.model.provider, effect.request.model.model);
      const response = await this.#models.resolve(model).complete(effect.request, signal);

      return modelEffectResult(response);
    }

    const tool = agent.tools.find(candidate => candidate.name === effect.call.name);
    if (!tool) {
      throw new OrionError(
        `model requested unregistered tool ${JSON.stringify(effect.call.name)}`,
        {code: "configuration"},
      );
    }

    return {
      type: "tool",
      value: {content: await tool.execute(effect.call.arguments, signal)},
    };

  }

}

function isRunResult(item: RunEvent | RunResult): item is RunResult {
  return "output" in item;
}

function modelEffectResult(response: ModelResponse): JsonObject {

  return {
    type: "model",
    value: {
      content: response.content,
      tool_calls: response.toolCalls.map(call => ({
        id: call.id,
        name: call.name,
        arguments: call.arguments,
      })),
      finish_reason: response.finishReason,
      usage: {
        input_tokens: response.usage.inputTokens,
        output_tokens: response.usage.outputTokens,
      },
      provider_state: response.providerState,
    },
  };

}

function hostEffectError(effect: NativeEffect, error: unknown): ProtocolError {

  if (error instanceof OrionError) {
    return {
      code: error.code,
      message: error.message,
      retryable: error.retryable,
      retryAfterMs: error.retryAfterMs,
    };
  }

  return {
    code: effect.type === "call_model" ? "provider" : "tool",
    message: effect.type === "call_model" ? "model provider request failed" : "tool execution failed",
    retryable: false,
    retryAfterMs: null,
  };

}

function throwIfAborted(signal: AbortSignal | undefined): void {

  if (signal?.aborted) {
    throw abortError(signal);
  }

}

function abortError(signal: AbortSignal): Error {

  return new OrionError("run cancelled", {
    code: "cancelled",
    ...(signal.reason === undefined ? {} : {cause: signal.reason}),
  });

}

function normalizeError(error: unknown, fallback: string, code: ProtocolError["code"]): OrionError {

  if (error instanceof OrionError) {
    return error;
  }

  return new OrionError(error instanceof Error ? error.message : fallback, {
    code,
    ...(error === undefined ? {} : {cause: error}),
  });

}

function findRunFailure(events: readonly RunEvent[]): ProtocolError | undefined {

  for (let index = events.length - 1; index >= 0; index -= 1) {
    const event = events[index];
    if (event?.type === "run_failed") {
      return event.data.error;
    }
  }

  return undefined;

}
