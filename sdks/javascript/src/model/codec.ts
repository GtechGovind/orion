import {type ZodType, z} from "zod";

import type {Json, JsonObject} from "./json.js";

export {z} from "zod";

/** Connects one runtime TypeScript value to JSON Schema and validated JSON. */
export interface JsonCodec<Value> {

  /** Draft-compatible schema sent to the Rust runtime and model provider. */
  readonly schema: JsonObject;

  /** Validates and decodes a JSON object into the application type. */
  decode(value: JsonObject): Value;

  /** Validates and encodes an application value as JSON. */
  encode(value: Value): Json;

  /** Validates JSON text and returns the configured application value. */
  decodeJson(value: string): Value;

}

/** Creates a JSON codec from a Zod 4 runtime schema. */
export function zodCodec<Value>(schema: ZodType<Value>): JsonCodec<Value> {

  const jsonSchema = requireJsonObject(z.toJSONSchema(schema), "Zod JSON Schema");

  return {
    schema: jsonSchema,
    decode: value => schema.parse(value),
    encode: value => requireJson(schema.parse(value), "encoded Zod value"),
    decodeJson: value => schema.parse(JSON.parse(value)),
  };

}

function requireJsonObject(value: unknown, context: string): JsonObject {

  const parsed = requireJson(value, context);
  if (!isJsonObject(parsed)) {
    throw new TypeError(`${context} must be an object`);
  }

  return parsed;

}

function isJsonObject(value: Json): value is JsonObject {

  return value !== null && typeof value === "object" && !Array.isArray(value);

}

function requireJson(value: unknown, context: string): Json {

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
    return value.map((item, index) => requireJson(item, `${context}[${index}]`));
  }

  if (typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, requireJson(item, `${context}.${key}`)]),
    );
  }

  throw new TypeError(`${context} is not JSON-compatible`);

}
