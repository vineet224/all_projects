# Document Extractor & Semantic Mapper Engine

Enterprise Neuro-Symbolic Document Extraction and Semantic Mapping Engine for Trade Finance Letters of Credit (LOC), featuring Two-Tier Retrieval, Strict SWIFT MT700 Invariant Validation, and an Active Learning Human-in-the-Loop Feedback Loop.

---

## Architecture Overview

```
[ Incoming PDF Document ]
           │
           ▼
┌────────────────────────────────────────────────────────┐
│ Stage 1: Document Extractor Node                       │
│ - Ingests native or scanned PDF via PDF parser / OCR   │
│ - LOC Guardrail Classifier (halts non-LOC early)       │
│ - Schema-less Dynamic Extractor                        │
│ - Enforces verbatim { value, source_snippet } contract │
└────────────────────────────────────────────────────────┘
           │
           ▼ Dynamic Key-Value Map
┌────────────────────────────────────────────────────────┐
│ Stage 2: LOC Issuance Semantic Mapper Node             │
│ - Two-Tier Retrieval:                                  │
│   • Tier 1: Deterministic Alias Cache (O(1) lookup)    │
│   • Tier 2: Vector DB (filter: doc_type=LOC_ISSUANCE)  │
│ - LLM Candidate Selection & Type Normalization         │
│ - Deterministic SWIFT MT700 Business Invariants Check  │
│   (expiry >= issue_date, positive amount, ISO-4217)   │
└────────────────────────────────────────────────────────┘
           │
     ┌─────┴─────────────────────────┐
     ▼                               ▼
[ Valid & High Confidence ]   [ Low Conf / Invalid ]
     │                               │
     ▼                               ▼
POST /post_issuance_call       ┌──────────────────────────────────────┐
                               │ Stage 3: Human Verification Node     │
                               │ - Operator inspection & override     │
                               │ - Active Learning feedback loop:     │
                               │   Writes to Tier 1 Cache & Vector DB │
                               └──────────────────────────────────────┘
```

---

## Key Modules

- [`src/nodes/documentExtractorNode.js`](file:///c:/vineet_java/study/study/javascript/DocumentExtractorAndMapper/src/nodes/documentExtractorNode.js): Ingestion, guardrails, schema-agnostic extraction.
- [`src/nodes/locIssuanceMapperNode.js`](file:///c:/vineet_java/study/study/javascript/DocumentExtractorAndMapper/src/nodes/locIssuanceMapperNode.js): Two-tier memory retrieval, candidate resolution, SWIFT invariants.
- [`src/nodes/humanVerificationNode.js`](file:///c:/vineet_java/study/study/javascript/DocumentExtractorAndMapper/src/nodes/humanVerificationNode.js): Human-in-the-loop review and continuous feedback propagation.
- [`src/config/swiftMt700Schema.js`](file:///c:/vineet_java/study/study/javascript/DocumentExtractorAndMapper/src/config/swiftMt700Schema.js): Canonical target schema definitions, data types, and conditional rules.
- [`src/services/tier1AliasCache.js`](file:///c:/vineet_java/study/study/javascript/DocumentExtractorAndMapper/src/services/tier1AliasCache.js): Fast $O(1)$ alias cache simulating Redis.
- [`src/services/mockVectorDbService.js`](file:///c:/vineet_java/study/study/javascript/DocumentExtractorAndMapper/src/services/mockVectorDbService.js): Vector DB with cosine similarity and metadata filtering.
- [`src/services/llmInterface.js`](file:///c:/vineet_java/study/study/javascript/DocumentExtractorAndMapper/src/services/llmInterface.js): Provider-agnostic LLM client interface, batch prompt builder, and structured JSON response schemas.
- [`src/services/mockLlmService.js`](file:///c:/vineet_java/study/study/javascript/DocumentExtractorAndMapper/src/services/mockLlmService.js): Mock LLM service implementing the interface with conforming static responses.
- [`docs/INTERVIEW_SYSTEM_DESIGN.md`](file:///c:/vineet_java/study/study/javascript/DocumentExtractorAndMapper/docs/INTERVIEW_SYSTEM_DESIGN.md): Detailed 1–2 hour interview preparation guide and talking scripts.

---

## Quickstart

### 1. Run the Interactive Pipeline Demo
```bash
npm start
# or: npm run demo
```
This automatically generates sample test PDFs (`temp_samples/`), runs 4 comprehensive scenarios (Guardrail rejection, Standard STP, Ambiguous bank advice, and Active Learning re-run), and displays execution telemetry.

### 2. Run the Automated Test Suite
```bash
npm test
```
Executes automated test cases testing guardrails, dynamic extraction, two-tier mapping, constraint validation, and feedback back-propagation. sample change
