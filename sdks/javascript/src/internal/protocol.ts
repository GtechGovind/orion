import type {Json, JsonObject} from "../model/json.js";
import type {ModelRequest, ModelSettings, ProviderOptions} from "../model/model-request.js";
import type {ToolCall, Usage} from "../model/model-response.js";
import type {RunEvent} from "../runtime/run-event.js";
import type {ErrorCode, ProtocolError} from "../runtime/protocol-error.js";

type UnknownObject = Readonly<Record<string, unknown>>;

/** Converts an untrusted value into bounded, recursively validated JSON. */
export function parseJson(value: unknown, context: string, depth = 0): Json {

  if (depth > MAX_JSON_DEPTH) {
    throw new TypeError(`${context} exceeds the maximum JSON depth`);
  }

  if (value === null || typeof value === "boolean" || typeof value === "string") {
    return value;
  }

  if (typeof value === "number") {
    if (!Number.isFinite(value)) {
      throw new TypeError(`${context} contains a non-finite number`);
    }

    return value;
  }

  if (Array.isArray(value)) {
    return value.map((item, index) => parseJson(item, `${context}[${index}]`, depth + 1));
  }

  const object = asObject(value, context);
  return Object.fromEntries(
    Object.entries(object).map(([key, item]) => [
      key,
      parseJson(item, `${context}.${key}`, depth + 1),
    ]),
  );

}

/** Validates an unknown JSON value as an object. */
export function parseJsonObject(value: unknown, context: string): JsonObject {

  const parsed = parseJson(value, context);
  if (!isJsonObject(parsed)) {
    throw new TypeError(`${context} must be a JSON object`);
  }

  return parsed;

}

function isJsonObject(value: Json): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Converts one native model request into the public camel-case contract. */
export function parseModelRequest(value: unknown): ModelRequest {

  const request = asObject(value, "native model request");
  const model = asObject(request.model, "native model reference");

  return {
    model: {
      provider: asString(model.provider, "native model provider"),
      model: asString(model.model, "native model identifier"),
    },
    messages: asArray(request.messages, "native model messages").map(parseMessage),
    tools: asArray(request.tools, "native model tools").map(parseModelTool),
    outputSchema: request.output_schema === null
      ? null
      : parseJsonObject(request.output_schema, "native output schema"),
    settings: parseModelSettings(request.settings),
    providerState: parseJsonObject(request.provider_state, "native provider state"),
  };

}

/** Converts one native event into a typed public lifecycle event. */
export function parseRunEvent(value: unknown): RunEvent {

  const event = asObject(value, "native run event");
  const kind = asObject(event.kind, "native run event kind");
  const envelope = {
    runId: asString(event.run_id, "native event run id"),
    sequence: asNonNegativeInteger(event.sequence, "native event sequence"),
  };

  switch (asString(kind.type, "native event type")) {
    case "run_started":
      return {...envelope, type: "run_started", data: {agentId: asString(kind.agent_id, "event agent id")}};
    case "model_requested":
      return {
        ...envelope,
        type: "model_requested",
        data: {
          turn: asNonNegativeInteger(kind.turn, "event turn"),
          provider: asString(kind.provider, "event provider"),
          model: asString(kind.model, "event model"),
        },
      };
    case "model_completed":
      return {
        ...envelope,
        type: "model_completed",
        data: {
          turn: asNonNegativeInteger(kind.turn, "event turn"),
          output: asString(kind.output, "event output"),
          toolCallCount: asNonNegativeInteger(kind.tool_call_count, "event tool-call count"),
        },
      };
    case "tool_requested":
      return parseToolEvent(envelope, "tool_requested", kind);
    case "tool_completed":
      return parseToolEvent(envelope, "tool_completed", kind);
    case "run_completed":
      return {
        ...envelope,
        type: "run_completed",
        data: {output: asString(kind.output, "event output")},
      };
    case "run_failed":
      return {...envelope, type: "run_failed", data: {error: parseProtocolError(kind.error)}};
    case "run_cancelled":
      return {...envelope, type: "run_cancelled", data: null};
    default:
      throw new TypeError("native run event has an unsupported type");
  }

}

/** Parses a native tool call. */
export function parseToolCall(value: unknown, context = "native tool call"): ToolCall {

  const call = asObject(value, context);

  return {
    id: asString(call.id, `${context} id`),
    name: asString(call.name, `${context} name`),
    arguments: parseJsonObject(call.arguments, `${context} arguments`),
  };

}

/** Parses native token accounting. */
export function parseUsage(value: unknown, context: string): Usage {

  const usage = asObject(value, context);

  return {
    inputTokens: asNonNegativeInteger(usage.input_tokens, `${context} input tokens`),
    outputTokens: asNonNegativeInteger(usage.output_tokens, `${context} output tokens`),
  };

}

