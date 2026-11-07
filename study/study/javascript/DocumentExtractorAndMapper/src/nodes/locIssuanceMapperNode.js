import { SWIFT_MT700_TARGET_SCHEMA, validateSwiftMt700Invariants } from '../config/swiftMt700Schema.js';
import { defaultAliasCache } from '../services/tier1AliasCache.js';
import { defaultVectorDb } from '../services/mockVectorDbService.js';
import { defaultLlmService } from '../services/mockLlmService.js';
import { buildBatchMappingPrompt } from '../services/llmInterface.js';
import { normalizeByType } from '../utils/normalizers.js';

/**
 * Node 2: Semantic Mapping Node (Specifically for LOC Issuance)
 * 
 * Responsibilities:
 * 1. Consumes dynamic key-value pairs from Node 1.
 * 2. Checks Tier 1 Alias Cache for instant O(1) deterministic matches.
 * 3. Identifies all unresolved target fields and queries Tier 2 Vector DB for their Top-3 candidates.
 * 4. Assembles a SINGLE BATCH PROMPT containing:
 *    - Full extracted document data
 *    - Already matched fields (cache/feedback)
 *    - Unresolved fields to map with their Top-3 candidates
 *    - Target schema rules and descriptions
 * 5. Calls the LLM interface in ONE batch request (or 0 requests if all fields came from cache).
 * 6. Merges matched fields and runs deterministic SWIFT MT700 invariant validations.
 */
export class LocIssuanceMapperNode {
  constructor({
    aliasCache = defaultAliasCache,
    vectorDb = defaultVectorDb,
    llmService = defaultLlmService,
    confidenceThreshold = 0.85
  } = {}) {
    this.aliasCache = aliasCache;
    this.vectorDb = vectorDb;
    this.llmService = llmService;
    this.confidenceThreshold = confidenceThreshold;
    this.docType = 'LOC_ISSUANCE';
  }

