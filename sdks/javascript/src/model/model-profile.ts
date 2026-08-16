/** Support level for one model capability. */
export type Capability = "native" | "emulated" | "unsupported" | "unknown";

/** Capabilities available for a selected model. */
export interface ModelProfile {

  /** Token streaming support. */
  readonly streaming: Capability;

  /** Model-originated tool-call support. */
  readonly toolCalling: Capability;

  /** Schema-constrained output support. */
  readonly structuredOutput: Capability;

  /** Parallel tool-call support. */
  readonly parallelToolCalls: Capability;

  /** Maximum context size when the provider reports it. */
  readonly maxContextTokens?: number;

}
