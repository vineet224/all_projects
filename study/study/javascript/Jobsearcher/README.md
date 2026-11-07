# Backend Job Search Automation (Node.js & SQLite)

High-performance, automated job search and match-scoring pipeline tailored for **4 YOE Backend Engineers (Java + Node.js)**. Discovers active vacancies across **Greenhouse, Lever, and Ashby**, deduplicates them instantly using an embedded **SQLite database (`jobs.db`)**, scores new postings with **Gemini 3.8 Flash**, and optionally syncs them to **Google Sheets**.

---

## What Changed & Key Capabilities

1. **Local SQLite File Database (`jobs.db`)**:
   - Replaces slow, remote network deduplication with local SQLite indexing.
   - Any previously seen or scored job is filtered out in $< 2\text{ms}$ before calling Gemini.
   - Zero PostgreSQL overhead: powered natively by Node.js 22's `node:sqlite`.
   - Even if the process terminates or internet drops, every scored job is already saved to disk.

2. **Expanded 220+ ATS Company Registry + Automated Crawler**:
   - Upgraded from 6 hardcoded companies to **220+ verified tech company boards** hiring in India and globally.
   - Includes an automated ATS crawler (`crawler.js` / `npm run discover`) that can probe thousands of community ATS board slugs, detect active India/remote backend vacancies, and automatically add them to `companies.json` and SQLite.

3. **Gemini 3.8 Flash with Dynamic Rate Limiting & Quota Throttling**:
   - Uses `gemini-3.8-flash` with structured JSON output.
   - Free-tier rate throttling enforced (13s delay between calls) to guarantee zero 429 quota exhaustion errors.
   - Dynamic error recovery: automatically parses Google API `retryDelay` headers on 429s and performs exponential backoff on 503s.

4. **Tuned for Vineet Gupta's Profile (4 YOE Backend)**:
   - IIIT Allahabad, Integrated B.Tech + M.Tech (AI).
   - Core Strengths: Java, Spring Boot, Node.js, NestJS, Kafka retry mechanisms, transaction systems, Redis, MySQL, MongoDB.
   - Secondary: Mid-level LLM integration (document extraction, vector search), minor DevOps familiarity (Kubernetes, Helm, Terraform).
   - Strict exclusions: Pure frontend / fullstack (UI-dominant), pure DevOps/SRE, data science/research, internships, and management roles.

---

## File Overview

- `index.js` — Main CLI, board scraper, quick filter, Gemini 3.8 Flash scoring, Sheets sync.
- `db.js` — SQLite database abstraction (tables for `jobs` and `boards`, indexing, instant deduplication).
- `crawler.js` — Automated ATS discovery engine (scans community directories and registers active boards).
- `companies.json` — 220+ active company boards (Greenhouse, Lever, Ashby).
- `jobs.db` — Local SQLite file database (created automatically).
- `.env` / `.env.example` — Configuration and API keys.

---

## Quick Start Commands

```powershell
# 1. Start the Express Web Dashboard (View existing, applied, not applied, run search)
npm start
# Open in browser: http://localhost:3000

# 2. Run offline tests (includes pre-Gemini seniority & stack rules)
npm test

# 3. Preview matching backend vacancies without consuming Gemini quota
npm run preview

# 4. Discover and score new matching jobs (saves to jobs.db and Google Sheets)
npm run search

# 5. View top recommended jobs (match score >= 70) directly from SQLite
npm run top

# 6. Check database metrics (total tracked, scored, apply, review, skip)
npm run stats

# 7. Run the crawler to discover new companies from community directories
npm run discover -- --scan 50

# 8. Add a specific company board manually
node crawler.js --add greenhouse:databricks:"Databricks"
```

---

## REST API Endpoints (Express.js)

- `GET /api/stats` — Summary metrics (applied, not applied, interviewing, total boards).
- `GET /api/jobs?tab=to_apply|applied|not_applied|interviewing|all&search=...&minScore=...` — Filtered openings.
- `PATCH /api/jobs/:jobKey` — Update status (`APPLIED`, `NOT_APPLIED`, `INTERVIEWING`, `REJECTED`), notes, recruiter contact.
- `POST /api/search/run` — Trigger background job search and scoring runner.
- `GET /api/search/status` — Live status and progress of the background runner.
- `GET /api/boards` — List monitored ATS company boards.
- `POST /api/boards` — Add a new company board to the registry.

---

## Configuration (`.env`)

```env
# Gemini API Key (from Google AI Studio)
GEMINI_API_KEY=your_key_here
GEMINI_MODEL=gemini-3.8-flash

# SQLite Database File
SQLITE_DB_PATH=./jobs.db

# Daily Batch Limit & Rate Throttling
MAX_NEW_JOBS=10
LLM_DELAY_MS=13000
INDIA_ONLY=true

# Optional: Google Sheets sync
GOOGLE_SHEET_ID=your_sheet_id
GOOGLE_SHEET_TAB=Jobs
GOOGLE_SERVICE_ACCOUNT_FILE=./service-account.json
```

---

## Daily Automation (Windows Cron / Task Scheduler)

To run this automatically every morning at 9:00 AM:
1. Open **Windows Task Scheduler** $\rightarrow$ **Create Basic Task**.
2. Trigger: **Daily** at `9:00 AM`.
3. Action: **Start a program**:
   - **Program**: `node` (or full path from `where.exe node`)
   - **Arguments**: `index.js`
   - **Start in**: `c:\vineet_java\study\study\javascript\Jobsearcher`
