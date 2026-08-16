import type {JsonObject, JsonSchema} from "../model/json.js";
import type {ProviderOptions} from "../model/model-request.js";
import {ModelRef} from "../model/model-ref.js";
import type {ToolDefinition} from "./tool.js";

/** Construction options for an immutable agent definition. */
export interface AgentDefinitionOptions {

  /** Stable application identifier. */
  readonly id: string;

  /** Human-readable display name. */
  readonly name: string;

  /** System-level behavior instructions. */
  readonly instructions: string;

  /** Explicit provider/model selection resolved through the model registry. */
  readonly model: ModelRef;

  /** Application tools available to the model. */
  readonly tools?: readonly ToolDefinition[];

  /** Optional JSON Schema constraining the assistant output. */
  readonly outputSchema?: JsonSchema;

  /** Optional sampling temperature. */
  readonly temperature?: number;

  /** Optional positive generation token limit. */
  readonly maxOutputTokens?: number;

  /** Provider-keyed opaque request options. */
  readonly providerOptions?: ProviderOptions;

  /** Positive maximum number of model turns. */
  readonly maxTurns?: number;

}

/** Wire representation consumed directly by the native binding. */
export interface AgentWire {

  /** Stable application identifier. */
  readonly id: string;

  /** Human-readable display name. */
  readonly name: string;

  /** System-level behavior instructions. */
  readonly instructions: string;

  /** Selected provider and model. */
  readonly model: {readonly provider: string; readonly model: string};

  /** Model-visible tool specifications. */
  readonly tools: readonly JsonObject[];

  /** Optional constrained-output JSON Schema. */
  readonly output_schema: JsonSchema | null;

  /** Portable and provider-specific model settings. */
  readonly model_settings: {
    readonly temperature: number | null;
    readonly max_output_tokens: number | null;
    readonly provider_options: ProviderOptions;
  };

  /** Positive maximum number of model turns. */
  readonly max_turns: number;

}

/** Immutable host-owned definition of agent behavior and limits. */
export class AgentDefinition {

  /** Application tools available during a run. */
  readonly tools: readonly ToolDefinition[];

  /** Validated immutable construction options. */
  readonly options: AgentDefinitionOptions;

  /** Creates an agent from language-native options after validating its invariants. */
  constructor(options: AgentDefinitionOptions) {

    validateOptions(options);

    this.options = options;
    this.tools = options.tools ?? [];

  }

  /** Returns the versioned value consumed by the Rust kernel. */
  toWire(): AgentWire {
    const settings: AgentWire["model_settings"] = {
      temperature: this.options.temperature ?? null,
      max_output_tokens: this.options.maxOutputTokens ?? null,
      provider_options: this.options.providerOptions ?? {},
    };

    return {
      id: this.options.id,
      name: this.options.name,
      instructions: this.options.instructions,
      model: {provider: this.options.model.provider, model: this.options.model.model},
      tools: this.tools.map(tool => ({
        name: tool.name,
        description: tool.description,
        input_schema: tool.inputSchema,
      } satisfies JsonObject)),
      output_schema: this.options.outputSchema ?? null,
      model_settings: settings,
      max_turns: this.options.maxTurns ?? 8,
    };

  }

}

function validateOptions(options: AgentDefinitionOptions): void {

  if (!options.id.trim() || !options.name.trim()) {
    throw new TypeError("agent id and name must be non-empty");
  }

  if (options.maxTurns !== undefined && (!Number.isInteger(options.maxTurns) || options.maxTurns < 1)) {
    throw new RangeError("agent maxTurns must be a positive integer");
  }

  if (
    options.maxOutputTokens !== undefined
    && (!Number.isInteger(options.maxOutputTokens) || options.maxOutputTokens < 1)
  ) {
    throw new RangeError("agent maxOutputTokens must be a positive integer");
  }

  if (options.temperature !== undefined && !Number.isFinite(options.temperature)) {
    throw new RangeError("agent temperature must be finite");
  }

  const toolNames = new Set<string>();
  for (const tool of options.tools ?? []) {

    if (!tool.name.trim() || toolNames.has(tool.name)) {
      throw new TypeError("agent tool names must be non-empty and unique");
    }

    toolNames.add(tool.name);

  }

}
