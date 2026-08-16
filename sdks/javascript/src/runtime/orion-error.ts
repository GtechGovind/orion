import type {ErrorCode, ProtocolError} from "./protocol-error.js";

/** Optional machine-readable details attached to an Orion SDK failure. */
export interface OrionErrorOptions {

  /** Original failure retained for diagnostics without changing the stable code. */
  readonly cause?: unknown;

  /** Stable failure category. Defaults to `invalid_state`. */
  readonly code?: ErrorCode;

  /** Whether caller policy may retry the failed operation. */
  readonly retryable?: boolean;

  /** Provider-suggested retry delay in milliseconds. */
  readonly retryAfterMs?: number | null;

}

/** Normalized, machine-readable SDK failure safe for applications to inspect. */
export class OrionError extends Error {

  /** Stable JavaScript error name for Orion SDK failures. */
  override name = "OrionError";

  /** Stable failure category shared with the Rust protocol. */
  readonly code: ErrorCode;

  /** Whether caller policy may retry the failed operation. */
  readonly retryable: boolean;

  /** Provider-suggested retry delay in milliseconds. */
  readonly retryAfterMs: number | null;

  /** Creates an SDK error with a stable category and optional cause. */
  constructor(message: string, options: OrionErrorOptions = {}) {

    super(message, options.cause === undefined ? undefined : {cause: options.cause});

    this.code = options.code ?? "invalid_state";
    this.retryable = options.retryable ?? false;
    this.retryAfterMs = options.retryAfterMs ?? null;

  }

}

/** Converts an internal Rust protocol failure into the public SDK error. */
export function orionErrorFromProtocol(error: ProtocolError, cause?: unknown): OrionError {

  return new OrionError(error.message, {
    code: error.code,
    retryable: error.retryable,
    retryAfterMs: error.retryAfterMs,
    ...(cause === undefined ? {} : {cause}),
  });

}
