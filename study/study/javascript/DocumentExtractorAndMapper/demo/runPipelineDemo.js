import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  DocumentExtractorNode,
  LocIssuanceMapperNode,
  HumanVerificationNode,
  SamplePdfGenerator,
  defaultAliasCache,
  defaultVectorDb
} from '../src/index.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SAMPLES_DIR = path.join(__dirname, '../temp_samples');

function printHeader(title) {
  console.log('\n' + '='.repeat(80));
  console.log(`  ${title}`);
  console.log('='.repeat(80));
}

function printSection(title) {
  console.log(`\n--- [${title}] ---`);
}

async function run() {
  console.log(`
================================================================================
   ENTERPRISE TRADE FINANCE DOCUMENT EXTRACTION & SEMANTIC MAPPING ENGINE
   Neuro-Symbolic Pipeline with Active Learning Human-in-the-Loop Feedback
================================================================================
  `);

  if (!fs.existsSync(SAMPLES_DIR)) {
    fs.mkdirSync(SAMPLES_DIR, { recursive: true });
  }

  const standardPdf = path.join(SAMPLES_DIR, 'sample_loc_issuance_standard.pdf');
  const ambiguousPdf = path.join(SAMPLES_DIR, 'sample_loc_issuance_ambiguous.pdf');
  const nonLocPdf = path.join(SAMPLES_DIR, 'sample_non_loc_invoice.pdf');

  console.log('Generating realistic Trade Finance sample PDFs...');
  await SamplePdfGenerator.generateStandardLocPdf(standardPdf);
  await SamplePdfGenerator.generateAmbiguousLocPdf(ambiguousPdf);
  await SamplePdfGenerator.generateNonLocInvoicePdf(nonLocPdf);
  console.log(' Sample PDFs ready in ./temp_samples\n');

  const extractorNode = new DocumentExtractorNode();
  const mapperNode = new LocIssuanceMapperNode();
  const hitlNode = new HumanVerificationNode();

  // ==========================================================================
  // SCENARIO 1: Non-LOC Document Ingestion (LOC Guardrail Defense)
  // ==========================================================================
  printHeader('SCENARIO 1: Non-LOC Document Ingestion (LOC Guardrail Defense)');
  console.log('Ingesting Commercial Invoice: sample_non_loc_invoice.pdf');
  
  const guardrailRun = await extractorNode.execute(nonLocPdf);
  if (!guardrailRun.guardrailPassed) {
    console.log(` Guardrail Triggered! Result: BLOCKED`);
    console.log(`   Reason: ${guardrailRun.error}`);
    console.log(`   Confidence: ${guardrailRun.metadata.confidence}`);
    console.log(`   Tokens Saved: 100% of Node 2 and downstream API costs prevented.`);
  }

  // ==========================================================================
  // SCENARIO 2: Standard SWIFT MT700 (Straight-Through Processing - STP)
  // ==========================================================================
  printHeader('SCENARIO 2: Standard SWIFT MT700 Issuance (Straight-Through Processing)');
  console.log('Ingesting Standard MT700 Document: sample_loc_issuance_standard.pdf');

  // Node 1: Extractor
  printSection('Stage 1: Document Extractor Node (Schema-Less Extraction)');
  const extractionResult = await extractorNode.execute(standardPdf);
  console.log(` Guardrail: PASSED (${extractionResult.documentType})`);
  console.log(` Total Dynamic Fields Extracted: ${extractionResult.metadata.totalFieldsExtracted}`);
  console.log(' Sample Extracted Dynamic Key with Source Snippet:');
  const sampleKey = Object.keys(extractionResult.extractedFields)[0];
  console.log(JSON.stringify({ [sampleKey]: extractionResult.extractedFields[sampleKey] }, null, 2));

  // Node 2: Semantic Mapper
  printSection('Stage 2: LOC Issuance Semantic Mapper Node (Two-Tier Retrieval + Single Batch LLM)');
  const mappingResult = await mapperNode.execute(extractionResult);
  console.log(` Telemetry: Cache Hits = ${mappingResult.telemetry.cacheHits}, Vector Queries = ${mappingResult.telemetry.vectorQueries}`);
  console.log(` Batch LLM Telemetry: LLM Calls Made = ${mappingResult.telemetry.llmCallsMade} (Single Batch Request, NOT a per-field loop!)`);
  console.log(` Unresolved Fields Sent in Batch: ${mappingResult.telemetry.fieldsSentToBatchLlm}`);
  console.log(` Average Confidence: ${(mappingResult.telemetry.averageConfidence * 100).toFixed(1)}%`);
  console.log(` Schema Validation: ${mappingResult.validation.isValid ? 'PASSED (0 Errors)' : 'FAILED'}`);
  console.log(` Final Routing: ===> ${mappingResult.routing.destination}`);
  console.log(`   Reason: ${mappingResult.routing.reason}`);

  if (mappingResult.telemetry.lastPreparedPrompt) {
    console.log('\n--- Prepared Batch LLM Request Payload (Sent to LLM Interface) ---');
    console.log(`Call Type: ${mappingResult.telemetry.lastPreparedPrompt.callType}`);
    console.log(`Already Matched Fields (via Cache): [${Object.keys(mappingResult.telemetry.lastPreparedPrompt.payload.already_matched_fields).join(', ')}]`);
    console.log(`Fields Sent to Map with Top-3 Candidates: [${Object.keys(mappingResult.telemetry.lastPreparedPrompt.payload.fields_to_map).join(', ')}]`);
  }

  console.log('\n Sample Target Schema Output for Downstream API:');
  const summaryPayload = {};
  for (const [k, v] of Object.entries(mappingResult.mappedData)) {
    summaryPayload[k] = { value: v.value, source_snippet: v.source_snippet };
  }
  console.log(JSON.stringify(summaryPayload, null, 2));

  // ==========================================================================
  // SCENARIO 3: Ambiguous Legacy Bank LOC (Human-in-the-Loop Escalation)
  // ==========================================================================
  printHeader('SCENARIO 3: Ambiguous Legacy Bank Format (HITL Escalation)');
  console.log('Ingesting Legacy Bank Advice: sample_loc_issuance_ambiguous.pdf');
  console.log('Document contains ambiguous non-standard keys: "our_ref", "ordering_customer", "favouring_party"...');

  const ambExtraction = await extractorNode.execute(ambiguousPdf);
  const ambMapping = await mapperNode.execute(ambExtraction);

  console.log(` Extracted Keys: ${ambExtraction.metadata.totalFieldsExtracted}`);
  console.log(` Cache Hits: ${ambMapping.telemetry.cacheHits} | Vector Queries: ${ambMapping.telemetry.vectorQueries}`);
  console.log(` Routing Decision: ${ambMapping.routing.destination}`);
  console.log(`   Notice: Target field 'applicant_name' received ambiguous key 'ordering_customer' via Vector DB.`);

  // ==========================================================================
  // SCENARIO 4: Active Learning Feedback Loop Demonstration
  // ==========================================================================
  printHeader('SCENARIO 4: Active Learning Feedback Loop (Continuous Self-Improvement)');
  console.log('Human Operator reviews ambiguous document and explicitly verifies:');
  console.log('  "ordering_customer" ===> "applicant_name"');
  console.log('  "favouring_party"   ===> "beneficiary_name"');

  const hitlFeedback = await hitlNode.processHumanCorrection({
    mapperPayload: ambMapping,
    corrections: {
      applicant_name: {
        value: 'NORDIC PAPER CORP',
        originalExtractedKey: 'ordering_customer',
        source_snippet: 'Ordering Customer: NORDIC PAPER CORP'
      },
      beneficiary_name: {
        value: 'HONG KONG DRAGON TRADING',
        originalExtractedKey: 'favouring_party',
        source_snippet: 'Favouring Party: HONG KONG DRAGON TRADING'
      }
    },
    operatorId: 'operator_alex_tradefin'
  });

  console.log(`\n Human Verification Node Result:`);
  console.log(`   Rules Learned & Back-Propagated: ${hitlFeedback.learnedRules.length}`);
  console.log(`   Updated Tier 1 Cache + Injected Tier 2 Vector Exemplars.`);
  console.log(`   API Endpoint Ready: ${hitlFeedback.apiEndpoint}`);

  printSection('Re-running the Ambiguous Document after Feedback Ingestion');
  console.log('Re-executing sample_loc_issuance_ambiguous.pdf through the pipeline...');
  const reRunMapping = await mapperNode.execute(ambExtraction);

  console.log(` Previous Cache Hits: ${ambMapping.telemetry.cacheHits} ---> New Cache Hits: ${reRunMapping.telemetry.cacheHits}`);
  console.log(` Previous Confidence: ${(ambMapping.telemetry.averageConfidence * 100).toFixed(1)}% ---> New Confidence: ${(reRunMapping.telemetry.averageConfidence * 100).toFixed(1)}%`);
  console.log(` Routing Decision: ===> ${reRunMapping.routing.destination}`);
  console.log(`   ${reRunMapping.routing.reason}`);

  printHeader('PIPELINE DEMO COMPLETED SUCCESSFULLY');
}

run().catch(err => {
  console.error('Fatal Demo Error:', err);
  process.exit(1);
});
