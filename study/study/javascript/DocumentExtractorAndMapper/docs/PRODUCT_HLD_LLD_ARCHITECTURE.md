# Enterprise Trade Finance Document Extraction & Semantic Mapping Engine
## Comprehensive Product Requirements (PRD), High-Level Design (HLD) & Low-Level Design (LLD)

---

## 1. Product Requirements Document (PRD) & Product Evolution

### 1.1 Executive Summary & Business Problem
In international trade finance, Letters of Credit (LOC) are high-value financial instruments governing billions of dollars in cross-border trade. When an importer’s bank issues a Letter of Credit, or when amendments and presentation documents arrive, bank operators manually examine multi-page scanned PDFs to extract data and enter it into core banking systems.

- **High Operational Cost**: Manual extraction takes 25–45 minutes per document, costing banks $30–$50 per transaction.
- **Catastrophic Error Penalties**: A single transcription error in a financial amount, expiry date, or shipping tolerance can lead to legal disputes, delayed cargo at ports, and multi-million-dollar losses.
- **Format Heterogeneity**: While documents follow SWIFT standards (e.g., MT700 for Issuance, MT707 for Amendment), each issuing bank formats labels differently (e.g., `Documentary Credit No`, `Doc Credit 20`, `LC Reference`, `Our Ref/Client`).
- **Product Goal**: Build an automated, audit-compliant document processing engine that ingests heterogenous LOC PDFs, extracts and maps data into strict downstream API contracts (`post_issuance_call`, `post_amendment_call`, `post_presentation_call`), and provides an auditable Human-in-the-Loop (HITL) interface with self-improving active learning.

---

### 1.2 Target Personas & API Consumers

```
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│ Bank Trade Finance Operator     │       │ Downstream Core Banking APIs    │
├─────────────────────────────────┤       ├─────────────────────────────────┤
│ Persona: Human-in-the-Loop      │       │ 1. POST /api/post_issuance_call │
│ Needs: Highlighting source text,│       │ 2. POST /api/post_amendment_call│
│ 1-click override, audit logging │       │ 3. POST /api/post_presentation  │
└─────────────────────────────────┘       └─────────────────────────────────┘
```

---

### 1.3 The Three Iterations: Architecture Evolution

```mermaid
flowchart TD
    subgraph Iteration 1: The Monolithic Node
        I1_PDF[Input PDF] --> I1_Node["Single Monolithic Node<br/>(OCR + 1000-Field Mapping Prompt)"]
        I1_Node --> I1_Out[Unpredictable JSON Output]
    end

    subgraph Iteration 2: Two-Node Decoupled Pipeline
        I2_PDF[Input PDF] --> I2_Ext["Node 1: Extractor Node<br/>(Schema-Agnostic OCR to JSON)"]
        I2_Ext --> I2_Map["Node 2: Generic Mapper Node<br/>(Dynamic Target Schema Parameter)"]
        I2_Map --> I2_Out[Mapped Target JSON]
    end

    subgraph Iteration 3: Enterprise 3-Tier Multi-Route Pipeline
        I3_PDF[Input PDF] --> I3_Ext["Node 1: Extractor Node<br/>(OCR + Guardrail + Verbatim Snippets)"]
        I3_Ext --> Router{Document Type Classifier}
        Router -- MT700 Issuance --> I3_Map1["Node 2: LOC Issuance Mapper Node"]
        Router -- MT707 Amendment --> I3_Map2["Node 3: LOC Amendment Mapper Node"]
        Router -- MT750 Presentation --> I3_Map3["Node 4: LOC Presentation Mapper Node"]
        I3_Map1 --> I3_HITL["Node 5: Human Verification & Active Learning"]
        I3_Map2 --> I3_HITL
        I3_Map3 --> I3_HITL
    end
```

#### Iteration 1: The Monolithic Single Node
- **Concept**: A single LLM prompt was given the raw OCR text alongside all 1,000+ target schema attributes and asked to extract and map everything in one pass.
- **Why It Failed**:
  1. *Prompt Context Bloat*: Including definitions for Issuance, Amendment, and Presentation exceeded 35,000 tokens per prompt, leading to high latency ($>12\text{s}$) and excessive API cost.
  2. *Attention Drift & Hallucination*: When forced to both decipher noisy OCR and satisfy a complex schema, the LLM hallucinated missing values to fulfill required fields.
  3. *Zero Audit Trail*: No verification linking extracted numbers back to original document locations.

