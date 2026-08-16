/** Serializable provider and model identity used at protocol boundaries. */
export interface ModelRefValue {

  /** Stable provider namespace. */
  readonly provider: string;

  /** Provider-specific model identifier. */
  readonly model: string;

}

/** Selects a model within a stable provider namespace. */
export class ModelRef {

  /** Stable provider key. */
  readonly provider: string;

  /** Provider-specific model identifier. */
  readonly model: string;

  /**
   * Creates a model reference from explicit components.
   *
   * @throws TypeError when either component is empty.
   */
  constructor(provider: string, model: string) {

    if (!provider.trim() || !model.trim()) {
      throw new TypeError("model provider and identifier must be non-empty");
    }

    this.provider = provider;
    this.model = model;

  }

  /**
   * Parses `provider:model` notation.
   *
   * @throws TypeError when the notation omits either component.
   */
  static parse(value: string): ModelRef {

    const index = value.indexOf(":");
    if (index <= 0 || index === value.length - 1) {
      throw new TypeError("model reference must use provider:model notation");
    }

    return new ModelRef(value.slice(0, index), value.slice(index + 1));

  }

}
