/**
 * Tier 1: Deterministic Exact Alias Cache
 * Simulates an in-memory Redis key-value store for O(1) instant alias lookups.
 * Bypasses Vector DB and LLM for previously verified exact or standardized keys.
 */

export class Tier1AliasCache {
  constructor() {
    // Structure: Map<docType, Map<normalizedKey, targetField>>
    this.store = new Map();
    this.statsData = { hits: 0, misses: 0 };
    this.initDefaultAliases();
  }

  normalizeKey(key) {
    return String(key || '')
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]/g, '_')
      .replace(/_+/g, '_')
      .replace(/^_|_$/g, '');
  }

  initDefaultAliases() {
    const issuanceDefaults = {
      'documentary_credit_number': 'loc_number',
      'doc_credit_no': 'loc_number',
      'field_20': 'loc_number',
      'date_of_issue': 'issue_date',
      'field_31c': 'issue_date',
      'date_of_expiry': 'expiry_date',
      'field_31d': 'expiry_date',
      'place_of_expiry': 'expiry_place',
      'applicant': 'applicant_name',
      'field_50': 'applicant_name',
      'beneficiary': 'beneficiary_name',
      'field_59': 'beneficiary_name',
      'currency_code': 'currency',
      'credit_amount': 'amount',
      'partial_shipment': 'partial_shipment',
      'partial_shipments': 'partial_shipment',
      'field_43p': 'partial_shipment',
      'latest_date_of_shipment': 'latest_shipment_date',
      'field_44c': 'latest_shipment_date',
      'amount_tolerance': 'tolerance_percentage',
      'field_39a': 'tolerance_percentage'
    };

    const issuanceMap = new Map();
    for (const [k, v] of Object.entries(issuanceDefaults)) {
      issuanceMap.set(this.normalizeKey(k), v);
    }
    this.store.set('LOC_ISSUANCE', issuanceMap);
    this.store.set('LOC_AMENDMENT', new Map());
    this.store.set('LOC_PRESENTATION', new Map());
  }

  get(docType, extractedKey) {
    const bucket = this.store.get(docType);
    if (!bucket) {
      this.statsData.misses++;
      return null;
    }
    const normalized = this.normalizeKey(extractedKey);
    const target = bucket.get(normalized);
    if (target) {
      this.statsData.hits++;
      return target;
    }
    this.statsData.misses++;
    return null;
  }

  set(docType, extractedKey, targetField) {
    if (!this.store.has(docType)) {
      this.store.set(docType, new Map());
    }
    const bucket = this.store.get(docType);
    bucket.set(this.normalizeKey(extractedKey), targetField);
  }

  getStats() {
    return { ...this.statsData };
  }

  resetStats() {
    this.statsData = { hits: 0, misses: 0 };
  }
}

export const defaultAliasCache = new Tier1AliasCache();