  /**
   * Executes semantic mapping for LOC Issuance in a single batch operation.
   * @param {{ extractedFields: Record<string, { value: string, source_snippet: string }> }} input
   */
  async execute(input) {
    const startTime = Date.now();
    const extractedFields = input?.extractedFields || {};

    const alreadyMatchedFields = {};
    const unconsumedExtractedKeys = new Set(Object.keys(extractedFields));
    let cacheHits = 0;

    // Step 1: Tier 1 Deterministic Cache Check
    for (const [extractedKey, fieldData] of Object.entries(extractedFields)) {
      const cachedTarget = this.aliasCache.get(this.docType, extractedKey);
      if (cachedTarget && SWIFT_MT700_TARGET_SCHEMA[cachedTarget]) {
        if (!alreadyMatchedFields[cachedTarget]) {
          cacheHits++;
          const targetDef = SWIFT_MT700_TARGET_SCHEMA[cachedTarget];
          const normalizedVal = normalizeByType(targetDef.dataType, cachedTarget, fieldData.value);

          alreadyMatchedFields[cachedTarget] = {
            value: normalizedVal,
            source_snippet: fieldData.source_snippet,
            originalKey: extractedKey,
            confidence: 0.99,
            source: 'TIER1_CACHE',
            reasoning: `Deterministic O(1) match from Tier 1 confirmed alias cache.`
          };
          unconsumedExtractedKeys.delete(extractedKey);
        }
      }
    }

    // Step 2: Collect Unresolved Target Fields and their Top-3 Vector Candidates
    const fieldsToMap = {};
    let vectorQueries = 0;

    for (const [targetField, fieldDef] of Object.entries(SWIFT_MT700_TARGET_SCHEMA)) {
      if (alreadyMatchedFields[targetField]) {
        continue; // Already resolved by Tier 1 Cache!
      }

      // Find top-3 candidate extracted fields for this target field
      const scoredCandidates = [];

      for (const extractedKey of unconsumedExtractedKeys) {
        const fieldData = extractedFields[extractedKey];

        vectorQueries++;
        const vectorMatches = this.vectorDb.query({
          queryText: extractedKey.replace(/_/g, ' '),
          topK: 5,
          filter: { doc_type: this.docType }
        });

        const matchForTarget = vectorMatches.find(m => m.targetField === targetField);
        if (matchForTarget) {
          scoredCandidates.push({
            extractedKey,
            value: fieldData.value,
            source_snippet: fieldData.source_snippet,
            score: matchForTarget.score,
            source: matchForTarget.isHumanFeedback ? 'TIER2_VECTOR_FEEDBACK' : 'TIER2_VECTOR_SCHEMA'
          });
        }
      }

      // Sort by similarity score descending and take Top-3
      scoredCandidates.sort((a, b) => b.score - a.score);
      const top3Candidates = scoredCandidates.slice(0, 3);

      fieldsToMap[targetField] = {
        swiftTag: fieldDef.swiftTag,
        description: fieldDef.description,
        dataType: fieldDef.dataType,
        required: fieldDef.required,
        top3Candidates
      };
    }

    // Step 3: Single Batch LLM Call for All Unresolved Fields
    let batchLlmResponse = { mapped_fields: {} };
    let preparedPrompt = null;
    let llmCallCount = 0;

    const fieldsToMapCount = Object.keys(fieldsToMap).length;
    if (fieldsToMapCount > 0) {
      // Assemble the single batch prompt payload
      preparedPrompt = buildBatchMappingPrompt({
        extractedData: extractedFields,
        alreadyMatchedFields,
        fieldsToMap,
        targetSchema: SWIFT_MT700_TARGET_SCHEMA
      });

      // Execute single batch LLM call
      llmCallCount = 1;
      batchLlmResponse = await this.llmService.completeBatchMapping(preparedPrompt);
    }

    // Step 4: Merge Cache Matches + Batch LLM Responses
    const mappedResult = {};
    const fieldConfidenceScores = [];

    for (const [targetField, fieldDef] of Object.entries(SWIFT_MT700_TARGET_SCHEMA)) {
      if (alreadyMatchedFields[targetField]) {
        const cached = alreadyMatchedFields[targetField];
        mappedResult[targetField] = {
          value: cached.value,
          source_snippet: cached.source_snippet,
          originalKey: cached.originalKey,
          confidence: cached.confidence,
          reasoning: cached.reasoning,
          source: cached.source
        };
        fieldConfidenceScores.push(cached.confidence);
      } else if (batchLlmResponse.mapped_fields && batchLlmResponse.mapped_fields[targetField]) {
        const llmField = batchLlmResponse.mapped_fields[targetField];
        mappedResult[targetField] = {
          value: llmField.value,
          source_snippet: llmField.source_snippet,
          originalKey: llmField.selected_candidate_key,
          confidence: llmField.confidence,
          reasoning: llmField.reasoning,
          source: 'BATCH_LLM'
        };
        fieldConfidenceScores.push(llmField.confidence);
      } else {
        mappedResult[targetField] = {
          value: null,
          source_snippet: null,
          originalKey: null,
          confidence: 0,
          reasoning: `Field unresolved: no match found by batch mapper.`,
          source: 'UNRESOLVED'
        };
        if (fieldDef.required) {
          fieldConfidenceScores.push(0);
        }
      }
    }

    // Step 5: Deterministic Business Invariants Validation
    const validation = validateSwiftMt700Invariants(mappedResult);

    // Step 6: Straight-Through Processing vs. Human Verification Routing
    const avgConfidence = fieldConfidenceScores.length > 0
      ? fieldConfidenceScores.reduce((a, b) => a + b, 0) / fieldConfidenceScores.length
      : 0;

    const qualifiesForStp = validation.isValid && avgConfidence >= this.confidenceThreshold;

    const routing = {
      destination: qualifiesForStp ? 'POST_ISSUANCE_API' : 'HUMAN_VERIFICATION_NODE',
      requiresHumanReview: !qualifiesForStp,
      reason: qualifiesForStp
        ? `Passed all schema validations and achieved high confidence (${(avgConfidence * 100).toFixed(1)}%). Ready for downstream API.`
        : (!validation.isValid 
            ? `Schema validation failed: ${validation.errors.join('; ')}`
            : `Average confidence (${(avgConfidence * 100).toFixed(1)}%) below STP threshold (${(this.confidenceThreshold * 100).toFixed(1)}%). Escalated to Human Verification Node.`)
    };

    return {
      success: true,
      docType: this.docType,
      mappedData: mappedResult,
      validation,
      routing,
      telemetry: {
        totalTargetFields: Object.keys(SWIFT_MT700_TARGET_SCHEMA).length,
        cacheHits,
        fieldsSentToBatchLlm: fieldsToMapCount,
        llmCallsMade: llmCallCount, // Exactly 1 (or 0 if all cached)
        vectorQueries,
        averageConfidence: parseFloat(avgConfidence.toFixed(3)),
        durationMs: Date.now() - startTime,
        lastPreparedPrompt: preparedPrompt
      }
    };
  }
}
