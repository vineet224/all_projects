# MyNaksh — Personalized AI Context Engine

A high-performance, resilient intelligence layer that sits between upstream Vedic astrological backend services (User Profile, Kundli, Horoscope, Panchang) and Large Language Models (LLMs).

---

## 🏛️ Architecture & Data Flow

```
                                  Client Request
                                         │
                                         │ POST /personalize
                                         ▼
                          ┌─────────────────────────────┐
                          │    PersonalizeController    │
                          └──────────────┬──────────────┘
                                         │
        ┌────────────────────────────────┴────────────────────────────────┐
        ▼                                                                 ▼
┌───────────────────────────────┐                       ┌────────────────────────────────┐
│   UpstreamAggregatorService   │                       │    KeywordIntentClassifier     │
│   (Promise.allSettled)        │                       │    (Extensible IIntent)        │
│   ├── User (TTL Cached)       │                       └────────────────┬───────────────┘
│   ├── Kundli (800ms timeout)  │                                        │
│   ├── Horoscope (800ms)       │                                        │ Intent
│   └── Panchang (TTL Cached)   │                                        ▼
└───────────────┬───────────────┘                       ┌────────────────────────────────┐
                │ Raw Context                           │    PersonalizationEngine       │
                └──────────────────────────────────────►│    - Declarative Matrix        │
                                                        │    - ContextPruner             │
                                                        │    - ConfidenceCalculator      │
                                                        └────────────────┬───────────────┘
                                                                         │ Filtered Context
                                                                         ▼
                                                        ┌────────────────────────────────┐
                                                        │         PromptBuilder          │
                                                        │  - Token Optimization          │
                                                        │  - Persona/Tone Integration    │
                                                        │  - Negative Context Exclusion  │
                                                        └────────────────┬───────────────┘
                                                                         │ Prompt
                                                                         ▼
                                                        ┌────────────────────────────────┐
                                                        │      ILlmProvider Adapter      │
                                                        │  ├── MockLlmProvider (default) │
                                                        │  └── GeminiLlmProvider         │
                                                        └────────────────┬───────────────┘
                                                                         │
                                                                         ▼
                                                        { answer, confidence, sourcesUsed }
```

---

## 🚀 Key Engineering Highlights

### 1. Declarative Personalization Matrix (Open-Closed Principle)

Instead of tangled procedural `if/else` statements, context selection and noise exclusion are modeled as a **Declarative Personalization Matrix** (`src/config/personalization.matrix.ts`).

- **Primary Sources**: Astrological indicators mandatory for the query domain (e.g. 10th House & Career Horoscope for career queries).
- **Secondary Sources**: Supporting indicators (e.g. Current Dasha, Panchang).
- **Negative Context Elimination**: Irrelevant or conflicting data (e.g. Relationship Horoscope during a Career query) is strictly stripped before prompt assembly to reduce token costs and eliminate LLM hallucination.

### 2. Resilience & Graceful Degradation

- **Concurrent Fetching with Timeouts**: Upstream services are requested in parallel using `Promise.allSettled()`, with strict per-service timeout bounds (default 800ms).
- **System-Computed Confidence**: Confidence (`HIGH`, `MEDIUM`, `LOW`) is not an arbitrary hallucinated LLM value, but a **deterministic calculation** based on data completeness:
  - `HIGH`: All primary and secondary context sources responded successfully.
  - `MEDIUM`: Secondary sources (e.g. Panchang) timed out or failed, but primary indicators are present.
  - `LOW`: Primary indicators failed (e.g. Kundli database timeout); prompt automatically instructs the LLM to provide modest guidance with a clear disclaimer.

### 3. Swappable LLM Providers (Adapter Pattern)

- `ILlmProvider` contract decouples the engine from specific AI vendors.
- **MockLlmProvider** (Default): Fast, zero-dependency, astrologically grounded responses allowing local testing without API keys.
- **GeminiLlmProvider**: Pluggable integration powered by the official `@google/genai` SDK (`gemini-2.5-flash`).

### 4. Dual-Tier In-Memory Caching

- **User Profiles**: Cached with a 1-hour TTL.
- **Panchang**: Daily astronomical data cached with a 6-hour TTL, saving redundant external compute.

---

## 🛠️ Quickstart & Run Instructions

### Prerequisites

- **Node.js**: v18+ (tested on Node v22)
- **npm**: v9+

### 1. Installation

```bash
npm install
```

### 2. Environment Configuration

A default `.env` file is provided:

```ini
PORT=3000
NODE_ENV=development
UPSTREAM_TIMEOUT_MS=800
CACHE_USER_TTL_MS=3600000
CACHE_PANCHANG_TTL_MS=21600000

# Optional: Set your Gemini API Key to use real Gemini models
GEMINI_API_KEY=
DEFAULT_LLM_PROVIDER=mock
```

### 3. Run Development Server

```bash
npm run dev
```

Server starts on `http://localhost:3000`.

### 4. Build & Run Production

```bash
npm run build
npm start
```

### 5. Run Automated Tests

```bash
npm test
```

Executes 14 unit and integration tests across 4 test suites using Node's native test runner (`tsx --test`).

---

## 📡 API Reference & Verification

### 1. `POST /personalize`

Generates a grounded, personalized astrological answer.

#### Request:

```bash
curl -X POST http://localhost:3000/personalize \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user_101",
    "question": "Should I consider changing my job in the next few months?"
  }'
```