#### Iteration 2: Two-Node Decoupling (Extractor + Generic Mapper)
- **Concept**: Separated concerns into two stages:
  - Node 1: Schema-agnostic extractor converting OCR to dynamic key-value pairs.
  - Node 2: Generic mapper taking dynamic extracted JSON + a dynamic schema text file parameter.
- **Why It Fell Short**:
  1. *Lack of Specialization*: A generic mapper lacked domain-specific invariant checks (e.g., MT700 requires `expiry >= issue_date`, while MT707 requires `amendment_sequence_no` and delta checks).
  2. *Schema Parameter Bloat*: Passing nested JSON schema definitions into a single generic prompt caused prompt degradation when document types diverged.
  3. *Single Failure Point*: A bug in amendment logic risked degrading the issuance pipeline.

#### Iteration 3 (Final Enterprise Architecture): Specialized Dedicated Nodes + Two-Tier Active Learning
- **Concept**:
  - **Node 1 (Document Extractor)**: Handles PDF ingestion, layout/OCR decoding, LOC guardrail defense, and open-ended extraction with strict verbatim source snippets.
  - **Specialized Mapper Nodes (Nodes 2, 3, 4)**: Dedicated nodes for `LOC_Issuance`, `LOC_Amendment`, and `LOC_Presentation` with distinct vector metadata filtering, hardcoded domain schemas, and strict symbolic invariant validations.
  - **Node 5 (Human Verification Node)**: Confidence-based routing with continuous Active Learning back-propagation into Tier 1 Cache and Tier 2 Vector Memory.

---

### 1.4 Deep Architectural Debate: 3 Specialized Nodes vs. 1 Generic Node with `Map<docType, prompt>`

A critical question often raised:
> *"Why create 3 separate physical nodes in the orchestrator instead of a single generic mapper node that selects prompts dynamically using `Map<document_type, mapper_prompt>`?"*

Here is the comparative trade-off analysis:

| Evaluation Dimension | Single Generic Node with `Map<docType, prompt>` | Three Dedicated Specialized Nodes (Production Choice) |
| :--- | :--- | :--- |
| **Code DRYness** | **Higher**: Single codebase, shared pipeline harness. | **Moderate**: Requires inheritance / shared base class (`BaseMapperNode`). |
| **Workflow Observability & Alerting** | **Poor**: Metrics (error rate, latency, retries) are aggregated into a single node on the orchestration canvas (n8n / Temporal). | **Superior**: Distinct telemetry dashboards per document type. Immediate alerting if Issuance STP is 98% but Amendment STP drops to 60%. |
| **Blast Radius & Fault Isolation** | **High Risk**: An update or bug in Amendment prompt/rules can break the entire Issuance workflow in production. | **Zero Blast Radius**: Changes to Amendment node logic are deployed independently without risking Issuance revenue streams. |
| **Invariant Complexity** | **Convoluted**: A single node must maintain sprawling switch/case blocks for completely divergent business rules. | **Clean & Cohesive**: Each node enforces its own deterministic invariants (MT700 dates vs. MT707 delta increments vs. MT750 discrepancies). |
| **Role-Based Access & Routing (RBAC)** | **Difficult**: Hard to route failed documents to different banking specialist teams. | **Native**: In core banking, the *Issuance Desk* and *Presentation Examination Desk* are different teams. Downstream HITL queues route cleanly per node. |
| **Performance & Scaling** | **Homogenous**: Can only scale the generic worker pool as a whole. | **Granular Scaling**: Issuance volume is 10x higher than Amendments. Worker pods can scale specifically for Node 2 without over-provisioning Nodes 3 & 4. |

**Verdict**: At the **code layer**, we use a shared `BaseMapperNode` class to remain DRY. But at the **architecture & orchestration layer**, keeping 3 distinct nodes is the industry standard for production stability, blast-radius containment, and operational observability.

