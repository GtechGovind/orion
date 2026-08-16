import {createRequire} from "node:module";

import type {JsonObject} from "../model/json.js";
import type {ModelRequest} from "../model/model-request.js";
import type {ToolCall, Usage} from "../model/model-response.js";
import type {AgentWire} from "../runtime/agent.js";
import type {RunEvent} from "../runtime/run-event.js";
import type {ProtocolError} from "../runtime/protocol-error.js";
import {
  asArray,
  asNonNegativeInteger,
  asObject,
  asString,
  parseModelRequest,
  parseRunEvent,
  parseToolCall,
  parseUsage,
} from "./protocol.js";

/** Model effect requested by the Rust kernel. */
export interface ModelEffect {

  /** Effect discriminator. */
  readonly type: "call_model";

  /** Fully validated provider-neutral request. */
  readonly request: ModelRequest;

}

/** Tool effect requested by the Rust kernel. */
export interface ToolEffect {

  /** Effect discriminator. */
  readonly type: "execute_tool";

  /** Kernel-generated action identity. */
  readonly actionId: string;

  /** Validated application tool call. */
  readonly call: ToolCall;

}

/** Outstanding host effect requested by the Rust kernel. */
export type NativeEffect = ModelEffect | ToolEffect;

/** Successful terminal result returned by the Rust kernel. */
export interface NativeResult {

  /** Stable run identity. */
  readonly runId: string;

  /** Final assistant output. */
  readonly output: string;

  /** Aggregate normalized token usage. */
  readonly usage: Usage;

  /** Number of completed model turns. */
  readonly turns: number;

}

/** One validated native state-machine advancement. */
export interface NativeStep {

  /** Events produced by this transition. */
  readonly events: readonly RunEvent[];

  /** Outstanding host effect, if the run is suspended. */
  readonly effect: NativeEffect | null;

  /** Terminal result, if the run completed successfully. */
  readonly result: NativeResult | null;

}

/** Typed start command accepted by the native constructor. */
export interface NativeStartCommand {

  /** Host-generated stable run identifier. */
  readonly run_id: string;

  /** Immutable agent definition in protocol field casing. */
  readonly agent: AgentWire;

  /** Initial user input. */
  readonly input: string;

}

interface NativeErrorWire {

  readonly code: ProtocolError["code"];

  readonly message: string;

  readonly retryable: boolean;

  readonly retry_after_ms: number | null;

}

interface RawNativeRun {

  takeStep(): unknown;

  resume(result: JsonObject): unknown;

  cancel(): unknown;

  fail(error: NativeErrorWire): unknown;

}

interface NativeModule {

  readonly NativeRun: new (command: NativeStartCommand) => RawNativeRun;

}

/** Safe adapter around the dynamically loaded Node-API session. */
export class NativeRun {

  readonly #binding: RawNativeRun;

  /** Loads the native module and starts one Rust-owned session. */
  constructor(command: NativeStartCommand) {

    const loaded = loadNativeModule();
    this.#binding = new loaded.NativeRun(command);

  }

  /** Takes and validates the initial kernel step. */
  takeStep(): NativeStep {
    return parseNativeStep(this.#binding.takeStep());
  }

  /** Resumes the pending effect and validates the next kernel step. */
  resume(result: JsonObject): NativeStep {
    return parseNativeStep(this.#binding.resume(result));
  }

  /** Cancels the session and validates its terminal transition. */
  cancel(): NativeStep {
    return parseNativeStep(this.#binding.cancel());
  }

  /** Fails the session with a sanitized protocol error. */
  fail(error: ProtocolError): NativeStep {

    return parseNativeStep(this.#binding.fail({
      code: error.code,
      message: error.message,
      retryable: error.retryable,
      retry_after_ms: error.retryAfterMs,
    }));

  }

}

function loadNativeModule(): NativeModule {

  // The compiled wrapper lives beside the generated Node-API binary in dist/.
  const loaded: unknown = createRequire(import.meta.url)("../native.cjs");
  if (!isNativeModule(loaded)) {
    throw new TypeError("Orion native module does not expose NativeRun");
  }

  return loaded;

}

function isNativeModule(value: unknown): value is NativeModule {
  return typeof value === "object"
    && value !== null
    && "NativeRun" in value
    && typeof value.NativeRun === "function";
}

function parseNativeStep(value: unknown): NativeStep {

  const step = asObject(value, "native step");

  return {
    events: asArray(step.events, "native step events").map(parseRunEvent),
    effect: step.effect === null ? null : parseNativeEffect(step.effect),
    result: step.result === null ? null : parseNativeResult(step.result),
  };

}

function parseNativeEffect(value: unknown): NativeEffect {

  const effect = asObject(value, "native effect");

  switch (asString(effect.type, "native effect type")) {
    case "call_model":
      return {type: "call_model", request: parseModelRequest(effect.request)};
    case "execute_tool":
      return {
        type: "execute_tool",
        actionId: asString(effect.action_id, "native action id"),
        call: parseToolCall(effect.call),
      };
    default:
      throw new TypeError("native effect has an unsupported type");
  }

}

function parseNativeResult(value: unknown): NativeResult {

  const result = asObject(value, "native result");

  return {
    runId: asString(result.run_id, "native result run id"),
    output: asString(result.output, "native result output"),
    usage: parseUsage(result.usage, "native result usage"),
    turns: asNonNegativeInteger(result.turns, "native result turns"),
  };

}
