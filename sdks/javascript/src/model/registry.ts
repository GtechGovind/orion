import { OrionError } from "../runtime/orion-error.js";
import type { ModelAdapter } from "./model-adapter.js";
import type { ModelRef } from "./model-ref.js";

/** Resolves unique provider keys to application-owned adapters. */
export class ModelRegistry {

  readonly #adapters = new Map<string, ModelAdapter>();

  /** Creates a registry and rejects duplicate provider ownership. */
  constructor(adapters: readonly ModelAdapter[]) {

    for (const adapter of adapters) {

      if (this.#adapters.has(adapter.provider)) {
        throw new OrionError(`duplicate model provider ${JSON.stringify(adapter.provider)}`);
      }

      this.#adapters.set(adapter.provider, adapter);

    }

  }

  /** Resolves the adapter selected by a model reference. */
  resolve(model: ModelRef): ModelAdapter {

    const adapter = this.#adapters.get(model.provider);
    if (!adapter) {
      throw new OrionError(
        `no model adapter registered for provider ${JSON.stringify(model.provider)}`,
      );
    }

    return adapter;

  }

  /**
   * Closes every adapter and waits for all cleanup operations.
   *
   * @throws AggregateError containing every adapter cleanup failure.
   */
  async close(): Promise<void> {

    const results = await Promise.allSettled(
      [...this.#adapters.values()].map(adapter => adapter.close?.()),
    );
    const failures = results
      .filter((result): result is PromiseRejectedResult => result.status === "rejected")
      .map(result => result.reason);
    if (failures.length) {
      throw new AggregateError(failures, "one or more model adapters failed to close");
    }

  }

}
