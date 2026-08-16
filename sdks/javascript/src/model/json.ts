/** A recursively typed value representable by JSON. */
export type Json = null | boolean | number | string | readonly Json[] | JsonObject;

/** A JSON object with immutable values. */
export interface JsonObject {

  readonly [key: string]: Json;

}

/** A JSON Schema document accepted by Orion tools and structured output. */
export type JsonSchema = JsonObject;
