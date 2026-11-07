import { LlmClientInterface, buildBatchMappingPrompt } from './llmInterface.js';

/**
 * Mock LLM Service
 * Implements LlmClientInterface without connecting to third-party APIs.
 * Prepares exact production prompts, records prepared request payloads,
 * and returns structured mock response data conforming to expected schemas.
 */
export class MockLlmService extends LlmClientInterface {
  constructor({ staticBatchResponse = null } = {}) {
    super();
    this.callLog = [];
    this.staticBatchResponse = staticBatchResponse;
    this.lastBatchPrompt = null;
  }

  /**
   * Sets a static mock response to return for batch mapping.
   * Useful for unit testing custom scenarios.
   */
  setStaticBatchResponse(response) {
    this.staticBatchResponse = response;
  }

  /**
   * Clears static mock override.
   */
  clearStaticBatchResponse() {
    this.staticBatchResponse = null;
  }

  /**
   * GUARDRAIL: Fast LLM classifier to verify if document is a valid Letter of Credit instrument.
   * @param {string} documentText Raw extracted text from PDF/OCR
   * @returns {Promise<{ isLoc: boolean, confidence: number, detectedType: string, reason: string }>}
   */
  async evaluateLocGuardrail(documentText) {
    const textLower = String(documentText || '').toLowerCase();
    
    // Trade finance signature tokens
    const locKeywords = [
      'documentary credit',
      'letter of credit',
      'irrevocable documentary',
      'swift mt700',
      'ucp 600',
      'ucp latest version',
      'issuing bank',
      'advising bank',
      'applicant',
      'beneficiary',
      'credit number',
      'lc no'
    ];

    let hits = 0;
    const matched = [];
    for (const kw of locKeywords) {
      if (textLower.includes(kw)) {
        hits++;
        matched.push(kw);
      }
    }

    const isLoc = hits >= 2;
    const confidence = isLoc ? Math.min(0.99, 0.70 + (hits * 0.05)) : 0.15;
    
    const result = {
      isLoc,
      confidence: parseFloat(confidence.toFixed(2)),
      detectedType: isLoc ? 'LETTER_OF_CREDIT_MT700' : 'NON_LOC_DOCUMENT',
      reason: isLoc 
        ? `Document contains ${hits} trade finance indicators (${matched.slice(0, 3).join(', ')}). Passes LOC guardrail.`
        : 'Document does not contain standard Letter of Credit markers or SWIFT terminology.'
    };

    this.callLog.push({
      step: 'GUARDRAIL_CLASSIFIER',
      timestamp: new Date().toISOString(),
      result
    });

    return result;
  }

  /**
   * NODE 1: Schema-less Dynamic Extractor
   * Discovers all key-value pairs without knowing target schema,
   * strictly attaching a 5-7 word verbatim source snippet to every value.
   * @param {string} documentText Raw document text
   * @returns {Promise<Record<string, { value: string, source_snippet: string }>>}
   */
  async extractDynamicFields(documentText) {
    const lines = documentText.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
    const extracted = {};

    const findSnippet = (needle, lineIndex) => {
      const line = lines[lineIndex] || '';
      const words = line.split(/\s+/);
      if (words.length <= 7) return line;
      return words.slice(0, 7).join(' ');
    };

    // Pattern recognition for standard trade finance label-value formats
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      // Format: "Key: Value" or "Field XX [Label]: Value"
      const colonMatch = line.match(/^([^:]{2,35}):\s*(.+)$/);
      if (colonMatch) {
        const rawKey = colonMatch[1].trim();
        const rawVal = colonMatch[2].trim();
        const snakeKey = rawKey.toLowerCase().replace(/[^a-z0-9]/g, '_').replace(/_+/g, '_').replace(/^_|_$/g, '');

        if (snakeKey && rawVal) {
          extracted[snakeKey] = {
            value: rawVal,
            source_snippet: findSnippet(rawVal, i)
          };
        }
        continue;
      }

      // Format: SWIFT MT700 tags like ":20:LC-987654" or ":31C:261011"
      const swiftMatch = line.match(/^:([0-9]{2}[A-Z]?):\s*(.+)$/);
      if (swiftMatch) {
        const tag = swiftMatch[1].trim();
        const rawVal = swiftMatch[2].trim();
        extracted[`swift_tag_${tag.toLowerCase()}`] = {
          value: rawVal,
          source_snippet: findSnippet(rawVal, i)
        };
      }
    }

    this.callLog.push({
      step: 'DYNAMIC_SCHEMA_LESS_EXTRACTION',
      timestamp: new Date().toISOString(),
      extractedKeysCount: Object.keys(extracted).length
    });