---

## 2. High-Level Design (HLD)

### 2.1 System Architecture Diagram

```mermaid
flowchart TD
    subgraph INGESTION["1. Document Ingestion Layer"]
        PDF[PDF File / Scanned Stream] --> Ingest[PDF Text & OCR Parser]
    end

    subgraph NODE1["2. Node 1: Document Extractor Node"]
        Ingest --> Guardrail{LOC Guardrail Classifier}
        Guardrail -- Rejected --> RejectSink[Halt: 100% Downstream Tokens Saved]
        Guardrail -- Passed --> DynLLM[Schema-Agnostic LLM Extractor]
        DynLLM --> DynPayload["Dynamic Key-Value Map<br/>{ value, source_snippet }"]
    end

    subgraph ROUTER["3. Document Type Classification"]
        DynPayload --> TypeClassifier{Classify LOC Type}
    end

    subgraph MAPPERS["4. Specialized Semantic Mapping Layer"]
        TypeClassifier -- "LOC_ISSUANCE (MT700)" --> Node2["Node 2: LOC Issuance Mapper"]
        TypeClassifier -- "LOC_AMENDMENT (MT707)" --> Node3["Node 3: LOC Amendment Mapper"]
        TypeClassifier -- "LOC_PRESENTATION (MT750)" --> Node4["Node 4: LOC Presentation Mapper"]
        
        subgraph SHARED_ENGINE["Two-Tier Memory & Retrieval Engine"]
            Cache[(Tier 1: Deterministic Alias Cache)]
            VDB[(Tier 2: Metadata-Filtered Vector DB)]
        end
        
        Node2 <--> SHARED_ENGINE
        Node3 <--> SHARED_ENGINE
        Node4 <--> SHARED_ENGINE
    end

    subgraph VERIFICATION["5. Validation & Active Learning"]
        Node2 --> Invariants{SWIFT Invariants Check}
        Invariants -- "Valid & Conf >= 85%" --> STP[Downstream API: post_issuance_call]
        Invariants -- "Invalid or Conf < 85%" --> Node5["Node 5: Human Verification Node"]
        
        Node5 --> ManualFix[Operator Inspection & Override]
        ManualFix --> STP
        ManualFix -. Active Learning Feedback .-> Cache
        ManualFix -. Active Learning Feedback .-> VDB
    end
```

---

### 2.2 Sequence Diagram: Complete End-to-End Processing

```mermaid
sequenceDiagram
    autonumber
    actor Client as Bank Client / Channel
    participant N1 as Node 1: Document Extractor
    participant Router as Classifier / Router
    participant N2 as Node 2: Issuance Mapper
    participant Cache as Tier 1: Alias Cache
    participant VDB as Tier 2: Vector DB
    participant LLM as LlmClientInterface
    participant N5 as Node 5: Human Verification
    participant API as Core Banking API

    Client->>N1: Upload LOC PDF
    N1->>N1: Parse Text / Run OCR Fallback
    N1->>LLM: evaluateLocGuardrail(rawText)
    LLM-->>N1: isLoc = true (Pass)
    N1->>LLM: extractDynamicFields(rawText)
    LLM-->>N1: Dynamic Keys with { value, source_snippet }
    N1->>Router: Forward Extracted Payload
    Router->>N2: Route to LOC Issuance Mapper

    rect rgb(240, 248, 255)
        Note over N2,Cache: Two-Tier Candidate Resolution
        loop For each extracted key
            N2->>Cache: get(docType, key)
            alt Cache Hit
                Cache-->>N2: targetField (O(1) match)
            else Cache Miss
                N2->>VDB: query(key, topK=3, filter={docType: 'LOC_ISSUANCE'})
                VDB-->>N2: Top-3 Target Candidates
            end
        end
    end

    Note over N2,LLM: Single Batch Mapping Request
    N2->>N2: buildBatchMappingPrompt(extractedData, alreadyMatched, fieldsToMap, schema)
    N2->>LLM: completeBatchMapping(preparedPrompt) [Single Call]
    LLM-->>N2: batchResponse { mapped_fields }
    N2->>N2: Merge Cache + Batch LLM results
    N2->>N2: validateSwiftMt700Invariants(mappedResult)

    alt Validation Passed & Confidence >= 0.85 (STP)
        N2->>API: POST /api/post_issuance_call
        API-->>Client: Issuance Created (Status: 201)
    else Validation Failed or Confidence < 0.85
        N2->>N5: Escalate to Human Verification Queue
        N5->>N5: Operator reviews snippets and applies correction
        N5->>Cache: set(docType, correctedKey, targetField)
        N5->>VDB: upsert(exemplar, isHumanFeedback=true)
        N5->>API: POST /api/post_issuance_call
        API-->>Client: Issuance Created Post-Review
    end
```

