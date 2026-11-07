import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

import {
  DocumentExtractorNode,
  LocIssuanceMapperNode,
  HumanVerificationNode,
  Tier1AliasCache,
  MockVectorDbService,
  MockLlmService,
  SamplePdfGenerator,
  validateSwiftMt700Invariants,
  buildBatchMappingPrompt,
  SWIFT_MT700_TARGET_SCHEMA
} from '../src/index.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const TEST_DIR = path.join(__dirname, '../temp_test_artifacts');

test.before(async () => {
  if (!fs.existsSync(TEST_DIR)) {
    fs.mkdirSync(TEST_DIR, { recursive: true });
  }
});

test('DocumentExtractorNode - rejects non-LOC commercial invoice via guardrail', async () => {
  const nonLocPath = path.join(TEST_DIR, 'test_non_loc.pdf');
  await SamplePdfGenerator.generateNonLocInvoicePdf(nonLocPath);

  const extractor = new DocumentExtractorNode();
  const result = await extractor.execute(nonLocPath);

  assert.equal(result.success, false);
  assert.equal(result.guardrailPassed, false);
  assert.match(result.error, /rejected by LOC Guardrail/i);
});

test('DocumentExtractorNode - extracts schema-less dynamic keys with source snippets', async () => {
  const locPath = path.join(TEST_DIR, 'test_loc_standard.pdf');
  await SamplePdfGenerator.generateStandardLocPdf(locPath);

  const extractor = new DocumentExtractorNode();
  const result = await extractor.execute(locPath);

  assert.equal(result.success, true);
  assert.equal(result.guardrailPassed, true);
  assert.ok(result.metadata.totalFieldsExtracted >= 10);

  // Verify source snippet contract
  for (const [key, field] of Object.entries(result.extractedFields)) {
    assert.ok(field.value !== undefined, `Key ${key} missing value`);
    assert.ok(field.source_snippet !== undefined, `Key ${key} missing source_snippet`);
    assert.ok(field.source_snippet.length > 0, `Key ${key} source_snippet must not be empty`);
  }
});

test('LocIssuanceMapperNode - executes two-tier retrieval and enforces SWIFT MT700 schema', async () => {
  const locPath = path.join(TEST_DIR, 'test_loc_standard.pdf');
  await SamplePdfGenerator.generateStandardLocPdf(locPath);

  const extractor = new DocumentExtractorNode();
  const extracted = await extractor.execute(locPath);

  const mapper = new LocIssuanceMapperNode();
  const result = await mapper.execute(extracted);

  assert.equal(result.success, true);
  assert.equal(result.validation.isValid, true);
  assert.equal(result.routing.destination, 'POST_ISSUANCE_API');
  assert.equal(result.mappedData.currency.value, 'USD');
  assert.equal(result.mappedData.amount.value, 1500000);
});

