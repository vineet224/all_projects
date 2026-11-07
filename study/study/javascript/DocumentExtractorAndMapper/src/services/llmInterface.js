/**
 * LLM Interface and Prompt Contracts for Enterprise Document Mapping.
 * 
 * Defines the exact structure of prompts sent to LLM providers (OpenAI, Claude, Gemini)
 * and the expected structured response schemas, without binding to any specific SDK.
 */

/**
 * Builds the single batch prompt payload for semantic mapping.
 * Bundles extracted document data, already matched fields (from cache/feedback),
 * and target fields requiring resolution with their top-3 candidates.
 * 
 * @param {{
 *   extractedData: Record<string, { value: string, source_snippet: string }>,
 *   alreadyMatchedFields: Record<string, { value: any, source_snippet: string, originalKey: string, source: string }>,
 *   fieldsToMap: Record<string, {
 *     swiftTag: string,
 *     description: string,
 *     dataType: string,
 *     required: boolean,
 *     top3Candidates: Array<{ extractedKey: string, value: string, source_snippet: string, score: number, source: string }>
 *   }>,
 *   targetSchema: Record<string, any>
 * }} params
 * @returns {PreparedBatchMappingPrompt}
 */
export function buildBatchMappingPrompt({
  extractedData,
  alreadyMatchedFields,
  fieldsToMap,
  targetSchema
}) {
  const systemPrompt = `You are an expert Trade Finance Data Mapper specializing in SWIFT MT700 Letter of Credit documents.
Your task is to map dynamically extracted key-value pairs to our canonical TARGET SCHEMA in a single batch operation.

You are provided with:
1. 'extracted_data': All dynamic key-value pairs extracted from the source document.
2. 'already_matched_fields': Target fields already resolved deterministically via confirmed cache or historical feedback. Do NOT re-map or overwrite these.
3. 'fields_to_map': Target schema fields that still need resolution, along with their top-3 semantic candidate matches retrieved via vector search.
4. 'target_schema': The full technical definition of each target field, expected data types, and constraints.

RULES:
1. For each field in 'fields_to_map', review its top-3 candidates against the target field's business definition.
2. Select the candidate that is the true semantic match. If none of the candidates match, return null for that field.
3. Carry over the exact 'source_snippet' from the selected candidate. Do not modify or hallucinate snippets.
4. Normalize values to match expected types:
   - Dates: format as ISO 'YYYY-MM-DD'
   - Currency: 3-letter ISO-4217 code (e.g., 'USD', 'EUR')
   - Amount: numeric value
   - Partial shipment: 'ALLOWED' or 'NOT_ALLOWED'
5. Return ONLY a valid JSON object matching the requested output schema.`;

  const userPrompt = `Map the unresolved target fields in 'fields_to_map' using their top-3 candidates and document context.

INPUT PAYLOAD:
${JSON.stringify({
  extracted_data: extractedData,
  already_matched_fields: alreadyMatchedFields,
  fields_to_map: fieldsToMap,
  target_schema_rules: targetSchema
}, null, 2)}`;

  const responseJsonSchema = {
    type: 'object',
    required: ['mapped_fields'],
    properties: {
      mapped_fields: {
        type: 'object',
        description: 'Keyed by target_field name',
        additionalProperties: {
          type: 'object',
          properties: {
            selected_candidate_key: { type: ['string', 'null'] },
            value: { type: ['string', 'number', 'null'] },
            source_snippet: { type: ['string', 'null'] },
            confidence: { type: 'number', minimum: 0, maximum: 1 },
            reasoning: { type: 'string' }
          },
          required: ['selected_candidate_key', 'value', 'source_snippet', 'confidence', 'reasoning']
        }
      }
    }
  };

  return {
    callType: 'BATCH_SEMANTIC_MAPPING',
    timestamp: new Date().toISOString(),
    systemPrompt,
    userPrompt,
    payload: {
      extracted_data: extractedData,
      already_matched_fields: alreadyMatchedFields,
      fields_to_map: fieldsToMap,
      target_schema: targetSchema
    },
    expectedResponseSchema: responseJsonSchema
  };
}

/**
 * Base Abstract Interface for LLM Clients.
 * Any real integration (OpenAiClient, ClaudeClient, GeminiClient) can implement this.
 */
export class LlmClientInterface {
  /**
   * Evaluates document guardrail classifier.
   * @param {string} documentText
   * @returns {Promise<{ isLoc: boolean, confidence: number, detectedType: string, reason: string }>}
   */
  async evaluateLocGuardrail(documentText) {
    throw new Error('evaluateLocGuardrail must be implemented by subclass');
  }

  /**
   * Performs schema-less dynamic field extraction.
   * @param {string} documentText
   * @returns {Promise<Record<string, { value: string, source_snippet: string }>>}
   */
  async extractDynamicFields(documentText) {
    throw new Error('extractDynamicFields must be implemented by subclass');
  }

  /**
   * Executes batch semantic mapping for all unresolved fields in a single call.
   * @param {ReturnType<typeof buildBatchMappingPrompt>} preparedPrompt
   * @returns {Promise<{ mapped_fields: Record<string, { selected_candidate_key: string|null, value: any, source_snippet: string|null, confidence: number, reasoning: string }> }>}
   */
  async completeBatchMapping(preparedPrompt) {
    throw new Error('completeBatchMapping must be implemented by subclass');
  }
}
