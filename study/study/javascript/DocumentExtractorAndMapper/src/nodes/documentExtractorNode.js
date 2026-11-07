import { PdfTextExtractor } from '../utils/pdfTextExtractor.js';
import { defaultLlmService } from '../services/mockLlmService.js';

/**
 * Node 1: AI-Powered Document Extraction Node
 * 
 * Responsibilities:
 * 1. Ingests raw PDF (scanned or native text).
 * 2. Parses document layout & text with OCR fallback.
 * 3. Evaluates LOC Guardrail: blocks non-Trade Finance documents before consuming downstream tokens.
 * 4. Executes schema-less open-ended LLM extraction.
 * 5. Attaches verifiable verbatim `source_snippet` to every extracted value.
 */
export class DocumentExtractorNode {
  constructor(llmService = defaultLlmService) {
    this.llmService = llmService;
  }

  /**
   * Executes the Document Extraction Node pipeline.
   * @param {string|Buffer} pdfInput File path or buffer of the incoming PDF
   * @returns {Promise<{
   *   success: boolean,
   *   guardrailPassed: boolean,
   *   documentType?: string,
   *   extractedFields?: Record<string, { value: string, source_snippet: string }>,
   *   error?: string,
   *   metadata: Object
   * }>}
   */
  async execute(pdfInput) {
    const startTime = Date.now();

    // Step 1: Text & Layout Extraction from PDF
    const { rawText, pageCount, extractionMethod } = await PdfTextExtractor.extract(pdfInput);

    if (!rawText || rawText.trim().length === 0) {
      return {
        success: false,
        guardrailPassed: false,
        error: 'Failed to extract any readable text from the document (blank or unreadable PDF).',
        metadata: { pageCount, durationMs: Date.now() - startTime }
      };
    }

    // Step 2: Guardrail Evaluation
    const guardrail = await this.llmService.evaluateLocGuardrail(rawText);

    if (!guardrail.isLoc) {
      return {
        success: false,
        guardrailPassed: false,
        documentType: guardrail.detectedType,
        error: `Document rejected by LOC Guardrail: ${guardrail.reason}`,
        metadata: {
          confidence: guardrail.confidence,
          pageCount,
          extractionMethod,
          durationMs: Date.now() - startTime
        }
      };
    }

    // Step 3: Schema-Less Open-Ended Extraction
    const dynamicFields = await this.llmService.extractDynamicFields(rawText);

    return {
      success: true,
      guardrailPassed: true,
      documentType: guardrail.detectedType,
      extractedFields: dynamicFields,
      metadata: {
        totalFieldsExtracted: Object.keys(dynamicFields).length,
        guardrailConfidence: guardrail.confidence,
        pageCount,
        extractionMethod,
        durationMs: Date.now() - startTime
      }
    };
  }
}