/** Requires an object at a dynamic provider or native boundary. */
export function asObject(value: unknown, context: string): UnknownObject {

  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new TypeError(`${context} must be an object`);
  }

  return value as UnknownObject;

}

/** Requires an array at a dynamic provider or native boundary. */
export function asArray(value: unknown, context: string): readonly unknown[] {

  if (!Array.isArray(value)) {
    throw new TypeError(`${context} must be an array`);
  }

  return value;

}

/** Requires a string at a dynamic provider or native boundary. */
export function asString(value: unknown, context: string): string {

  if (typeof value !== "string") {
    throw new TypeError(`${context} must be a string`);
  }

  return value;

}

/** Requires a finite non-negative integer at a dynamic boundary. */
export function asNonNegativeInteger(value: unknown, context: string): number {

  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new TypeError(`${context} must be a non-negative safe integer`);
  }

  return value;

}

function parseMessage(value: unknown): ModelRequest["messages"][number] {

  const message = asObject(value, "native model message");

  return {
    role: parseRole(message.role),
    content: asString(message.content, "native message content"),
    toolCallId: message.tool_call_id === null
      ? null
      : asString(message.tool_call_id, "native message tool-call id"),
    toolCalls: asArray(message.tool_calls, "native message tool calls").map(call => parseToolCall(call)),
  };

}

function parseModelTool(value: unknown): ModelRequest["tools"][number] {

  const tool = asObject(value, "native model tool");

  return {
    name: asString(tool.name, "native model tool name"),
    description: asString(tool.description, "native model tool description"),
    inputSchema: parseJsonObject(tool.input_schema, "native model tool schema"),
  };

}

function parseModelSettings(value: unknown): ModelSettings {

  const settings = asObject(value, "native model settings");

  return {
    temperature: settings.temperature === null
      ? null
      : asFiniteNumber(settings.temperature, "native model temperature"),
    maxOutputTokens: settings.max_output_tokens === null
      ? null
      : asNonNegativeInteger(settings.max_output_tokens, "native model token limit"),
    providerOptions: parseProviderNamespaces(settings.provider_options, "native provider options"),
  };

}

function parseProviderNamespaces(value: unknown, context: string): ProviderOptions {

  const namespaces = asObject(value, context);

  return Object.fromEntries(
    Object.entries(namespaces).map(([provider, options]) => [
      provider,
      parseJsonObject(options, `${context}.${provider}`),
    ]),
  );

}

function parseRole(value: unknown): ModelRequest["messages"][number]["role"] {

  if (value === "system" || value === "user" || value === "assistant" || value === "tool") {
    return value;
  }

  throw new TypeError("native message has an unsupported role");

}

function parseToolEvent(
  envelope: {readonly runId: string; readonly sequence: number},
  type: "tool_requested" | "tool_completed",
  kind: UnknownObject,
): RunEvent {

  return {
    ...envelope,
    type,
    data: {
      actionId: asString(kind.action_id, "event action id"),
      callId: asString(kind.call_id, "event call id"),
      name: asString(kind.name, "event tool name"),
    },
  };

}

function parseProtocolError(value: unknown): ProtocolError {

  const error = asObject(value, "native protocol error");

  return {
    code: parseErrorCode(error.code),
    message: asString(error.message, "native error message"),
    retryable: asBoolean(error.retryable, "native error retryable flag"),
    retryAfterMs: error.retry_after_ms === null
      ? null
      : asNonNegativeInteger(error.retry_after_ms, "native error retry delay"),
  };

}

function parseErrorCode(value: unknown): ErrorCode {

  if (typeof value === "string" && ERROR_CODES.has(value as ErrorCode)) {
    return value as ErrorCode;
  }

  throw new TypeError("native protocol error has an unsupported code");

}

function asBoolean(value: unknown, context: string): boolean {

  if (typeof value !== "boolean") {
    throw new TypeError(`${context} must be a boolean`);
  }

  return value;

}

function asFiniteNumber(value: unknown, context: string): number {

  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new TypeError(`${context} must be a finite number`);
  }

  return value;

}

const MAX_JSON_DEPTH = 64;

const ERROR_CODES: ReadonlySet<ErrorCode> = new Set([
  "invalid_command",
  "invalid_state",
  "configuration",
  "authentication",
  "rate_limited",
  "timeout",
  "network",
  "unsupported_capability",
  "content_safety",
  "malformed_response",
  "provider",
  "tool",
  "cancelled",
  "turn_limit_exceeded",
]);
