import type { ModelProfile } from "./model-profile.js";
import type { ModelRef } from "./model-ref.js";
import type { ModelRequest } from "./model-request.js";
import type { ModelResponse } from "./model-response.js";

/** Connects Orion to one model-provider namespace. */
export interface ModelAdapter {

  /** Stable provider key used by model references and registries. */
  readonly provider: string;

  /** Returns capabilities without executing the selected model. */
  profile(model: ModelRef): ModelProfile;

  /**
   * Executes one provider-neutral request.
   *
   * @param request - Validated request emitted by the Orion kernel.
   * @param signal - Optional cancellation signal for provider I/O.
   * @returns A fully normalized model response.
   * @throws Error when transport or response normalization fails.
   */
  complete(request: ModelRequest, signal?: AbortSignal): Promise<ModelResponse>;

  /** Releases resources owned by the adapter. */
  close?(): Promise<void>;

}
