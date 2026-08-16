import {
  asArray,
  asNonNegativeInteger,
  asObject,
  asString,
  parseJsonObject,
} from "../internal/protocol.js";
import type {JsonObject} from "../model/json.js";
import {Model} from "../model/configured.js";
import type {ModelAdapter} from "../model/model-adapter.js";
import type {ModelProfile} from "../model/model-profile.js";
import {ModelRef} from "../model/model-ref.js";
import type {ModelRequest} from "../model/model-request.js";
import type {FinishReason, ModelResponse, ToolCall} from "../model/model-response.js";
import {OrionError} from "../runtime/orion-error.js";

/** Construction options for an OpenAI-compatible adapter. */
export interface OpenAICompatibleOptions {

  /** Provider namespace registered with Orion. Defaults to `openai`. */
  readonly provider?: string;

  /** Bearer credential. Defaults to `OPENAI_API_KEY` when omitted. */
  readonly apiKey?: string;

  /** API root ending before `/chat/completions`. */
  readonly baseUrl?: string;

  /** Positive request timeout in milliseconds. Defaults to 60 seconds. */
  readonly timeoutMs?: number;

}

/** Application-facing configuration for an OpenAI model. */
export type OpenAIOptions = Omit<OpenAICompatibleOptions, "provider">;

/** Chat Completions adapter for OpenAI and compatible endpoints. */
export class OpenAICompatibleAdapter implements ModelAdapter {

  /** Stable namespace handled by this adapter. */
  readonly provider: string;

  readonly #apiKey: string | undefined;

  readonly #endpoint: URL;

  readonly #timeoutMs: number;

  /** Creates an adapter without opening a network connection. */
  constructor(options: OpenAICompatibleOptions = {}) {

    this.provider = options.provider ?? "openai";
    if (!this.provider.trim()) {
      throw new TypeError("model provider must be non-empty");
    }

    this.#apiKey = options.apiKey ?? process.env.OPENAI_API_KEY;
    this.#endpoint = new URL(
      "chat/completions",
      `${(options.baseUrl ?? "https://api.openai.com/v1").replace(/\/+$/, "")}/`,
    );
    this.#timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    if (!Number.isSafeInteger(this.#timeoutMs) || this.#timeoutMs < 1) {
      throw new RangeError("model provider timeoutMs must be a positive safe integer");
    }

  }

  /** Returns capabilities for a model owned by this adapter. */
  profile(model: ModelRef): ModelProfile {

    if (model.provider !== this.provider) {
      throw new OrionError("model provider does not match adapter");
    }

    return {
      streaming: "unsupported",
      toolCalling: "native",
      structuredOutput: "native",
      parallelToolCalls: "unknown",
    };

  }

  /**
   * Executes one Chat Completions request and validates its response.
   *
   * @throws OrionError for HTTP failures or malformed provider responses.
   */
  async complete(request: ModelRequest, signal?: AbortSignal): Promise<ModelResponse> {

    if (request.model.provider !== this.provider) {
      throw new OrionError("model request provider does not match adapter");
    }

    const control = requestControl(signal, this.#timeoutMs);
    let response: Response;

    try {
      response = await fetch(this.#endpoint, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          ...(this.#apiKey ? {authorization: `Bearer ${this.#apiKey}`} : {}),
        },
        body: JSON.stringify(buildPayload(request, this.provider)),
        signal: control.signal,
      });
    } catch (error: unknown) {
      if (control.didTimeout()) {
        throw new OrionError("model provider request timed out", {code: "timeout", cause: error});
      }

      if (signal?.aborted) {
        throw new OrionError("model provider request was cancelled", {
          code: "cancelled",
          ...(signal.reason === undefined ? {} : {cause: signal.reason}),
        });
      }

      throw new OrionError("model provider network request failed", {
        code: "network",
        retryable: true,
        cause: error,
      });
    } finally {
      control.dispose();
    }

    if (!response.ok) {
      throw httpError(response);
    }

    try {
      return parseResponse(await response.json());
    } catch (error: unknown) {
      throw new OrionError("model provider returned a malformed response", {
        code: "malformed_response",
        cause: error,
      });
    }

  }

}

interface RequestControl {

  readonly signal: AbortSignal;

  didTimeout(): boolean;

  dispose(): void;

}

function requestControl(external: AbortSignal | undefined, timeoutMs: number): RequestControl {

  const controller = new AbortController();
  let timedOut = false;
  const timeout = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  const cancel = (): void => controller.abort(external?.reason);

  if (external?.aborted) {
    cancel();
  } else {
    external?.addEventListener("abort", cancel, {once: true});
  }

  return {
    signal: controller.signal,
    didTimeout: () => timedOut,
    dispose: () => {
      clearTimeout(timeout);
      external?.removeEventListener("abort", cancel);
    },
  };

}

