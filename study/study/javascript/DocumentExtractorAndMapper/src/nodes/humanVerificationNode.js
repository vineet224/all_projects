import { defaultAliasCache } from '../services/tier1AliasCache.js';
import { defaultVectorDb } from '../services/mockVectorDbService.js';
import { validateSwiftMt700Invariants } from '../config/swiftMt700Schema.js';

/**
 * Node 5: Human Verification Node & Active Learning Feedback Engine
 * 
 * Responsibilities:
 * 1. Receives low-confidence or validation-failed payloads from Mapper nodes.
 * 2. Allows human operators to inspect source snippets and override/supply missing values.
 * 3. Persists human corrections into Tier 1 Alias Cache and Tier 2 Vector Memory Store.
 * 4. Re-validates the finalized payload and forwards to downstream banking API.
 */
export class HumanVerificationNode {
  constructor({
    aliasCache = defaultAliasCache,
    vectorDb = defaultVectorDb
  } = {}) {
    this.aliasCache = aliasCache;
    this.vectorDb = vectorDb;
    this.auditLog = [];
  }

  /**
   * Simulates operator review and applying corrections.
   * @param {{
   *   mapperPayload: Object,
   *   corrections: Record<string, { value: any, originalExtractedKey?: string, source_snippet?: string }>,
   *   operatorId?: string
   * }} params
   */
  async processHumanCorrection({
    mapperPayload,
    corrections = {},
    operatorId = 'operator_sarah_tradefin_01'
  }) {
    const updatedMappedData = { ...mapperPayload.mappedData };
    const learnedRules = [];

    for (const [targetField, patch] of Object.entries(corrections)) {
      const prevEntry = updatedMappedData[targetField] || {};

      updatedMappedData[targetField] = {
        value: patch.value,
        source_snippet: patch.source_snippet || prevEntry.source_snippet || 'Verified by human operator',
        originalKey: patch.originalExtractedKey || prevEntry.originalKey || 'human_override',
        confidence: 1.0, // Human verified
        reasoning: `Manually verified and corrected by operator [${operatorId}]`
      };

      // Active Learning Feed-Forward: Update Tier 1 Cache & Tier 2 Vector Memory
      if (patch.originalExtractedKey) {
        const docType = mapperPayload.docType || 'LOC_ISSUANCE';

        // 1. Update Tier 1 Exact Alias Cache
        this.aliasCache.set(docType, patch.originalExtractedKey, targetField);

        // 2. Update Tier 2 Vector Store with Human-Verified Exemplar
        const feedbackId = `hitl_${docType}_${patch.originalExtractedKey}_to_${targetField}`;
        this.vectorDb.upsert({
          id: feedbackId,
          text: `${patch.originalExtractedKey.replace(/_/g, ' ')} maps to ${targetField}`,
          targetField,
          metadata: {
            doc_type: docType,
            operatorId,
            action: 'HUMAN_CORRECTION'
          },
          isHumanFeedback: true
        });

        learnedRules.push({
          extractedKey: patch.originalExtractedKey,
          targetField,
          docType
        });
      }

      this.auditLog.push({
        timestamp: new Date().toISOString(),
        operatorId,
        targetField,
        previousValue: prevEntry.value,
        correctedValue: patch.value,
        originalKey: patch.originalExtractedKey
      });
    }

    // Re-run deterministic invariants
    const validation = validateSwiftMt700Invariants(updatedMappedData);

    return {
      success: true,
      readyForDownstreamApi: validation.isValid,
      apiEndpoint: 'POST /api/v1/trade-finance/post_issuance_call',
      finalPayload: updatedMappedData,
      validation,
      learnedRules,
      auditLogEntriesCount: this.auditLog.length
    };
  }

  getAuditLog() {
    return this.auditLog;
  }
}

export const defaultHumanVerificationNode = new HumanVerificationNode();