    return extracted;
  }

  /**
   * NODE 2 (BATCH): Executes batch semantic mapping for all unresolved fields in a SINGLE call.
   * Receives the prepared batch prompt and returns structured mock response data.
   * 
   * @param {ReturnType<typeof buildBatchMappingPrompt>} preparedPrompt
   * @returns {Promise<{ mapped_fields: Record<string, { selected_candidate_key: string|null, value: any, source_snippet: string|null, confidence: number, reasoning: string }> }>}
   */
  async completeBatchMapping(preparedPrompt) {
    this.lastBatchPrompt = preparedPrompt;

    // If static mock override is specified, use it directly
    if (this.staticBatchResponse) {
      this.callLog.push({
        step: 'BATCH_SEMANTIC_MAPPING',
        timestamp: new Date().toISOString(),
        promptType: preparedPrompt.callType,
        fieldsToMapCount: Object.keys(preparedPrompt.payload?.fields_to_map || {}).length,
        responseSource: 'STATIC_OVERRIDE'
      });
      return this.staticBatchResponse;
    }

    // Default static mock resolution conforming to the prompt contract
    const fieldsToMap = preparedPrompt?.payload?.fields_to_map || {};
    const mapped_fields = {};

    for (const [targetField, fieldInfo] of Object.entries(fieldsToMap)) {
      const candidates = fieldInfo.top3Candidates || [];
      if (candidates.length === 0) {
        mapped_fields[targetField] = {
          selected_candidate_key: null,
          value: null,
          source_snippet: null,
          confidence: 0,
          reasoning: `No candidates available for '${targetField}'.`
        };
        continue;
      }

      // Best candidate
      const best = candidates[0];

      if (best.score < 0.40) {
        mapped_fields[targetField] = {
          selected_candidate_key: null,
          value: null,
          source_snippet: null,
          confidence: 0,
          reasoning: `Top candidate '${best.extractedKey}' had low similarity (${best.score}). Rejected.`
        };
        continue;
      }

      // Type normalization
      let normalizedValue = best.value;
      if (fieldInfo.dataType === 'date') {
        normalizedValue = this.normalizeDate(best.value);
      } else if (fieldInfo.dataType === 'number') {
        normalizedValue = this.normalizeNumber(best.value);
      } else if (fieldInfo.dataType === 'currency') {
        normalizedValue = this.normalizeCurrency(best.value);
      } else if (fieldInfo.dataType === 'enum' && targetField === 'partial_shipment') {
        normalizedValue = this.normalizePartialShipment(best.value);
      }

      mapped_fields[targetField] = {
        selected_candidate_key: best.extractedKey,
        value: normalizedValue,
        source_snippet: best.source_snippet,
        confidence: best.score,
        reasoning: `Batch-mapped candidate '${best.extractedKey}' to '${targetField}' with confidence score ${best.score}.`
      };
    }

    const response = { mapped_fields };

    this.callLog.push({
      step: 'BATCH_SEMANTIC_MAPPING',
      timestamp: new Date().toISOString(),
      promptType: preparedPrompt.callType,
      fieldsToMapCount: Object.keys(fieldsToMap).length,
      responseSource: 'STATIC_CONFORMING_MOCK'
    });

    return response;
  }

  /**
   * Backwards-compatibility helper for per-field resolution if needed.
   */
  async resolveCandidateForTargetField({ targetField, fieldDef, candidates }) {
    if (!candidates || candidates.length === 0) {
      return {
        selected: null,
        reasoning: `No candidate fields were found for target '${targetField}'`
      };
    }

    const best = candidates[0];
    if (best.score < 0.40) {
      return {
        selected: null,
        reasoning: `Top candidate '${best.key || best.extractedKey}' has low similarity (${best.score}) for '${targetField}'. Rejected.`
      };
    }

    let normalizedValue = best.value;
    if (fieldDef.dataType === 'date') normalizedValue = this.normalizeDate(best.value);
    else if (fieldDef.dataType === 'number') normalizedValue = this.normalizeNumber(best.value);
    else if (fieldDef.dataType === 'currency') normalizedValue = this.normalizeCurrency(best.value);
    else if (fieldDef.dataType === 'enum' && targetField === 'partial_shipment') normalizedValue = this.normalizePartialShipment(best.value);

    return {
      selected: {
        value: normalizedValue,
        source_snippet: best.source_snippet,
        originalKey: best.key || best.extractedKey,
        confidence: best.score
      },
      reasoning: `Mapped candidate '${best.key || best.extractedKey}' to '${targetField}' with confidence score ${best.score}.`
    };
  }

  normalizeDate(str) {
    if (!str) return null;
    const clean = String(str).trim();
    if (/^\d{4}-\d{2}-\d{2}$/.test(clean)) return clean;
    if (/^\d{6}$/.test(clean)) {
      const yy = clean.substring(0, 2);
      const mm = clean.substring(2, 4);
      const dd = clean.substring(4, 6);
      return `20${yy}-${mm}-${dd}`;
    }
    const parsed = new Date(clean);
    if (!isNaN(parsed.getTime())) {
      return parsed.toISOString().split('T')[0];
    }
    return clean;
  }

  normalizeNumber(str) {
    if (typeof str === 'number') return str;
    const cleaned = String(str || '').replace(/[^0-9.]/g, '');
    const num = parseFloat(cleaned);
    return isNaN(num) ? str : num;
  }

  normalizeCurrency(str) {
    const match = String(str || '').toUpperCase().match(/\b(USD|EUR|GBP|JPY|SGD|CHF|AED|CNY)\b/);
    return match ? match[1] : String(str).trim().toUpperCase();
  }

  normalizePartialShipment(str) {
    const lower = String(str || '').toLowerCase();
    if (lower.includes('allowed') || lower.includes('permitted') || lower.includes('yes') || lower.includes('true')) {
      return 'ALLOWED';
    }
    if (lower.includes('not') || lower.includes('prohibited') || lower.includes('no') || lower.includes('false')) {
      return 'NOT_ALLOWED';
    }
    return 'ALLOWED';
  }

  getCallLogs() {
    return this.callLog;
  }

  getLastBatchPrompt() {
    return this.lastBatchPrompt;
  }
}

export const defaultLlmService = new MockLlmService();