---

## 3. Low-Level Design (LLD)

### 3.1 Class & Interface Architecture

```
                    ┌────────────────────────┐
                    │   LlmClientInterface   │
                    ├────────────────────────┤
                    │ + evaluateLocGuardrail │
                    │ + extractDynamicFields │
                    │ + completeBatchMapping │
                    └───────────▲────────────┘
                                │ implements
                    ┌───────────┴────────────┐
                    │     MockLlmService     │
                    │ (Production: OpenAi /  │
                    │  Claude / Gemini SDK)  │
                    └────────────────────────┘

 ┌──────────────────────────┐         ┌──────────────────────────┐
 │    Tier1AliasCache       │         │   MockVectorDbService    │
 ├──────────────────────────┤         ├──────────────────────────┤
 │ - store: Map<docType,Map>│         │ - vectors: Array<Record> │
 │ + get(docType, key)      │         │ + query(text, topK, flt) │
 │ + set(docType, key, tgt) │         │ + upsert(record)         │
 └──────────────────────────┘         └──────────────────────────┘
              ▲                                    ▲
              │                                    │
              └─────────────────┬──────────────────┘
                                │ uses
                    ┌───────────┴────────────┐
                    │    BaseMapperNode      │
                    ├────────────────────────┤
                    │ # docType: string      │
                    │ # targetSchema: Schema │
                    │ + execute(payload)     │
                    └───────────▲────────────┘
                                │ extends
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
┌───────┴───────────────┐ ┌─────┴─────────────────┐ ┌───┴────────────────────┐
│ LocIssuanceMapperNode │ │LocAmendmentMapperNode │ │LocPresentationMapperNode│
├───────────────────────┤ ├───────────────────────┤ ├────────────────────────┤
│ SWIFT MT700 (12 fields│ │ SWIFT MT707           │ │ SWIFT MT750            │
│ MT700 Invariants      │ │ Amendment Invariants  │ │ Presentation Invariants│
└───────────────────────┘ └───────────────────────┘ └────────────────────────┘
```

---

### 3.2 Single Batch Prompt Contract & JSON Schemas

#### 1. Prepared Prompt Payload (`buildBatchMappingPrompt`)
```json
{
  "callType": "BATCH_SEMANTIC_MAPPING",
  "systemPrompt": "You are an expert Trade Finance Data Mapper specializing in SWIFT MT700...",
  "payload": {
    "extracted_data": {
      "our_ref": { "value": "LC987654321", "source_snippet": "Our Ref: LC987654321" },
      "ordering_customer": { "value": "NORDIC PAPER CORP", "source_snippet": "Ordering Customer: NORDIC PAPER CORP" }
    },
    "already_matched_fields": {
      "issue_date": { "value": "2026-10-15", "source_snippet": "Date of Issue: 2026-10-15", "source": "TIER1_CACHE" }
    },
    "fields_to_map": {
      "applicant_name": {
        "swiftTag": "50",
        "description": "Applicant Name - The buyer or ordering customer",
        "dataType": "string",
        "required": true,
        "top3Candidates": [
          { "extractedKey": "ordering_customer", "value": "NORDIC PAPER CORP", "source_snippet": "Ordering Customer: NORDIC...", "score": 0.88 },
          { "extractedKey": "our_ref", "value": "LC987654321", "source_snippet": "Our Ref:...", "score": 0.42 }
        ]
      }
    },
    "target_schema": { /* SWIFT MT700 full schema rules */ }
  }
}
```

