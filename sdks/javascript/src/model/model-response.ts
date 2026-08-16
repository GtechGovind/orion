import type {JsonObject} from "./json.js";

/** Normalized reason a model stopped generating output. */
export type FinishReason = "stop" | "tool_calls" | "length" | "content_filter" | "other";

/** Normalized token consumption. */
export interface Usage {

  /** Number of input tokens consumed. */
  readonly inputTokens: number;

  /** Number of output tokens generated. */
  readonly outputTokens: number;

}

/** Provider-neutral tool invocation proposed by a model. */
export interface ToolCall {

  /** Provider-supplied call identifier. */
  readonly id: string;

  /** Registered application tool name. */
  readonly name: string;

  /** Object-shaped JSON arguments; the application validates schema constraints. */
  readonly arguments: JsonObject;

}

/** Complete normalized response returned by a model adapter. */
export interface ModelResponse {

  /** Assistant text, which may be empty when tools were requested. */
  readonly content: string;

  /** Tool calls in provider order. */
  readonly toolCalls: readonly ToolCall[];

  /** Normalized termination reason. */
  readonly finishReason: FinishReason;

  /** Token usage, with zeroes when the provider omits accounting. */
  readonly usage: Usage;

  /** Opaque continuation data retained for the provider adapter. */
  readonly providerState: JsonObject;

}