#### Response:

```json
{
  "answer": "Based on your 10th House alignment with Moon in a strong position, combined with your Career Horoscope highlighting new networking opportunities, the upcoming period is highly favorable for professional exploration. However, under your current Rahu-Mars dasha, ensure you thoroughly evaluate contractual terms before finalizing any major career transitions. Focus on networking strategically over the next two to three months.",
  "confidence": "HIGH",
  "sourcesUsed": [
    "10th House (Karma/Profession)",
    "Career Horoscope",
    "Current Dasha",
    "Today's Panchang"
  ],
  "metadata": {
    "intent": "career",
    "latencyMs": 182,
    "provider": "MockLlmProvider",
    "isDegraded": false
  }
}
```

---

### 2. `POST /debug/personalization`

Inspects how the Context Engine interprets a question and selects context **without invoking the LLM**.

#### Request:

```bash
curl -X POST http://localhost:3000/debug/personalization \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user_101",
    "question": "How does this month look for my relationship?"
  }'
```

#### Response:

```json
{
  "intent": "relationship",
  "selectedContext": [
    "7th House (Relationships/Marriage)",
    "Relationship Horoscope",
    "Moon Sign (Chandra Rashi)",
    "Current Dasha"
  ],
  "excludedContext": ["Career Horoscope", "10th House (Karma/Profession)"],
  "language": "English",
  "tone": "motivational",
  "maxWords": 300,
  "confidence": "HIGH",
  "isDegraded": false,
  "assembledPrompt": {
    "systemDirective": "Provide compassionate, emotionally intelligent guidance regarding romantic partnerships...",
    "contextText": "- [7th House (Relationships/Marriage)]: Lord is Mars, Strength is Weak.\n- [Relationship Horoscope]: \"Communication with your partner improves.\"\n- [Moon Sign (Chandra Rashi)]: Moon Sign (Rashi): Scorpio.\n- [Current Dasha]: Current Dasha: Mahadasha of Rahu, Antardasha of Mars."
  }
}
```

---

### 3. `GET /health`

```bash
curl http://localhost:3000/health
```

---

## ⚖️ Engineering Trade-Offs & Decisions

| Decision               | Chosen Approach                                               | Alternative Considered                                    | Rationale                                                                                                                                                                        |
| :--------------------- | :------------------------------------------------------------ | :-------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Intent Detection**   | Regex / Keyword Pattern Classifier behind `IIntentClassifier` | LLM-based Intent Router (e.g. calling Gemini to classify) | Eliminates a redundant LLM round-trip, saving 300–600ms of latency and cutting API token cost by 50%. The interface allows swapping in an embedding semantic router when needed. |
| **Caching**            | In-memory TTL Cache with active expiry                        | Redis / Memcached                                         | Avoids requiring Docker/external infrastructure for local review. Implements an `ICacheService` interface that can be pointed to Redis with zero engine changes.                 |
| **Confidence Scoring** | Deterministic rule completeness metric                        | LLM self-evaluation ("Rate your confidence 1-10")         | LLMs suffer from overconfidence and hallucinate reliability. System-level source tracking guarantees objective confidence based on real upstream data availability.              |
| **Testing**            | Node.js Native Test Runner (`node:test` + `node:assert`)      | Jest / Vitest                                             | Native runner requires 0 external dev dependencies, has no ESM/CommonJS transformation issues, and runs in <3 seconds.                                                           |

---

## 🔍 Assumptions

1. **User Profile Persistence**: User profiles and birth details change infrequently; thus, aggressive caching (1 hour) is safe.
2. **Panchang Validity**: Panchang calculations are valid across the calendar date; caching daily panchang avoids repetitive astronomical math.
3. **Partial Failure Tolerance**: An astrological consultation is still valuable even if Kundli or Panchang is momentarily unreachable, provided the user is transparently informed of degraded confidence rather than receiving an outright `500 Internal Server Error`.

---

## 🔭 Retrospective & Future Improvements

### What Was Intentionally Simplified:

1. **Token Counting**: Prompt token estimation uses character ratio heuristic `Math.ceil(chars / 4)` instead of compiling native C++ tokenizer libraries (`tiktoken`).
2. **Cache Eviction**: The in-memory cache uses lazy TTL expiration on read rather than a background LRU eviction worker.
3. **Intent Ambiguity**: Questions spanning multiple intents (e.g. _"Will my new job affect my marriage?"_) resolve to the first matching rule rather than triggering multi-intent blend mode.

### What I Would Improve With Another Day of Development:

1. **Semantic / Embedding-Based Router**: Use a local embedding model (e.g. lightweight vector similarity) to classify nuanced intents that keyword rules might miss.
2. **Multi-Intent Blending**: Allow composite intents (e.g. `career + relationship`) to merge context rules dynamically.
3. **Distributed Redis & Circuit Breakers**: Implement Redis cluster caching and integrate stateful circuit breakers (`opossum`) with exponential backoff and half-open probes.
4. **Streaming Responses**: Enable Server-Sent Events (SSE) or WebSocket streaming for instant token delivery to frontend clients.

### Production Concerns Left Out:

- Authentication & Authorization (e.g., JWT validation, API key rate-limiting).
- Distributed tracing (OpenTelemetry / Jaeger).
- Horizontal scaling orchestration (Kubernetes manifests, health probes).
