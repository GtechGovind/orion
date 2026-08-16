import type {ModelAdapter} from "./model-adapter.js";
import {ModelRef} from "./model-ref.js";

/** Application-facing model selection paired with its provider adapter. */
export class Model {

  /** Provider-neutral model reference consumed by the Rust runtime. */
  readonly ref: ModelRef;

  /** Adapter responsible for provider I/O. */
  readonly adapter: ModelAdapter;

  /** Creates a configured model from an internal adapter and reference. */
  constructor(ref: ModelRef, adapter: ModelAdapter) {

    this.ref = ref;
    this.adapter = adapter;

  }

}
