import express from 'express';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import 'dotenv/config';
import {
  getDb,
  getJobs,
  updateJobApplication,
  getStats,
  getBoards,
  upsertBoard,
} from './db.js';
import { runJobSearch } from './index.js';

const __dirname = resolve(fileURLToPath(import.meta.url), '..');
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());
app.use(express.static(resolve(__dirname, 'public')));

// Global state for background job search runner
let searchRunnerState = {
  isRunning: false,
  status: 'idle',
  message: 'No search in progress',
  startedAt: null,
  progress: null,
  lastResult: null,
};

// --- API Endpoints ---

/**
 * GET /api/stats - Aggregate metrics for dashboard header
 */
app.get('/api/stats', (req, res) => {
  try {
    const stats = getStats();
    res.json(stats);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/jobs - Query jobs with tab, search, ATS, score, and pagination filters
 */
app.get('/api/jobs', (req, res) => {
  try {
    const {
      tab = 'to_apply',
      search = '',
      ats = 'ALL',
      minScore,
      limit = 100,
      offset = 0
    } = req.query;

    let applicationStatus;
    let recommendation;
    let scoreFilter = minScore ? Number(minScore) : undefined;

    switch (tab) {
      case 'to_apply':
        // High match jobs not yet applied
        applicationStatus = 'NOT_APPLIED';
        if (scoreFilter === undefined) scoreFilter = 65;
        break;
      case 'not_applied':
        applicationStatus = 'NOT_APPLIED';
        break;
      case 'applied':
        applicationStatus = 'APPLIED';
        break;
      case 'interviewing':
        applicationStatus = 'INTERVIEWING';
        break;
      case 'rejected':
        applicationStatus = 'REJECTED';
        break;
      case 'all':
      default:
        applicationStatus = 'ALL';
        break;
    }

    const result = getJobs({
      applicationStatus,
      recommendation,
      minScore: scoreFilter,
      search,
      ats,
      limit: Math.min(200, Number(limit)),
      offset: Number(offset)
    });

    const stats = getStats();

    res.json({
      total: result.total,
      jobs: result.jobs,
      stats,
      tab
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * PATCH /api/jobs/:jobKey - Update application status, notes, or recruiter contact
 */
app.patch('/api/jobs/:jobKey', (req, res) => {
  try {
    const { jobKey } = req.params;
    const { applicationStatus, notes, recruiterEmail } = req.body;

    const updated = updateJobApplication(jobKey, {
      applicationStatus,
      notes,
      recruiterEmail
    });

    if (!updated) {
      return res.status(404).json({ error: 'Job not found or no changes made' });
    }

    res.json({ success: true, jobKey, applicationStatus, notes, recruiterEmail });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/boards - List monitored ATS boards
 */
app.get('/api/boards', (req, res) => {
  try {
    const boards = getBoards();
    res.json({ total: boards.length, boards });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * POST /api/boards - Add a new ATS board
 */
app.post('/api/boards', (req, res) => {
  try {
    const { ats, board, name } = req.body;
    if (!ats || !board) {
      return res.status(400).json({ error: 'ats and board are required' });
    }
    upsertBoard({
      ats: ats.toLowerCase().trim(),
      board: board.toLowerCase().trim(),
      name: (name || board).trim(),
      enabled: true
    });
    res.json({ success: true, message: `Added board ${ats}:${board}` });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * POST /api/search/run - Trigger the job discovery & Gemini scoring runner in the background
 */
app.post('/api/search/run', async (req, res) => {
  if (searchRunnerState.isRunning) {
    return res.status(409).json({
      status: 'already_running',
      message: 'A job search is already running.',
      state: searchRunnerState
    });
  }

  const maxJobs = Math.max(1, Number(req.body.maxJobs || process.env.MAX_NEW_JOBS || 5));

  searchRunnerState = {
    isRunning: true,
    status: 'starting',
    message: 'Starting job discovery and scoring...',
    startedAt: new Date().toISOString(),
    progress: { current: 0, total: 0 },
    lastResult: null
  };

  // Launch in background
  (async () => {
    try {
      const result = await runJobSearch({
        maxJobs,
        onProgress: (p) => {
          searchRunnerState.status = p.status;
          searchRunnerState.message = p.message || `Scoring ${p.current || 0}/${p.total || 0}...`;
          if (p.current) {
            searchRunnerState.progress = { current: p.current, total: p.total };
          }
        }
      });
      searchRunnerState.isRunning = false;
      searchRunnerState.status = 'completed';
      searchRunnerState.message = result.message;
      searchRunnerState.lastResult = result;
    } catch (err) {
      searchRunnerState.isRunning = false;
      searchRunnerState.status = 'failed';
      searchRunnerState.message = `Search error: ${err.message}`;
    }
  })();

  res.json({
    status: 'started',
    message: `Job search started for up to ${maxJobs} new positions.`,
    state: searchRunnerState
  });
});

/**
 * GET /api/search/status - Check background search progress
 */
app.get('/api/search/status', (req, res) => {
  res.json(searchRunnerState);
});

// Fallback to index.html for SPA routing (Express 5 compatible)
app.use((req, res) => {
  res.sendFile(resolve(__dirname, 'public', 'index.html'));
});

// Start Express server
const server = app.listen(PORT, () => {
  console.log(`\n======================================================`);
  console.log(`  Job Searcher Dashboard running at:`);
  console.log(`  >>> http://localhost:${PORT}`);
  console.log(`======================================================\n`);
});

server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    const nextPort = Number(PORT) + 1;
    console.warn(`Port ${PORT} is in use. Trying port ${nextPort}...`);
    app.listen(nextPort, () => {
      console.log(`Job Searcher Dashboard running at http://localhost:${nextPort}`);
    });
  } else {
    console.error('Server error:', err);
  }
});
