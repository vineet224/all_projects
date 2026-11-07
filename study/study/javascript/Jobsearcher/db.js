import { DatabaseSync } from 'node:sqlite';
import { resolve } from 'node:path';

const DB_PATH = resolve(process.env.SQLITE_DB_PATH || './jobs.db');

let _db = null;

/**
 * Initialize SQLite database connection and create tables/indices if not exists.
 */
export function getDb(path = DB_PATH) {
  if (_db) return _db;

  _db = new DatabaseSync(path);

  // Performance pragmas for local file SQLite
  _db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA synchronous = NORMAL;
  `);

  // Jobs table: tracks all discovered and scored jobs
  _db.exec(`
    CREATE TABLE IF NOT EXISTS jobs (
      job_key TEXT PRIMARY KEY,
      ats TEXT NOT NULL,
      company TEXT NOT NULL,
      title TEXT NOT NULL,
      location TEXT,
      job_url TEXT NOT NULL,
      ats_job_id TEXT,
      description TEXT,
      updated_at TEXT,
      first_seen_at TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'NEW',
      match_score INTEGER,
      recommendation TEXT,
      reason TEXT,
      missing_requirements TEXT,
      experience_fit TEXT,
      tech_stack_fit TEXT,
      application_status TEXT DEFAULT 'NOT_APPLIED',
      applied_at TEXT,
      notes TEXT,
      recruiter_email TEXT
    );
  `);

  // Safe migrations for newly added columns in existing DB
  const existingCols = new Set(_db.prepare('PRAGMA table_info(jobs)').all().map(c => c.name));
  if (!existingCols.has('application_status')) {
    _db.exec(`ALTER TABLE jobs ADD COLUMN application_status TEXT DEFAULT 'NOT_APPLIED';`);
  }
  if (!existingCols.has('applied_at')) {
    _db.exec(`ALTER TABLE jobs ADD COLUMN applied_at TEXT;`);
  }
  if (!existingCols.has('notes')) {
    _db.exec(`ALTER TABLE jobs ADD COLUMN notes TEXT;`);
  }
  if (!existingCols.has('recruiter_email')) {
    _db.exec(`ALTER TABLE jobs ADD COLUMN recruiter_email TEXT;`);
  }

  // Create indices
  _db.exec(`
    CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
    CREATE INDEX IF NOT EXISTS idx_jobs_app_status ON jobs(application_status);
    CREATE INDEX IF NOT EXISTS idx_jobs_score ON jobs(match_score);
    CREATE INDEX IF NOT EXISTS idx_jobs_company ON jobs(company);
    CREATE INDEX IF NOT EXISTS idx_jobs_first_seen ON jobs(first_seen_at);

    -- ATS Boards registry: tracks all monitored companies and boards
    CREATE TABLE IF NOT EXISTS boards (
      ats TEXT NOT NULL,
      board TEXT NOT NULL,
      name TEXT NOT NULL,
      enabled INTEGER NOT NULL DEFAULT 1,
      last_checked_at TEXT,
      total_jobs INTEGER DEFAULT 0,
      candidate_jobs INTEGER DEFAULT 0,
      PRIMARY KEY (ats, board)
    );
  `);

  return _db;
}

/**
 * Returns a Set of all job_keys already recorded in SQLite.
 */
export function getSeenJobKeys(db = getDb()) {
  const rows = db.prepare('SELECT job_key FROM jobs').all();
  return new Set(rows.map(r => r.job_key));
}

/**
 * Check if a single job key has already been seen.
 */
export function isJobSeen(jobKey, db = getDb()) {
  const row = db.prepare('SELECT 1 FROM jobs WHERE job_key = ? LIMIT 1').get(jobKey);
  return Boolean(row);
}

/**
 * Filters an array of jobs, returning only those that do NOT exist in the database.
 */
export function filterUnseenJobs(jobs, db = getDb()) {
  if (!jobs || !jobs.length) return [];
  const seenKeys = getSeenJobKeys(db);
  return jobs.filter(j => !seenKeys.has(j.jobKey));
}

/**
 * Saves or updates a scored job in the database.
 */
export function saveScoredJob(job, evaluation, db = getDb()) {
  const stmt = db.prepare(`
    INSERT INTO jobs (
      job_key, ats, company, title, location, job_url, ats_job_id,
      description, updated_at, first_seen_at, status,
      match_score, recommendation, reason, missing_requirements,
      experience_fit, tech_stack_fit, application_status
    ) VALUES (
      ?, ?, ?, ?, ?, ?, ?,
      ?, ?, ?, ?,
      ?, ?, ?, ?,
      ?, ?, 'NOT_APPLIED'
    )
    ON CONFLICT(job_key) DO UPDATE SET
      match_score = excluded.match_score,
      recommendation = excluded.recommendation,
      reason = excluded.reason,
      missing_requirements = excluded.missing_requirements,
      experience_fit = excluded.experience_fit,
      tech_stack_fit = excluded.tech_stack_fit,
      status = 'SCORED';
  `);

  const missing = Array.isArray(evaluation.missingRequirements)
    ? evaluation.missingRequirements.join('; ')
    : String(evaluation.missingRequirements || '');

  stmt.run(
    job.jobKey,
    job.ats,
    job.company,
    job.title,
    job.location || '',
    job.jobUrl,
    job.atsJobId || '',
    job.description ? job.description.slice(0, 10000) : '',
    job.updatedAt || '',
    new Date().toISOString(),
    'SCORED',
    evaluation.matchScore ?? null,
    evaluation.recommendation || '',
    evaluation.reason || '',
    missing,
    evaluation.experienceFit || '',
    evaluation.techStackFit || ''
  );
}

/**
 * Marks a job as discovered/skipped (e.g. filtered by senior/frontend rule filter)
 * to avoid re-evaluating on future runs.
 */
export function recordSkippedJob(job, status = 'SKIPPED_RULE', reason = '', db = getDb()) {
  const stmt = db.prepare(`
    INSERT INTO jobs (
      job_key, ats, company, title, location, job_url, ats_job_id,
      description, updated_at, first_seen_at, status, reason
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(job_key) DO UPDATE SET
      status = excluded.status,
      reason = excluded.reason;
  `);

  stmt.run(
    job.jobKey,
    job.ats,
    job.company,
    job.title,
    job.location || '',
    job.jobUrl,
    job.atsJobId || '',
    '',
    job.updatedAt || '',
    new Date().toISOString(),
    status,
    reason
  );
}

/**
 * Update job application status, notes, or recruiter contact.
 */
export function updateJobApplication(jobKey, updates, db = getDb()) {
  const fields = [];
  const values = [];

  if (updates.applicationStatus !== undefined) {
    fields.push('application_status = ?');
    values.push(updates.applicationStatus);
    if (updates.applicationStatus === 'APPLIED') {
      fields.push('applied_at = ?');
      values.push(new Date().toISOString());
    }
  }
  if (updates.notes !== undefined) {
    fields.push('notes = ?');
    values.push(updates.notes);
  }
  if (updates.recruiterEmail !== undefined) {
    fields.push('recruiter_email = ?');
    values.push(updates.recruiterEmail);
  }

  if (!fields.length) return false;

  values.push(jobKey);
  const sql = `UPDATE jobs SET ${fields.join(', ')} WHERE job_key = ?`;
  const result = db.prepare(sql).run(...values);
  return result.changes > 0;
}

/**
 * Query jobs with pagination and multi-criteria filters.
 */
export function getJobs({
  applicationStatus,
  recommendation,
  minScore,
  search,
  ats,
  limit = 50,
  offset = 0
} = {}, db = getDb()) {
  const conditions = [];
  const params = [];

  if (applicationStatus && applicationStatus !== 'ALL') {
    conditions.push("COALESCE(application_status, 'NOT_APPLIED') = ?");
    params.push(applicationStatus);
  }
  if (recommendation && recommendation !== 'ALL') {
    conditions.push('recommendation = ?');
    params.push(recommendation);
  }
  if (minScore !== undefined && minScore !== null && minScore !== '') {
    conditions.push('match_score >= ?');
    params.push(Number(minScore));
  }
  if (ats && ats !== 'ALL') {
    conditions.push('ats = ?');
    params.push(ats.toLowerCase());
  }
  if (search && search.trim()) {
    conditions.push('(company LIKE ? OR title LIKE ? OR tech_stack_fit LIKE ? OR reason LIKE ?)');
    const q = `%${search.trim()}%`;
    params.push(q, q, q, q);
  }

  // By default, exclude jobs skipped by hard rule unless specifically requested
  if (!applicationStatus && !search) {
    conditions.push("status != 'SKIPPED_RULE'");
  }

  const whereClause = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
  const countRow = db.prepare(`SELECT count(*) as total FROM jobs ${whereClause}`).get(...params);
  const total = countRow ? countRow.total : 0;

  const sql = `
    SELECT * FROM jobs
    ${whereClause}
    ORDER BY
      CASE WHEN COALESCE(application_status, 'NOT_APPLIED') = 'APPLIED' THEN 2 ELSE 1 END,
      COALESCE(match_score, 0) DESC,
      first_seen_at DESC
    LIMIT ? OFFSET ?
  `;
  const rows = db.prepare(sql).all(...params, limit, offset);
  return { total, jobs: rows };
}

/**
 * Upsert board into the boards registry.
 */
export function upsertBoard(boardConfig, db = getDb()) {
  const stmt = db.prepare(`
    INSERT INTO boards (ats, board, name, enabled, last_checked_at, total_jobs, candidate_jobs)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(ats, board) DO UPDATE SET
      name = excluded.name,
      enabled = excluded.enabled,
      last_checked_at = excluded.last_checked_at,
      total_jobs = excluded.total_jobs,
      candidate_jobs = excluded.candidate_jobs;
  `);

  stmt.run(
    boardConfig.ats,
    boardConfig.board,
    boardConfig.name,
    boardConfig.enabled !== false ? 1 : 0,
    boardConfig.lastCheckedAt || new Date().toISOString(),
    boardConfig.totalJobs || 0,
    boardConfig.candidateJobs || 0
  );
}

/**
 * Load all boards from database.
 */
export function getBoards(db = getDb()) {
  return db.prepare('SELECT ats, board, name, enabled, total_jobs, candidate_jobs, last_checked_at FROM boards ORDER BY candidate_jobs DESC, name ASC').all();
}

/**
 * Get summary stats from SQLite.
 */
export function getStats(db = getDb()) {
  const totalJobs = db.prepare('SELECT count(*) as count FROM jobs').get().count;
  const scoredJobs = db.prepare("SELECT count(*) as count FROM jobs WHERE status = 'SCORED'").get().count;
  const applyJobs = db.prepare("SELECT count(*) as count FROM jobs WHERE recommendation = 'APPLY'").get().count;
  const reviewJobs = db.prepare("SELECT count(*) as count FROM jobs WHERE recommendation = 'REVIEW'").get().count;
  const skipJobs = db.prepare("SELECT count(*) as count FROM jobs WHERE recommendation = 'SKIP'").get().count;
  const appliedJobs = db.prepare("SELECT count(*) as count FROM jobs WHERE application_status = 'APPLIED'").get().count;
  const notAppliedJobs = db.prepare("SELECT count(*) as count FROM jobs WHERE COALESCE(application_status, 'NOT_APPLIED') = 'NOT_APPLIED' AND status = 'SCORED'").get().count;
  const interviewingJobs = db.prepare("SELECT count(*) as count FROM jobs WHERE application_status = 'INTERVIEWING'").get().count;
  const totalBoards = db.prepare('SELECT count(*) as count FROM boards').get().count;

  return {
    totalJobs,
    scoredJobs,
    applyJobs,
    reviewJobs,
    skipJobs,
    appliedJobs,
    notAppliedJobs,
    interviewingJobs,
    totalBoards,
  };
}

/**
 * Retrieve top recommended jobs for review.
 */
export function getTopJobs({ minScore = 70, limit = 20 } = {}, db = getDb()) {
  const stmt = db.prepare(`
    SELECT job_key, company, title, location, job_url, match_score, recommendation, reason, first_seen_at, application_status
    FROM jobs
    WHERE status = 'SCORED' AND match_score >= ?
    ORDER BY match_score DESC, first_seen_at DESC
    LIMIT ?
  `);
  return stmt.all(minScore, limit);
}

/**
 * Close database connection.
 */
export function closeDb() {
  if (_db) {
    _db.close();
    _db = null;
  }
}
