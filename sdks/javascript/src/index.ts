/** TypeScript API for the in-process Orion Rust kernel. */
import { randomUUID } from "node:crypto";
import { createRequire } from "node:module";

interface NativeRunBinding {
  takeStep(): Record<string, any>;
  resume(result: Record<string, any>): Record<string, any>;
  cancel(): Record<string, any>;
  fail(error: Record<string, any>): Record<string, any>;
}
interface NativeModule {
  NativeRun: new (command: Record<string, any>) => NativeRunBinding;
}
const native = createRequire(import.meta.url)("./native.cjs") as NativeModule;

export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
export interface Usage { inputTokens: number; outputTokens: number }
export interface ModelProfile {
  streaming: "native" | "emulated" | "unsupported" | "unknown";
  toolCalling: "native" | "emulated" | "unsupported" | "unknown";
  structuredOutput: "native" | "emulated" | "unsupported" | "unknown";
  parallelToolCalls: "native" | "emulated" | "unsupported" | "unknown";
  maxContextTokens?: number;
}
export interface ToolCall { id: string; name: string; arguments: Json }
export interface ModelResponse {
  content?: string; toolCalls?: ToolCall[]; finishReason?: string; usage?: Partial<Usage>;
  providerState?: Record<string, Json>;
}
export interface ModelAdapter {
  readonly provider: string;
  profile(model: ModelRef): ModelProfile;
  complete(request: Record<string, any>, signal?: AbortSignal): Promise<ModelResponse>;
  close?(): Promise<void>;
}
export interface Tool {
  name: string;
  description: string;
  inputSchema: Record<string, Json>;
  execute(arguments_: Json, signal?: AbortSignal): Json | Promise<Json>;
}
export interface AgentOptions {
  id: string; name: string; instructions: string; model: string | ModelRef; tools?: Tool[];
  outputSchema?: Record<string, Json>; temperature?: number; maxOutputTokens?: number;
  providerOptions?: Record<string, Json>; maxTurns?: number;
}
export interface RunEvent { runId: string; sequence: number; type: string; data: Record<string, Json> }
export interface RunResult { runId: string; output: string; usage: Usage; turns: number; events: RunEvent[] }

export class OrionError extends Error { override name = "OrionError"; }

export class ModelRef {
  readonly provider: string;
  readonly model: string;
  constructor(provider: string, model: string) { this.provider = provider; this.model = model; }
  static parse(value: string): ModelRef {
    const index = value.indexOf(":");
    if (index <= 0 || index === value.length - 1) throw new TypeError("model reference must use provider:model notation");
    return new ModelRef(value.slice(0, index), value.slice(index + 1));
  }
}

export class Agent {
  readonly tools: Tool[];
  readonly options: AgentOptions;
  constructor(options: AgentOptions) { this.options = options; this.tools = options.tools ?? []; }
  toWire(): Record<string, any> {
    const model = typeof this.options.model === "string" ? ModelRef.parse(this.options.model) : this.options.model;
    return {
      id: this.options.id, name: this.options.name, instructions: this.options.instructions,
      model: { provider: model.provider, model: model.model },
      tools: this.tools.map((tool) => ({ name: tool.name, description: tool.description, input_schema: tool.inputSchema })),
      output_schema: this.options.outputSchema ?? null,
      model_settings: { temperature: this.options.temperature ?? null,
        max_output_tokens: this.options.maxOutputTokens ?? null,
        provider_options: this.options.providerOptions ?? {} },
      max_turns: this.options.maxTurns ?? 8,
    };
  }
}

