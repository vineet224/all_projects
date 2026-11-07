# Enterprise Document Extraction & Semantic Mapping Engine
## 1–2 Hour Technical Interview Presentation Guide & System Design Playbook

---

### Overarching Theme & Pitch
> **"A Two-Stage Neuro-Symbolic Extraction and Semantic Routing Pipeline for High-Stakes Trade Finance (Letters of Credit) with Continuous Active Learning."**

*Why this framing wins:*
- **Neuro-Symbolic Architecture**: You do not blindly trust LLM predictions for banking APIs. You combine neural extraction (VLM/LLMs and vector embeddings) for unstructured ambiguity with deterministic symbolic guardrails (schema typing, SWIFT MT700 invariants, business rules).
- **Separation of Concerns**: Extractor Node (perception/OCR $\rightarrow$ unstructured JSON) is decoupled from the Mapper Node (semantic routing $\rightarrow$ strict downstream API contract).
- **Auditability Contract**: Every extracted value is backed by a verbatim `source_snippet`, eliminating hallucinations and meeting banking compliance requirements.

---

### Presentation Agenda (60–90 Minutes)

| Time | Phase | Key Discussion Points |
| :--- | :--- | :--- |
| **00–10 min** | **Problem & Business Impact** | High volume of scanned PDFs (Issuance, Amendment, Presentation). High cost of manual bank data entry ($30–$50/doc). Error penalties (a \$2M LC error can halt international shipping). |
| **10–25 min** | **Architecture Evolution (Iteration 1 vs 2)** | Why the single monolithic prompt failed (context bloat, hallucination, prompt drift). The 2-stage decoupling: Schema-less Extractor + Domain-Specific Mapper. |
| **25–45 min** | **Semantic Mapping & Vector Architecture** | The 1,000+ attribute ambiguity challenge. Two-Tier Retrieval: Tier 1 Exact Alias Cache ($O(1)$) + Tier 2 Metadata-Filtered Vector DB (`doc_type: LOC_ISSUANCE`). |
| **45–65 min** | **Reliability, Invariants & Auditability** | Verbatim source snippets, deterministic validation (`expiry_date >= issue_date`, positive amounts, ISO-4217), STP vs. HITL confidence routing. |
| **65–80 min** | **Active Learning & Feedback Loop** | Human operator overrides back-propagated into Cache and Vector exemplars, increasing Straight-Through Processing (STP) to near 100%. |
| **80–90 min** | **Trade-offs, Edge Cases & Q&A** | Cost/token economics, latency, scaling to thousands of document variants. |

---

### System Architecture Diagram

```mermaid
flowchart TD
    subgraph Stage 1: Document Extractor Node
        PDF[PDF Input / Scanned LOC] --> OCR[PDF Text & OCR Parser]
        OCR --> Guardrail{LOC Guardrail Classifier}
        Guardrail -- Rejected --> Terminate[Halt: 100% Tokens Saved]
        Guardrail -- Passed --> DynLLM[Schema-less Dynamic Extractor]
        DynLLM --> DynJSON["Dynamic Key-Value Output<br/>{ value, source_snippet }"]
    end

    subgraph Stage 2: LOC Issuance Semantic Mapper
        DynJSON --> Tier1{Tier 1: Alias Cache Hit?}
        Tier1 -- HIT --> DirectMap[O(1) Instant Match]
        Tier1 -- MISS --> Tier2[Tier 2: Vector DB<br/>filter: doc_type=LOC_ISSUANCE]
        Tier2 --> Candidates[Top-3 Semantic Candidates]
        DirectMap --> Aggregate[Candidate Aggregation]
        Candidates --> Aggregate
        Aggregate --> LLMResolve[LLM Normalization & Resolution]
        LLMResolve --> Invariants{Deterministic SWIFT Invariants}
    end

    subgraph Stage 3: Routing & Active Learning
        Invariants -- Valid & Conf >= 85% --> STP[POST /api/post_issuance_call]
        Invariants -- Invalid or Conf < 85% --> HITL[Human Verification Node]
        HITL --> Correction[Operator Override]
        Correction --> STP
        Correction -. Active Learning Feedback .-> Tier1Update[Write to Tier 1 Cache]
        Correction -. Active Learning Feedback .-> Tier2Update[Inject Vector Exemplar]
    end
```

---

### Deep-Dive: Hard Interview Questions & Senior Answers