test('Batch Prompt & Single LLM Call Contract - groups unresolved fields into single LLM request', async () => {
  const customLlm = new MockLlmService();
  const mapper = new LocIssuanceMapperNode({ llmService: customLlm });

  // Extracted input where 2 fields are non-standard/ambiguous and need LLM resolution
  const sampleExtracted = {
    extractedFields: {
      documentary_credit_number: { value: 'LC-BKP-001', source_snippet: 'Credit Number LC-BKP-001' },
      date_of_issue: { value: '2026-10-15', source_snippet: 'Date of Issue 2026-10-15' },
      date_of_expiry: { value: '2027-04-15', source_snippet: 'Date of Expiry 2027-04-15' },
      place_of_expiry: { value: 'SINGAPORE', source_snippet: 'Place of Expiry SINGAPORE' },
      credit_amount: { value: '500000.00', source_snippet: 'Credit Amount 500000.00' },
      currency_code: { value: 'USD', source_snippet: 'Currency Code USD' },
      partial_shipment: { value: 'ALLOWED', source_snippet: 'Partial Shipment ALLOWED' },
      // Ambiguous keys requiring Vector + Batch LLM:
      ordering_customer: { value: 'ACME TRADING CORP', source_snippet: 'Ordering Customer ACME TRADING CORP' },
      favouring_party: { value: 'GLOBAL EXPORTS LTD', source_snippet: 'Favouring Party GLOBAL EXPORTS LTD' }
    }
  };

  const result = await mapper.execute(sampleExtracted);

  // Assert exactly 1 batch LLM call was made (NOT a per-field loop!)
  assert.equal(result.telemetry.llmCallsMade, 1);
  assert.ok(result.telemetry.lastPreparedPrompt !== null);

  const prompt = result.telemetry.lastPreparedPrompt;
  assert.equal(prompt.callType, 'BATCH_SEMANTIC_MAPPING');

  // Verify prompt structure:
  // 1. extracted_data is present
  assert.ok(prompt.payload.extracted_data.ordering_customer);
  // 2. already_matched_fields contains cached items
  assert.ok(prompt.payload.already_matched_fields.loc_number);
  assert.equal(prompt.payload.already_matched_fields.loc_number.source, 'TIER1_CACHE');
  // 3. fields_to_map contains unresolved fields with top3 candidates
  assert.ok(prompt.payload.fields_to_map.applicant_name);
  assert.ok(Array.isArray(prompt.payload.fields_to_map.applicant_name.top3Candidates));
  assert.ok(prompt.payload.fields_to_map.applicant_name.top3Candidates.length > 0);
  // 4. target_schema rules are present
  assert.ok(prompt.payload.target_schema.loc_number);

  // Verify response schema is specified
  assert.equal(prompt.expectedResponseSchema.type, 'object');
});

test('Deterministic Invariant Validation - detects logical constraint violations', () => {
  // Violation 1: Expiry before Issue date
  const invalidDates = {
    issue_date: { value: '2027-01-01' },
    expiry_date: { value: '2026-01-01' },
    loc_number: { value: 'LC123' },
    applicant_name: { value: 'Buyer Inc' },
    beneficiary_name: { value: 'Seller Ltd' },
    currency: { value: 'USD' },
    amount: { value: 1000 },
    expiry_place: { value: 'London' },
    partial_shipment: { value: 'ALLOWED' }
  };
  const res1 = validateSwiftMt700Invariants(invalidDates);
  assert.equal(res1.isValid, false);
  assert.ok(res1.errors.some(e => e.includes('Expiry date') && e.includes('cannot precede Issue date')));

  // Violation 2: Invalid Currency & Negative Amount
  const invalidCurrency = {
    ...invalidDates,
    expiry_date: { value: '2027-12-31' },
    currency: { value: 'DOLLARS' }, // Not 3-letter ISO
    amount: { value: -50 }
  };
  const res2 = validateSwiftMt700Invariants(invalidCurrency);
  assert.equal(res2.isValid, false);
  assert.ok(res2.errors.some(e => e.includes('ISO-4217')));
  assert.ok(res2.errors.some(e => e.includes('strictly greater than 0')));
});

test('HumanVerificationNode - Active Learning feedback loop propagates to Tier 1 and Tier 2', async () => {
  const cache = new Tier1AliasCache();
  const vectorDb = new MockVectorDbService();
  const hitl = new HumanVerificationNode({ aliasCache: cache, vectorDb });

  const dummyPayload = {
    docType: 'LOC_ISSUANCE',
    mappedData: {
      applicant_name: { value: null }
    }
  };

  // Prior to feedback: cache miss for obscure key
  assert.equal(cache.get('LOC_ISSUANCE', 'foreign_buyer_cust_tag'), null);

  // Human operator corrects
  const feedbackResult = await hitl.processHumanCorrection({
    mapperPayload: dummyPayload,
    corrections: {
      applicant_name: {
        value: 'ACME GLOBAL TRADING',
        originalExtractedKey: 'foreign_buyer_cust_tag',
        source_snippet: 'foreign buyer cust tag ACME GLOBAL'
      }
    },
    operatorId: 'operator_dan'
  });

  assert.equal(feedbackResult.learnedRules.length, 1);

  // Subsequent check: Tier 1 Cache HIT!
  const cached = cache.get('LOC_ISSUANCE', 'foreign_buyer_cust_tag');
  assert.equal(cached, 'applicant_name');
});