export class ModelRegistry {
  readonly #adapters: Map<string, ModelAdapter>;
  constructor(adapters: ModelAdapter[]) {
    this.#adapters = new Map();
    for (const adapter of adapters) {
      if (this.#adapters.has(adapter.provider)) throw new OrionError(`duplicate model provider ${JSON.stringify(adapter.provider)}`);
      this.#adapters.set(adapter.provider, adapter);
    }
  }
  resolve(model: ModelRef): ModelAdapter {
    const adapter = this.#adapters.get(model.provider);
    if (!adapter) throw new OrionError(`no model adapter registered for provider ${JSON.stringify(model.provider)}`);
    return adapter;
  }
  async close(): Promise<void> { await Promise.all([...this.#adapters.values()].map((adapter) => adapter.close?.())); }
}

export class Runner {
  readonly models: ModelRegistry;
  constructor(models: ModelRegistry) {
    this.models = models;
  }
  async run(agent: Agent, input: string, options: { runId?: string; signal?: AbortSignal } = {}): Promise<RunResult> {
    let result: RunResult | undefined;
    for await (const item of this.runStream(agent, input, options)) if ("output" in item) result = item;
    if (!result) throw new OrionError("run ended without a result");
    return result;
  }
  async *runStream(agent: Agent, input: string, options: { runId?: string; signal?: AbortSignal } = {}): AsyncGenerator<RunEvent | RunResult> {
    const selectedModel = typeof agent.options.model === "string" ? ModelRef.parse(agent.options.model) : agent.options.model;
    const profile = this.models.resolve(selectedModel).profile(selectedModel);
    if (agent.tools.length && profile.toolCalling === "unsupported") throw new OrionError(`model ${selectedModel.provider}:${selectedModel.model} does not support tool calling`);
    if (agent.options.outputSchema && profile.structuredOutput === "unsupported") throw new OrionError(`model ${selectedModel.provider}:${selectedModel.model} does not support structured output`);
    const run = new native.NativeRun({ run_id: options.runId ?? `run-${randomUUID()}`, agent: agent.toWire(), input });
    let step = run.takeStep();
    const events: RunEvent[] = [];
    while (true) {
      for (const raw of step.events) {
        const { type, ...data } = raw.kind;
        const event = { runId: raw.run_id, sequence: raw.sequence, type, data } as RunEvent;
        events.push(event); yield event;
      }
      if (step.result) {
        const value = step.result;
        yield { runId: value.run_id, output: value.output,
          usage: { inputTokens: value.usage.input_tokens, outputTokens: value.usage.output_tokens },
          turns: value.turns, events }; return;
      }
      const effect = step.effect;
      if (!effect) throw new OrionError("run terminated without a successful result");
      if (options.signal?.aborted) { run.cancel(); throw options.signal.reason ?? new OrionError("run cancelled"); }
      try {
        const result = await this.executeEffect(agent, effect, options.signal);
        step = run.resume(result);
      } catch (error) {
        if (options.signal?.aborted) {
          run.cancel();
          throw options.signal.reason ?? new OrionError("run cancelled");
        }
        run.fail({ code: effect.type === "call_model" ? "provider" : "tool",
          message: String(error).slice(0, 4096), retryable: false, retry_after_ms: null });
        throw error;
      }
    }
  }
  private async executeEffect(agent: Agent, effect: Record<string, any>, signal?: AbortSignal): Promise<Record<string, any>> {
    if (effect.type === "call_model") {
      const response = await this.models.resolve(new ModelRef(effect.request.model.provider, effect.request.model.model)).complete(effect.request, signal);
      const calls = response.toolCalls ?? [];
      return { type: "model", value: { content: response.content ?? "",
        tool_calls: calls.map((call) => ({ id: call.id, name: call.name, arguments: call.arguments })),
        finish_reason: response.finishReason ?? (calls.length ? "tool_calls" : "stop"),
        usage: { input_tokens: response.usage?.inputTokens ?? 0, output_tokens: response.usage?.outputTokens ?? 0 },
        provider_state: response.providerState ?? {} } };
    }
    const tool = agent.tools.find((candidate) => candidate.name === effect.call.name);
    if (!tool) throw new OrionError(`model requested unregistered tool ${JSON.stringify(effect.call.name)}`);
    return { type: "tool", value: { content: await tool.execute(effect.call.arguments, signal) } };
  }
}

export class OpenAICompatibleAdapter implements ModelAdapter {
  readonly provider: string;
  readonly options: { provider?: string; apiKey?: string; baseUrl?: string };
  constructor(options: { provider?: string; apiKey?: string; baseUrl?: string } = {}) {
    this.options = options; this.provider = options.provider ?? "openai";
  }
  profile(_model: ModelRef): ModelProfile {
    return { streaming: "unsupported", toolCalling: "native", structuredOutput: "native",
      parallelToolCalls: "unknown" };
  }
  async complete(request: Record<string, any>, signal?: AbortSignal): Promise<ModelResponse> {
    const body: Record<string, any> = { model: request.model.model,
      messages: request.messages.map((message: any) => ({ role: message.role, content: message.content,
        ...(message.tool_call_id ? { tool_call_id: message.tool_call_id } : {}),
        ...(message.tool_calls?.length ? { tool_calls: message.tool_calls.map((call: any) => ({
          id: call.id, type: "function", function: { name: call.name, arguments: JSON.stringify(call.arguments) },
        })) } : {}) })) };
    if (request.tools.length) body.tools = request.tools.map((tool: any) => ({ type: "function", function: {
      name: tool.name, description: tool.description, parameters: tool.input_schema } }));
    if (request.settings.temperature != null) body.temperature = request.settings.temperature;
    if (request.settings.max_output_tokens != null) body.max_tokens = request.settings.max_output_tokens;
    if (request.output_schema) body.response_format = { type: "json_schema",
      json_schema: { name: "orion_output", schema: request.output_schema, strict: true } };
    const providerOptions = request.settings.provider_options?.[this.provider] ?? {};
    const protectedFields = ["model", "messages", "tools", "response_format"].filter((key) => key in providerOptions);
    if (protectedFields.length) throw new OrionError(`provider options cannot override protected fields: ${protectedFields.join(", ")}`);
    Object.assign(body, providerOptions);
    const apiKey = this.options.apiKey ?? process.env.OPENAI_API_KEY;
    const response = await fetch(`${(this.options.baseUrl ?? "https://api.openai.com/v1").replace(/\/$/, "")}/chat/completions`, {
      method: "POST", headers: { "content-type": "application/json", ...(apiKey ? { authorization: `Bearer ${apiKey}` } : {}) },
      body: JSON.stringify(body), ...(signal ? { signal } : {}),
    });
    if (!response.ok) throw new OrionError(`model provider returned HTTP ${response.status}: ${(await response.text()).slice(0, 4096)}`);
    const data: any = await response.json(), choice = data.choices[0], message = choice.message;
    const toolCalls = (message.tool_calls ?? []).map((call: any) => ({ id: call.id, name: call.function.name,
      arguments: JSON.parse(call.function.arguments) }));
    return { content: message.content ?? "", toolCalls, finishReason: toolCalls.length ? "tool_calls" : normalizeFinish(choice.finish_reason),
      usage: { inputTokens: data.usage?.prompt_tokens ?? 0, outputTokens: data.usage?.completion_tokens ?? 0 } };
  }
}

function normalizeFinish(value: string): string {
  return ({ stop: "stop", length: "length", content_filter: "content_filter" } as Record<string, string>)[value] ?? "other";
}
