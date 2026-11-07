import { SWIFT_MT700_TARGET_SCHEMA } from '../config/swiftMt700Schema.js';

/**
 * Mock Vector Database Service
 * Implements high-dimensional semantic vector indexing, cosine similarity scoring,
 * metadata filtering (by doc_type), and dynamic exemplar ingestion for active learning.
 */
export class MockVectorDbService {
  constructor() {
    this.vectors = [];
    this.initTargetSchemaVectors();
  }

  /**
   * Generates a deterministic normalized semantic feature vector for text.
   * Uses trade finance vocabulary, n-grams, and semantic clustering.
   * @param {string} text
   * @returns {number[]} 64-dimensional float vector
   */
  generateEmbedding(text) {
    const clean = String(text || '').toLowerCase().replace(/[^a-z0-9]/g, ' ');
    const tokens = clean.split(/\s+/).filter(Boolean);
    const dim = 64;
    const vector = new Float64Array(dim);

    // Domain concept clusters for vector affinity
    const conceptClusters = {
      identifier: ['number', 'no', 'ref', 'reference', 'credit', 'doc', 'documentary', 'id', 'tag', 'identifier', '20'],
      date: ['date', 'day', 'time', 'issue', 'issued', 'opening', 'calendar', '31c'],
      expiry: ['expiry', 'expire', 'expiration', 'valid', 'validity', 'till', 'until', 'counter', 'place', '31d'],
      party_applicant: ['applicant', 'buyer', 'importer', 'accountee', 'ordering', 'customer', 'opener', 'client', '50'],
      party_beneficiary: ['beneficiary', 'seller', 'exporter', 'favour', 'in_favour_of', 'receiver', 'payee', '59'],
      money: ['amount', 'sum', 'total', 'value', 'price', 'ccy', 'currency', 'usd', 'eur', 'gbp', '32b'],
      shipment: ['shipment', 'shipping', 'dispatch', 'loading', 'partial', 'latest', 'vessel', 'port', '43p', '44c'],
      tolerance: ['tolerance', 'plus', 'minus', 'percentage', 'pct', 'variance', 'range', '39a']
    };

    let clusterIdx = 0;
    for (const [clusterName, keywords] of Object.entries(conceptClusters)) {
      let weight = 0;
      for (const token of tokens) {
        if (keywords.includes(token)) weight += 2.0;
        else if (keywords.some(k => token.includes(k) || k.includes(token))) weight += 1.0;
      }
      const baseSlot = clusterIdx * 8;
      for (let j = 0; j < 8; j++) {
        vector[baseSlot + j] = weight * (1 + 0.1 * j);
      }
      clusterIdx++;
    }

    // Token hash dispersion to avoid flat zeros
    for (const token of tokens) {
      let hash = 0;
      for (let i = 0; i < token.length; i++) {
        hash = (hash << 5) - hash + token.charCodeAt(i);
        hash |= 0;
      }
      const slot = Math.abs(hash) % dim;
      vector[slot] += 1.0;
    }

    // Normalize L2 norm
    let sumSq = 0;
    for (let i = 0; i < dim; i++) sumSq += vector[i] * vector[i];
    const norm = Math.sqrt(sumSq) || 1.0;
    for (let i = 0; i < dim; i++) vector[i] /= norm;

    return Array.from(vector);
  }

  /**
   * Calculates cosine similarity between two unit vectors.
   */
  cosineSimilarity(vA, vB) {
    if (!vA || !vB || vA.length !== vB.length) return 0;
    let dot = 0;
    for (let i = 0; i < vA.length; i++) dot += vA[i] * vB[i];
    return Math.max(0, Math.min(1, dot));
  }

  /**
   * Initializes the vector database with target schema attributes and domain synonyms.
   */
  initTargetSchemaVectors() {
    for (const [fieldName, fieldDef] of Object.entries(SWIFT_MT700_TARGET_SCHEMA)) {
      // Primary field definition entry
      const primaryText = `${fieldName}: ${fieldDef.description} (SWIFT Tag ${fieldDef.swiftTag})`;
      this.upsert({
        id: `schema_loc_issuance_${fieldName}`,
        text: primaryText,
        targetField: fieldName,
        metadata: {
          doc_type: 'LOC_ISSUANCE',
          swiftTag: fieldDef.swiftTag,
          dataType: fieldDef.dataType,
          isSchemaField: true
        }
      });

      // Alias exemplar entries
      for (const alias of fieldDef.aliases || []) {
        this.upsert({
          id: `alias_loc_issuance_${fieldName}_${alias}`,
          text: alias.replace(/_/g, ' '),
          targetField: fieldName,
          metadata: {
            doc_type: 'LOC_ISSUANCE',
            swiftTag: fieldDef.swiftTag,
            dataType: fieldDef.dataType,
            isAlias: true
          }
        });
      }
    }
  }

  /**
   * Upserts a document/exemplar into the vector collection.
   */
  upsert({ id, text, targetField, metadata = {}, isHumanFeedback = false }) {
    const embedding = this.generateEmbedding(text);
    const existingIndex = this.vectors.findIndex(v => v.id === id);
    const record = {
      id,
      text,
      targetField,
      embedding,
      metadata,
      isHumanFeedback,
      boost: isHumanFeedback ? 1.3 : 1.0,
      timestamp: new Date().toISOString()
    };

    if (existingIndex >= 0) {
      this.vectors[existingIndex] = record;
    } else {
      this.vectors.push(record);
    }
    return record;
  }

  /**
   * Queries the vector index with metadata filtering.
   * @param {{ queryText: string, topK?: number, filter?: Object }} params
   * @returns {Array<{ targetField: string, score: number, matchedText: string, metadata: Object, isHumanFeedback: boolean }>}
   */
  query({ queryText, topK = 3, filter = {} }) {
    const queryEmbedding = this.generateEmbedding(queryText);
    const scored = [];

    for (const item of this.vectors) {
      // Apply metadata filter (e.g., doc_type = "LOC_ISSUANCE")
      if (filter.doc_type && item.metadata.doc_type !== filter.doc_type) {
        continue;
      }

      const rawSim = this.cosineSimilarity(queryEmbedding, item.embedding);
      const score = Math.min(1.0, rawSim * (item.boost || 1.0));

      scored.push({
        targetField: item.targetField,
        score: parseFloat(score.toFixed(4)),
        matchedText: item.text,
        metadata: item.metadata,
        isHumanFeedback: !!item.isHumanFeedback
      });
    }

    // Sort descending by score
    scored.sort((a, b) => b.score - a.score);

    // Group by unique targetField to return distinct top target options
    const uniqueTargets = [];
    const seen = new Set();
    for (const match of scored) {
      if (!seen.has(match.targetField)) {
        seen.add(match.targetField);
        uniqueTargets.push(match);
      }
      if (uniqueTargets.length >= topK) break;
    }

    return uniqueTargets;
  }

  getRecordCount() {
    return this.vectors.length;
  }
}

export const defaultVectorDb = new MockVectorDbService();
