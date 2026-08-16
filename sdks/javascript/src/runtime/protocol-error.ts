/** Stable machine-readable categories for failures crossing the native boundary. */
export type ErrorCode =
  | "invalid_command"
  | "invalid_state"
  | "configuration"
  | "authentication"
  | "rate_limited"
  | "timeout"
  | "network"
  | "unsupported_capability"
  | "content_safety"
  | "malformed_response"
  | "provider"
  | "tool"
  | "cancelled"
  | "turn_limit_exceeded";

/** Sanitized failure value accepted by the Rust runtime. */
export interface ProtocolError {

  /** Stable failure category. */
  readonly code: ErrorCode;

  /** Human-readable message that contains no secrets. */
  readonly message: string;

  /** Whether a caller policy may retry the operation. */
  readonly retryable: boolean;

  /** Provider-suggested retry delay in milliseconds. */
  readonly retryAfterMs: number | null;

}