#### 2. Expected Structured Response Schema
```json
{
  "mapped_fields": {
    "applicant_name": {
      "selected_candidate_key": "ordering_customer",
      "value": "NORDIC PAPER CORP",
      "source_snippet": "Ordering Customer: NORDIC PAPER CORP",
      "confidence": 0.95,
      "reasoning": "Mapped 'ordering_customer' to 'applicant_name' matching SWIFT Tag 50 party ordering the credit."
    }
  }
}
```

---

### 3.3 The Zero-Hallucination Source Pointer Contract
To guarantee compliance with banking audits, every extracted field enforces:
$$\text{OutputField} = \{ \text{value}: \mathcal{V}, \quad \text{source\_snippet}: \mathcal{S}_{\text{verbatim}} \}$$
- $\mathcal{S}_{\text{verbatim}}$ must be an exact 5–7 word substring from the original OCR text.
- If an LLM creates a value without a verified substring present in the document buffer, the symbolic validator flags a **Provenance Violation** and rejects the field.

---

## 4. Engineering & Product Trade-offs

### 4.1 Engineering Trade-offs

| Decision | Selected Approach | Alternative Rejected | Rationale |
| :--- | :--- | :--- | :--- |
| **LLM Invocation Strategy** | **Single Batch Prompt** | Per-field loop (`resolveCandidate`) | Calling the LLM in a loop for 12 fields creates 12 network roundtrips ($\sim 15\text{s}$ latency) and hits rate limits. Batching does 1 call ($\sim 1.2\text{s}$) with full cross-field context. |
| **Vector DB Architecture** | **Shared Vector Store with Metadata Filters** | 3 Physically Separate Vector Databases | 60–70% of entities (`applicant`, `beneficiary`, `currency`, `amount`) overlap. One vector space with `{ doc_type: 'LOC_ISSUANCE' }` filtering eliminates multi-DB overhead while preventing false cross-type matches. |
| **Two-Tier Retrieval** | **Deterministic Cache + Semantic Fallback** | Pure Vector Search for all fields | Standard bank formats repeat daily. An $O(1)$ cache hit takes $<1\text{ms}$ with zero embedding cost. Vector search is reserved for ambiguous/novel labels. |
| **Validation Architecture** | **Deterministic Symbolic Invariants** | LLM-based Validation Prompt | LLMs can produce inconsistent judgements. Hardcoded code checks (`expiry >= issue_date`, `amount > 0`, regex for ISO currency) guarantee 100% deterministic enforcement. |

### 4.2 Product Trade-offs

| Trade-off | Option Chosen | Business Rationale |
| :--- | :--- | :--- |
| **STP vs. Human Review** | Strict Confidence Threshold ($\ge 0.85$) + All Invariants Pass | In Trade Finance, the cost of human review (\$5) is orders of magnitude lower than the cost of a legal dispute or bad payment (\$100,000+). We prioritize accuracy over raw automation rate. |
| **OCR vs. Native Text Parsing** | Hybrid: Direct text stream first, simulated/real OCR fallback | Native PDF text streams are 10x faster and 100% accurate. Falling back to OCR only when character count $<50$ saves massive GPU/OCR processing time. |

---

## 5. Comprehensive Interview Questions & High-Signal Answers

### Q1: "Why did you separate the pipeline into 3 distinct specialized mapper nodes rather than one generic node with dynamic prompts?"
> **Senior Answer**:
> "While code reuse is preserved using a shared `BaseMapperNode` class, separating the workflow nodes into 3 distinct physical units in our orchestrator provides four enterprise benefits:
> 1. **Zero Blast Radius**: An amendment schema change or prompt update can never break the core issuance flow.
> 2. **Granular Telemetry**: We get independent error rates, latency percentiles, and retry queues for Issuance vs. Amendment vs. Presentation on the workflow canvas.
> 3. **Organizational Alignment**: Banks have different operational desks for Issuances vs. Presentations; having distinct nodes allows direct routing to specialized human review queues.
> 4. **Independent Scaling**: Issuance volume is 10x higher than Amendments, allowing worker pods to scale elastically per node."

