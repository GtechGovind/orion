import type { JsonObject, JsonSchema } from "./json.js";
import type { ModelRefValue } from "./model-ref.js";
import type { ToolCall } from "./model-response.js";

/** Provider-neutral role for one transcript message. */
export type MessageRole = "system" | "user" | "assistant" | "tool";

/** Provider-neutral transcript message passed to an adapter. */
export interface ModelMessage {

  /** Semantic author of the message. */
  readonly role: MessageRole;

  /** Text content of the message. */
  readonly content: string;

  /** Matching call identifier for a tool-result message. */
  readonly toolCallId: string | null;

  /** Calls proposed by an assistant message. */
  readonly toolCalls: readonly ToolCall[];

}

/** Model-visible declaration of an application tool. */
export interface ModelTool {

  /** Stable tool name. */
  readonly name: string;

  /** Human-readable guidance for the model. */
  readonly description: string;

  /** JSON Schema for tool arguments. */
  readonly inputSchema: JsonSchema;

}

/** Validated opaque options keyed by provider namespace. */
export interface ProviderOptions {

  /** Provider namespace mapped to its validated opaque options. */
  readonly [provider: string]: JsonObject;

}

/** Portable model settings and provider-specific extensions. */
export interface ModelSettings {

  /** Sampling temperature, or `null` to use the provider default. */
  readonly temperature: number | null;

  /** Generation token limit, or `null` to use the provider default. */
  readonly maxOutputTokens: number | null;

  /** Opaque options isolated by provider namespace. */
  readonly providerOptions: ProviderOptions;

}

/** Fully typed provider-neutral request emitted by the Orion kernel. */
export interface ModelRequest {

  /** Selected provider and model. */
  readonly model: ModelRefValue;

  /** Canonical transcript in chronological order. */
  readonly messages: readonly ModelMessage[];

  /** Tools visible to the selected model. */
  readonly tools: readonly ModelTool[];

  /** Optional schema constraining the assistant output. */
  readonly outputSchema: JsonSchema | null;

  /** Portable and provider-specific request settings. */
  readonly settings: ModelSettings;

  /** Opaque continuation data owned by provider adapters. */
  readonly providerState: JsonObject;

}
