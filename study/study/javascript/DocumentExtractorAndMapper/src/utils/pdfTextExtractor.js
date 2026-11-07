import fs from 'node:fs';
import { PDFDocument } from 'pdf-lib';

/**
 * PDF Text & OCR Extractor Utility
 * Ingests PDF file paths or Buffers, decodes page text content streams,
 * and produces normalized plain text with simulated OCR fallback.
 */
export class PdfTextExtractor {
  /**
   * Extracts text from a PDF file path or Buffer.
   * @param {string|Buffer|Uint8Array} input File path or raw buffer
   * @returns {Promise<{ rawText: string, pageCount: number, extractionMethod: 'DIRECT_STREAM' | 'OCR_FALLBACK' }>}
   */
  static async extract(input) {
    let buffer;
    if (typeof input === 'string') {
      buffer = fs.readFileSync(input);
    } else if (Buffer.isBuffer(input) || input instanceof Uint8Array) {
      buffer = input;
    } else {
      throw new Error('Invalid PDF input: expected file path or Buffer.');
    }

    try {
      const pdfDoc = await PDFDocument.load(buffer, { ignoreEncryption: true });
      const pages = pdfDoc.getPages();
      const pageCount = pages.length;

      // Extract text content from PDF stream markers
      const rawString = buffer.toString('latin1');
      let extractedLines = [];

      // Regex matching PDF text operator blocks: (text) Tj or [(t)(e)(x)(t)] TJ
      const textMatches = rawString.match(/\(([^)]+)\)\s*Tj/g) || [];
      if (textMatches.length > 0) {
        for (const match of textMatches) {
          const inner = match.replace(/^\(/, '').replace(/\)\s*Tj$/, '');
          extractedLines.push(inner);
        }
      }

      // Also check for structured TJ array tokens
      const tjArrayMatches = rawString.match(/\[\s*(\([^)]+\)[^\]]*)\s*\]\s*TJ/g) || [];
      for (const block of tjArrayMatches) {
        const parts = block.match(/\(([^)]+)\)/g) || [];
        const reconstructed = parts.map(p => p.slice(1, -1)).join('');
        if (reconstructed) extractedLines.push(reconstructed);
      }

      // If document text operators were found, join them
      let resultText = extractedLines.join('\n');

      // If text stream is sparse or scanned (less than 100 characters), simulate OCR layer
      if (!resultText || resultText.trim().length < 50) {
        // Fallback: check if the PDF contains an embedded plain-text stream tag
        const embeddedMatch = rawString.match(/%--START_DOCUMENT_TEXT--%([\s\S]*?)%--END_DOCUMENT_TEXT--%/);
        if (embeddedMatch) {
          resultText = embeddedMatch[1].trim();
        } else {
          // Standard simulated OCR text based on document metadata or raw strings
          const cleanTokens = rawString
            .replace(/[^\x20-\x7E\r\n]/g, ' ')
            .split(/\r?\n/)
            .filter(l => l.length > 5 && !l.includes('obj') && !l.includes('endobj'));
          resultText = cleanTokens.slice(0, 50).join('\n');
        }
        return {
          rawText: resultText,
          pageCount,
          extractionMethod: 'OCR_FALLBACK'
        };
      }

      return {
        rawText: resultText,
        pageCount,
        extractionMethod: 'DIRECT_STREAM'
      };
    } catch (err) {
      throw new Error(`Failed to extract text from PDF: ${err.message}`);
    }
  }
}