function httpError(response: Response): OrionError {

  const code = response.status === 401 || response.status === 403
    ? "authentication"
    : response.status === 429
      ? "rate_limited"
      : "provider";

  return new OrionError(`model provider returned HTTP ${response.status}`, {
    code,
    retryable: response.status === 429 || response.status >= 500,
    retryAfterMs: parseRetryAfter(response.headers.get("retry-after")),
  });

}

function parseRetryAfter(value: string | null): number | null {

  if (value === null) {
    return null;
  }

  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) {
    return Math.round(seconds * 1_000);
  }

  const date = Date.parse(value);
  return Number.isNaN(date) ? null : Math.max(0, date - Date.now());

}

/** Selects an OpenAI model and configures its application-owned adapter. */
export class OpenAI extends Model {

  /** Creates an OpenAI Chat Completions model. */
  constructor(model: string, options: OpenAIOptions = {}) {

    super(
      new ModelRef("openai", model),
      new OpenAICompatibleAdapter({...options, provider: "openai"}),
    );

  }

}

function buildPayload(request: ModelRequest, provider: string): JsonObject {

  const providerOptions = request.settings.providerOptions[provider] ?? {};
  rejectProtectedOverrides(providerOptions);

  // Provider options are merged last only after protecting Orion-owned fields.
  return {
    model: request.model.model,
    messages: request.messages.map(message => ({
      role: message.role,
      content: message.content,
      ...(message.toolCallId ? {tool_call_id: message.toolCallId} : {}),
      ...(message.toolCalls.length
        ? {
            tool_calls: message.toolCalls.map(call => ({
              id: call.id,
              type: "function",
              function: {name: call.name, arguments: JSON.stringify(call.arguments)},
            })),
          }
        : {}),
    })),
    ...(request.tools.length
      ? {
          tools: request.tools.map(tool => ({
            type: "function",
            function: {
              name: tool.name,
              description: tool.description,
              parameters: tool.inputSchema,
            },
          })),
        }
      : {}),
    ...(request.settings.temperature === null
      ? {}
      : {temperature: request.settings.temperature}),
    ...(request.settings.maxOutputTokens === null
      ? {}
      : {max_tokens: request.settings.maxOutputTokens}),
    ...(request.outputSchema
      ? {
          response_format: {
            type: "json_schema",
            json_schema: {name: "orion_output", schema: request.outputSchema, strict: true},
          },
        }
      : {}),
    ...providerOptions,
  };

}

function rejectProtectedOverrides(options: JsonObject): void {

  const protectedFields = PROTECTED_FIELDS.filter(field => field in options);
  if (protectedFields.length) {
    throw new OrionError(
      `provider options cannot override protected fields: ${protectedFields.join(", ")}`,
    );
  }

}

function parseResponse(value: unknown): ModelResponse {

  const response = asObject(value, "provider response");
  const choices = asArray(response.choices, "provider choices");
  if (!choices.length) {
    throw new TypeError("provider response contains no choices");
  }

  const choice = asObject(choices[0], "provider choice");
  const message = asObject(choice.message, "provider message");
  const toolCalls = asArray(message.tool_calls ?? [], "provider tool calls").map(parseProviderToolCall);
  const usage = asObject(response.usage ?? {}, "provider usage");

  return {
    content: message.content === null || message.content === undefined
      ? ""
      : asString(message.content, "provider message content"),
    toolCalls,
    finishReason: toolCalls.length ? "tool_calls" : parseFinishReason(choice.finish_reason),
    usage: {
      inputTokens: asNonNegativeInteger(usage.prompt_tokens ?? 0, "provider input token usage"),
      outputTokens: asNonNegativeInteger(usage.completion_tokens ?? 0, "provider output token usage"),
    },
    providerState: {},
  };

}

function parseProviderToolCall(value: unknown): ToolCall {

  const call = asObject(value, "provider tool call");
  const fn = asObject(call.function, "provider tool function");
  const serializedArguments = asString(fn.arguments, "provider tool arguments");
  let parsedArguments: unknown;

  try {
    parsedArguments = JSON.parse(serializedArguments);
  } catch (error: unknown) {
    throw new TypeError("provider tool arguments are not valid JSON", {cause: error});
  }

  return {
    id: asString(call.id, "provider tool call id"),
    name: asString(fn.name, "provider tool name"),
    arguments: parseJsonObject(parsedArguments, "provider tool arguments"),
  };

}

function parseFinishReason(value: unknown): FinishReason {

  if (typeof value !== "string") {
    return "other";
  }

  return FINISH_REASONS[value] ?? "other";

}

const PROTECTED_FIELDS = ["model", "messages", "tools", "response_format"] as const;

const FINISH_REASONS: Readonly<Record<string, FinishReason>> = {
  stop: "stop",
  length: "length",
  content_filter: "content_filter",
};

const DEFAULT_TIMEOUT_MS = 60_000;
