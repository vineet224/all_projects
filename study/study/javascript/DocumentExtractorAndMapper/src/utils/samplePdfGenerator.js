import fs from 'node:fs';
import path from 'node:path';
import { PDFDocument, StandardFonts, rgb } from 'pdf-lib';

/**
 * Utility to generate realistic Trade Finance test PDFs using pdf-lib.
 */
export class SamplePdfGenerator {
  /**
   * Helper to write lines into a PDF and embed document stream text.
   */
  static async createPdf(lines, outputPath) {
    const pdfDoc = await PDFDocument.create();
    const page = pdfDoc.addPage([600, 800]);
    const font = await pdfDoc.embedFont(StandardFonts.Helvetica);
    const boldFont = await pdfDoc.embedFont(StandardFonts.HelveticaBold);

    let y = 750;
    const lineHeight = 18;

    for (const line of lines) {
      if (line.startsWith('###')) {
        y -= 10;
        page.drawText(line.replace('###', '').trim(), {
          x: 50,
          y,
          size: 14,
          font: boldFont,
          color: rgb(0.1, 0.2, 0.4)
        });
        y -= 6;
      } else if (line.startsWith('---')) {
        y -= 8;
        page.drawLine({
          start: { x: 50, y },
          end: { x: 550, y },
          thickness: 1,
          color: rgb(0.7, 0.7, 0.7)
        });
        y -= 8;
      } else {
        const isHeader = line.includes(':') && line.split(':')[0].length < 30;
        page.drawText(line, {
          x: 50,
          y,
          size: 10,
          font: isHeader ? font : font,
          color: rgb(0.15, 0.15, 0.15)
        });
      }
      y -= lineHeight;
    }

    const rawPdfBytes = await pdfDoc.save();

    // Embed verifiable plain-text metadata stream for OCR/parser fidelity
    const textStream = `\n%--START_DOCUMENT_TEXT--%\n${lines.join('\n')}\n%--END_DOCUMENT_TEXT--%\n`;
    const finalBuffer = Buffer.concat([Buffer.from(rawPdfBytes), Buffer.from(textStream, 'utf8')]);

    const dir = path.dirname(outputPath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }

    fs.writeFileSync(outputPath, finalBuffer);
    return outputPath;
  }

  /**
   * Generates standard SWIFT MT700 Issuance PDF
   */
  static async generateStandardLocPdf(outputPath) {
    const lines = [
      '### SWIFT MT700 - ISSUE OF A DOCUMENTARY CREDIT',
      '---',
      'Documentary Credit: Irrevocable Documentary Credit subject to UCP 600',
      'Documentary Credit Number: LC987654321',
      'Date of Issue: 2026-10-15',
      'Date of Expiry: 2027-04-15',
      'Place of Expiry: SINGAPORE',
      'Applicant: GLOBAL LOGISTICS PTE LTD',
      'Applicant Address: 12 Marina Boulevard, Singapore 018982',
      'Beneficiary: SHANGHAI PACIFIC TEXTILES LTD',
      'Currency Code: USD',
      'Credit Amount: 1500000.00',
      'Partial Shipments: ALLOWED',
      'Latest Date of Shipment: 2027-03-31',
      'Percentage Credit Amount Tolerance: 5',
      'Issuing Bank: DBS BANK LTD SINGAPORE',
      'Advising Bank: BANK OF CHINA SHANGHAI'
    ];
    return this.createPdf(lines, outputPath);
  }

  /**
   * Generates Ambiguous LOC Issuance PDF with non-standard banking labels
   */
  static async generateAmbiguousLocPdf(outputPath) {
    const lines = [
      '### BANK COMMERCIAL ADVICE - LETTER OF CREDIT ISSUANCE',
      '---',
      'Document Type: Irrevocable Commercial Letter of Credit (UCP Latest)',
      'Our Ref: LC-AMB-2026-88',
      'Credit Opening Date: 2026-11-01',
      'Valid Until: 2027-05-01',
      'Expiring At: LONDON COUNTER',
      'Ordering Customer: NORDIC PAPER CORP',
      'Applicant Location: STORGATAN 14, STOCKHOLM',
      'Favouring Party: HONG KONG DRAGON TRADING',
      'Denominated Currency: EUR',
      'Total Value: 850000.00',
      'Partial Drawings: Permitted',
      'Must Ship By: 2027-04-15',
      'Variance Percentage: 10',
      'Issuing Bank Ref: BARCLAYS PLC LONDON'
    ];
    return this.createPdf(lines, outputPath);
  }

  /**
   * Generates Non-LOC PDF (e.g. standard invoice) to test the Guardrail
   */
  static async generateNonLocInvoicePdf(outputPath) {
    const lines = [
      '### COMMERCIAL INVOICE & RECEIPT',
      '---',
      'Invoice Number: INV-2026-8831',
      'Invoice Date: 2026-10-11',
      'Bill To: Quick Tech Solutions LLC',
      'Ship To: 100 Main Street, Austin, TX',
      'Item Description: Office Workstations & Chairs',
      'Quantity: 10',
      'Unit Price: 150.00',
      'Total Due: 1500.00 USD',
      'Payment Terms: Net 30 Days via Wire Transfer'
    ];
    return this.createPdf(lines, outputPath);
  }
}