#### Q1: "Why not do extraction and mapping in a single LLM prompt?"
> **Senior Answer**:
> "In early iterations, we tested a single monolithic prompt. It broke down for three reasons:
> 1. **Token Economics**: Passing 1,000+ potential target schema fields, descriptions, and banking rules blew up the prompt context window to 30,000+ tokens per call, causing high latency and prohibitive API costs.
> 2. **Attention Drift & Hallucination**: When an LLM is asked to both transcribe noisy OCR and force-fit values into a rigid schema simultaneously, it hallucinates missing values to satisfy the schema.
> 3. **Decoupled Evolution**: Decoupling allows our OCR/Extraction node to remain completely schema-agnostic. When banking APIs update or we add Amendment/Presentation schemas, the extraction node remains untouched."

#### Q2: "How did you solve field-name ambiguity across 1,000+ attributes?"
> **Senior Answer**:
> "We implemented a **Two-Tier Memory & Retrieval Engine**:
> - **Tier 1 (Deterministic Alias Cache)**: Standardizes known variations (e.g., SWIFT Tag 20 $\rightarrow$ `loc_number`) with an $O(1)$ in-memory/Redis lookup, bypassing vector search and LLMs completely.
> - **Tier 2 (Metadata-Filtered Vector Store)**: When an extracted label is non-standard (e.g., `'Our Ref/Client'` or `'Ordering Customer'`), we embed the label and query our vector index with a metadata filter: `filter: { doc_type: 'LOC_ISSUANCE' }`. This retrieves the Top-3 candidate target fields.
> - **Constrained LLM Call**: The LLM only receives the 3 candidate options for each field rather than 1,000 fields, reducing token consumption by ~80%."

#### Q3: "How do you guarantee that the LLM didn't hallucinate a financial figure?"
> **Senior Answer**:
> "We enforced an immutable **Source Pointer Contract**. Every extracted value must return `{ value: ..., source_snippet: ... }` where `source_snippet` is a 5–7 word exact substring from the OCR text.
> Before calling downstream APIs, our verification layer asserts that the `source_snippet` actually exists verbatim in the document buffer. If it cannot be matched, it fails validation and routes to a human operator."

#### Q4: "How does the system improve over time without expensive model fine-tuning?"
> **Senior Answer**:
> "Through our **Active Learning Feedback Loop** in the Human Verification Node. When a human operator reviews an ambiguous document and maps an obscure label (e.g. `'favouring_party'` $\rightarrow$ `'beneficiary_name'`), the system writes this confirmation to both:
> 1. The Tier 1 exact cache (guaranteeing instant $O(1)$ hits on identical future bank templates).
> 2. The Tier 2 vector database as a high-weight exemplar.
> On subsequent document runs, Straight-Through Processing (STP) rises automatically without retraining weights."

#### Q5: "Did you invoke the LLM for every unresolved field in a loop? How did you design the LLM Interface?"
> **Senior Answer**:
> "Never in a per-field loop! Calling the LLM in a loop for 10–15 fields creates severe HTTP latency (15 network roundtrips), hits API rate limits, and loses cross-field relational context.
> 
> Instead, we architected:
> 1. **Batch Prompt Assembly**: We bundle all unresolved target fields into a **single batch prompt**. The payload explicitly contains:
>    - `extracted_data`: Full document key-values for holistic context.
>    - `already_matched_fields`: Fields already resolved deterministically via Tier 1 Cache (so the LLM doesn't waste tokens or overwrite them).
>    - `fields_to_map`: Only the unresolved target fields, each bundled with its Top-3 vector candidate matches.
>    - `target_schema`: Technical definitions, expected data types, and banking constraints.
> 2. **Provider-Agnostic LLM Interface (`LlmClientInterface`)**: The node code has zero tight coupling to OpenAI, Anthropic, or Gemini SDKs. The node prepares the structured prompt payload and expected JSON schema, and delegates to an interface. In our prototype/tests, this interface returns static conforming mock data, allowing production LLM providers to be plugged in via configuration without modifying any node mapping logic."

---

### Token Economics & Performance Metrics to Quote

- **Token Reduction**: Monolithic 1,000-field prompt ($\approx 32,000$ tokens) $\rightarrow$ Two-Stage Vector Candidate prompt ($\approx 4,200$ tokens) = **86.8% Token Savings**.
- **Latency**: $O(1)$ cache hits execute in $< 1\text{ms}$; vector cosine search runs in $< 15\text{ms}$; LLM resolution runs in $\approx 1.2\text{s}$.
- **Guardrail Savings**: Guardrail check (lightweight classifier) filters out invalid/non-LOC files in $\approx 150\text{ms}$, saving 100% of downstream extraction and mapping compute.