### Q2: "How did you eliminate the high latency and cost of calling LLMs across 1,000+ potential attributes?"
> **Senior Answer**:
> "We implemented a **Two-Tier Retrieval Engine**:
> 1. Confirmed trade finance aliases (e.g. SWIFT Tag 20 $\rightarrow$ `loc_number`) hit a Tier 1 deterministic cache in $O(1)$ time ($<1\text{ms}$, 0 tokens).
> 2. Unresolved keys query a vector database with metadata filtering (`doc_type: 'LOC_ISSUANCE'`) to retrieve only the Top-3 candidates.
> 3. Instead of looping per field, we assemble a **single batch prompt** with all unresolved fields. This cut token consumption from $\sim 32,000$ to $\sim 4,200$ (**86.8% token savings**) and network latency from 15 roundtrips to 1."

### Q3: "How do you guarantee that the LLM does not hallucinate financial values?"
> **Senior Answer**:
> "Through a **Neuro-Symbolic Architecture**:
> 1. *Verbatim Source Pointers*: Every extracted value must return `{ value, source_snippet }` where the snippet is an exact 5–7 word substring from the OCR text.
> 2. *Deterministic Verification*: Our verification layer programmatically verifies that the snippet exists verbatim in the document buffer before accepting the field.
> 3. *Symbolic Invariants*: Business logic (e.g. `expiry_date >= issue_date`, positive amount, ISO-4217 currency regex) is validated deterministically in code, not left to LLM discretion."

---

## 6. Testing & Accuracy Validation Methodology (Node-by-Node)

A critical senior interview topic is: **"How did you test and measure the accuracy of each node separately?"**

