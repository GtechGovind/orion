import type {ProtocolError} from "./protocol-error.js";

interface EventEnvelope<Type extends string, Data> {

  /** Run identifier shared by every event in one trace. */
  readonly runId: string;

  /** Zero-based monotonic event sequence. */
  readonly sequence: number;

  /** Stable lifecycle discriminator. */
  readonly type: Type;

  /** Event-specific immutable payload. */
  readonly data: Data;

}

/** Ordered lifecycle event emitted while an Orion run advances. */
export type RunEvent =
  | EventEnvelope<"run_started", {readonly agentId: string}>
  | EventEnvelope<"model_requested", {readonly turn: number; readonly provider: string; readonly model: string}>
  | EventEnvelope<"model_completed", {readonly turn: number; readonly output: string; readonly toolCallCount: number}>
  | EventEnvelope<"tool_requested" | "tool_completed", {readonly actionId: string; readonly callId: string; readonly name: string}>
  | EventEnvelope<"run_completed", {readonly output: string}>
  | EventEnvelope<"run_failed", {readonly error: ProtocolError}>
  | EventEnvelope<"run_cancelled", null>;
