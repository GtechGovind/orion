import type {Usage} from "../model/model-response.js";
import type {RunEvent} from "./run-event.js";

/** Successful terminal value and complete observed event trace. */
export interface RunResult {

  /** Stable identifier for the completed run. */
  readonly runId: string;

  /** Final assistant text. */
  readonly output: string;

  /** Aggregate normalized token usage. */
  readonly usage: Usage;

  /** Number of completed model turns. */
  readonly turns: number;

  /** Every event observed before the terminal result. */
  readonly events: readonly RunEvent[];

}