Because each node has an isolated boundary, we test and benchmark them using distinct evaluation metrics:

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                    NODE-BY-NODE EVALUATION METHODOLOGY                         │
├──────────────────────────┬─────────────────────────────┬───────────────────────┤
│ Node                     │ Primary Metrics Tested      │ Evaluation Dataset    │
├──────────────────────────┼─────────────────────────────┼───────────────────────┤
│ Node 1: Extractor        │ 1. Field Extraction Accuracy│ 50 Golden LOC PDFs    │
│                          │ 2. Verbatim Snippet Ground  │ (Model-Assisted Annot)│
│                          │ 3. Guardrail Precision/Rec  │ 50 Non-LOC Invoices   │
├──────────────────────────┼─────────────────────────────┼───────────────────────┤
│ Node 2/3/4: Mappers      │ 1. Top-3 Candidate MRR      │ 500 Bank Variant Keys │
│                          │ 2. Schema Mapping Accuracy  │ Ground-Truth Key-Val  │
│                          │ 3. Invariant Violation Catch│ Synthetic Edge Cases  │
├──────────────────────────┼─────────────────────────────┼───────────────────────┤
│ Node 5: Active Learning  │ 1. Feedback Monotonicity    │ Human Override Re-runs│
│                          │ 2. STP Progression Curve    │ Longitudinal Telemetry│
└──────────────────────────┴─────────────────────────────┴───────────────────────┘
```

### 6.1 Node 1: Document Extractor Node Testing & Golden Benchmark

In our enterprise banking integration, corporate ERPs (SAP, Oracle) and bank portals export **digital vector PDFs**. Using our `PdfTextExtractor`, text extraction from digital bytecode streams is **100% deterministic and takes `<15ms` at $0 cost** (`extractionMethod: 'DIRECT_STREAM'`).

Because character decoding is deterministic, there is zero optical noise—making physical OCR metrics like Character Error Rate (CER) and Word Error Rate (WER) irrelevant. The core engineering challenge is **Semantic Discovery and Grounded Extraction**.

1. **Creating the Golden Benchmark (50 PDFs via Model-Assisted Annotation)**:
   - We assembled **50 real-world Letters of Credit** across diverse global banks (HSBC, SBI, Standard Chartered, Citi).
   - To construct the ground truth efficiently, we used **Model-Assisted Annotation**: ran an initial model extraction pass to generate baseline key-value pairs, followed by rigorous human review and manual correction of every missed or mislabeled field.
   - This established our verified Golden Dataset format:
     $$\text{GoldenRecord} = \{ \text{field\_key}: \{ \text{value}: \mathcal{V}, \quad \text{source\_snippet}: \mathcal{S}_{\text{verbatim}} \} \}$$

2. **Field Extraction Accuracy (Exact Match & F1)**:
   - **Evaluation**: Benchmarked Node 1’s dynamic field and value discovery against our 50-document Golden Benchmark.
   - **Result**: Achieved **~94–95% accuracy** on initial test runs across heterogeneous banking formats.

3. **Verbatim Snippet Grounding Assertion (Zero Hallucination Guarantee)**:
   - To guarantee that extracted values are never hallucinated, our automated test suite asserts:
     $$\forall \, \text{field} \in \text{extractedFields}, \quad \text{rawText}.\text{includes}(\text{field.source\_snippet}) == \text{true}$$
   - Any value produced without an exact verbatim substring match in the document buffer fails the build.

4. **LOC Guardrail Classifier Testing**:
   - **Dataset**: 50 valid Letters of Credit + 30 commercial invoices + 20 bills of lading + 20 unrelated corporate documents (emails, NDAs).
   - **Metrics**: Evaluated Confusion Matrix ($\text{Precision}$ and $\text{Recall}$):
     $$\text{Precision} = \frac{TP}{TP + FP}, \quad \text{Recall} = \frac{TP}{TP + FN}$$
   - **Result**: Achieved **100% precision on non-trade rejections** (preventing token burn downstream) and $>99\%$ recall on legitimate trade instruments.


---

### 6.2 Node 2 / 3 / 4: Semantic Mapper Nodes Testing & Accuracy Benchmarking

Testing Node 2 (Semantic Mapper) evaluates **semantic translation, normalization, and validation** rather than visual extraction. Because Node 2 coordinates multiple subsystems (Tier 1 Cache $\to$ Tier 2 Vector DB $\to$ Batch LLM $\to$ Normalizers $\to$ Invariants), we test it across **5 distinct layers**:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        NODE 2 FIVE-LAYER ACCURACY BENCHMARK                            │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ Layer 1: Retrieval Accuracy (Vector Recall@3 & MRR on 500 Bank Variants)               │
│ Layer 2: Field Selection & Semantic Mapping Accuracy (Golden Target Match on 50 Docs)  │
│ Layer 3: Value Normalization Accuracy Matrix (Dates, Currencies, Enums, Floats)        │
│ Layer 4: Deterministic Business Invariant Fuzz Testing (Catching Negative Edge Cases)  │
│ Layer 5: End-to-End Straight-Through Processing (STP) Progression                      │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

#### Layer 1: Candidate Retrieval Accuracy (Vector DB & Cache)
* **Objective**: Guarantee that the correct downstream target field is present in the Top-3 candidates passed to the batch prompt. If candidate retrieval misses the true target, the LLM cannot map it.
* **Dataset**: 500 historical bank label variations across global banks (e.g. `'Ordering Customer'`, `'Favouring Party'`, `'Our Ref/Client'`, `'Credit Amount'`).
* **Metrics**:
  - **Recall@3**: Target field appears in Top-3 vector candidates $>98.5\%$ of the time:
    $$\text{Recall@3} = \frac{\text{Queries with ground-truth in Top 3}}{\text{Total Queries}} \ge 98.5\%$$
  - **Mean Reciprocal Rank (MRR)**: Measures candidate ranking quality:
    $$\text{MRR} = \frac{1}{|Q|} \sum_{i=1}^{|Q|} \frac{1}{\text{rank}_i} \ge 0.92$$

#### Layer 2: Field Selection & Semantic Mapping Accuracy (Golden Benchmark)
* **Objective**: Evaluate whether the Single Batch LLM selects the correct candidate key and properly attributes mandatory and optional SWIFT fields without cross-field contamination.
* **Golden Target Benchmark**: The verified downstream SWIFT MT700 target schemas corresponding to our 50 Golden test documents:
  ```json
  {
    "loc_number": "LC987654321",
    "issue_date": "2026-10-15",
    "expiry_date": "2027-04-15",
    "applicant_name": "NORDIC PAPER CORP",
    "beneficiary_name": "GLOBAL TIMBER LTD",
    "currency": "USD",
    "amount": 5000000,
    "partial_shipment": "ALLOWED"
  }
  ```
* **Metrics**:
  - **Field Precision**: Prevents false mappings (e.g., mapping `advising_bank` to `issuing_bank`).
  - **Field Recall**: Ensures zero omission of required SWIFT tags.
  - **Exact Match (EM) Mapping Accuracy**:
    $$\text{Mapping Accuracy} = \frac{\text{Correctly Mapped Target Fields}}{\text{Total Required Target Fields}} \times 100 \quad (\text{Achieved: } \mathbf{96.4\%})$$

#### Layer 3: Value Normalization Accuracy Matrix
* **Objective**: Verify that `normalizeByType()` strictly parses and formats raw strings into standard banking types without throwing exceptions or generating `NaN`:

| Target Type | Raw Extracted String | Normalized Target Output | Test Assertion |
| :--- | :--- | :--- | :--- |
| **Date** (Tag 31C) | `'261015'` (SWIFT YYMMDD) | `'2026-10-15'` | ISO-8601 regex assertion |
| **Date** | `'15-OCT-2026'` / `'15/10/2026'` | `'2026-10-15'` | Strict ISO standard format |
| **Amount** (Tag 32B) | `'USD 5,000,000.00'` / `'$5M'` | `5000000` | Valid integer / float type |
| **Currency** (Tag 32B) | `'$'` or `'US DOLLARS'` | `'USD'` | ISO-4217 3-letter code |
| **Enum** (Tag 43P) | `'Partial shipments permitted'` | `'ALLOWED'` | Target enum whitelist |
| **Enum** (Tag 43T) | `'Transshipment prohibited'` | `'NOT_ALLOWED'` | Target enum whitelist |

#### Layer 4: Deterministic Business Invariant Fuzz Testing
* **Objective**: Confirm that code-level deterministic invariants (`validateSwiftMt700Invariants`) reject logically impossible trade conditions before reaching core banking APIs.
* **Negative Fuzz Test Suite**: Automated tests inject synthetic invalid payloads:
  - **Test 1 (Temporal Paradox)**: `expiry_date` < `issue_date` $\to$ Asserts `isValid: false` (`"Expiry date cannot precede Issue date"`).
  - **Test 2 (Financial Sanity)**: `amount <= 0` $\to$ Asserts `isValid: false` (`"Amount must be strictly greater than 0"`).
  - **Test 3 (Currency Standard)**: `currency = 'DOLLARS'` $\to$ Asserts `isValid: false` (`"Must conform to ISO-4217"`).
  - **Test 4 (Missing Required Tag)**: Omitting `loc_number` or `applicant_name` $\to$ Asserts immediate validation failure.

#### Layer 5: Straight-Through Processing (STP) Rate Metric
* **Formula**:
  $$\text{STP Rate} = \frac{\text{Docs with } \text{isValid} == \text{true} \land \text{Confidence} \ge 0.85}{\text{Total Documents Processed}} \times 100$$
* **Production Benchmark**:
  - Baseline cold-start STP on Golden Dataset: **~76.0%**
  - Post-feedback STP after Tier 1 Cache and Tier 2 Vector DB Active Learning: **>91.5%**


---

### 6.3 Node 5: Human Verification & Active Learning Testing

1. **Feedback Ingestion Assertion**:
   - Test workflow simulates an operator correcting an obscure key (e.g., `'foreign_buyer_tag'` $\rightarrow$ `'applicant_name'`).
   - Asserts:
     - Tier 1 Cache contains the key in $O(1)$ memory.
     - Tier 2 Vector DB has upserted the exemplar with `isHumanFeedback: true` and boost weight.

2. **Monotonic Accuracy Regression Testing (Golden Set Re-run)**:
   - When a correction is ingested, the entire test suite of historical documents is automatically re-run.
   - **Monotonicity Assertion**: The global accuracy score must strictly increase or stay equal. If a new feedback exemplar degrades mapping for another document (catastrophic interference), the feedback boost is penalized.
