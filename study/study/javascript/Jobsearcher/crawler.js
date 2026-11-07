import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { getDb, upsertBoard } from './db.js';

const INDIA_LOCATIONS_REGEX = /(india|bengaluru|bangalore|hyderabad|gurugram|gurgaon|noida|delhi|pune|mumbai|chennai|jaipur|kolkata|ahmedabad|kochi|remote|anywhere|worldwide)/i;
const BACKEND_TITLE_REGEX = /(back[ -]?end|server[ -]?side|software (development )?engineer|software developer|sde\b|platform engineer|java developer|node\.?js)/i;

function boardUrl({ ats, board }) {
  const token = encodeURIComponent(board);
  if (ats === 'greenhouse')
    return `https://boards-api.greenhouse.io/v1/boards/${token}/jobs?content=false`;
  if (ats === 'lever')
    return `https://api.lever.co/v0/postings/${token}?mode=json`;
  if (ats === 'ashby')
    return `https://api.ashbyhq.com/posting-api/job-board/${token}`;
  throw new Error(`Unsupported ATS: ${ats}`);
}

async function fetchBoard(ats, board) {
  try {
    const url = boardUrl({ ats, board });
    const response = await fetch(url, {
      headers: { Accept: 'application/json', 'User-Agent': 'JobSearcherCrawler/1.0' },
      signal: AbortSignal.timeout(6000),
    });
    if (!response.ok) return null;
    const json = await response.json();
    let jobs = [];
    if (ats === 'greenhouse' || ats === 'ashby') jobs = json.jobs || [];
    else jobs = Array.isArray(json) ? json : [];

    let indiaJobs = 0;
    let backendJobs = 0;

    for (const j of jobs) {
      let title = '', location = '';
      if (ats === 'greenhouse') {
        title = j.title || '';
        location = j.location?.name || '';
      } else if (ats === 'lever') {
        title = j.text || '';
        location = j.categories?.allLocations?.join(' / ') || j.categories?.location || '';
      } else {
        title = j.title || '';
        location = [j.location, ...(j.secondaryLocations || []).map(x => x.location)].filter(Boolean).join(' / ');
      }

      if (INDIA_LOCATIONS_REGEX.test(location)) {
        indiaJobs++;
        if (BACKEND_TITLE_REGEX.test(title)) backendJobs++;
      }
    }

    return {
      ats,
      board,
      totalJobs: jobs.length,
      indiaJobs,
      backendJobs,
      isValid: true,
    };
  } catch {
    return null;
  }
}

async function scanCommunitySlugs(limit = 100) {
  console.log(`[Crawler] Fetching community ATS directories to discover new boards (limit: ${limit})...`);
  const db = getDb();
  const existingRows = JSON.parse(await readFile(resolve('companies.json'), 'utf8'));
  const existingSet = new Set(existingRows.map(c => `${c.ats}:${c.board.toLowerCase()}`));

  let ghSlugs = [], leverSlugs = [], ashbySlugs = [];
  try {
    const [gh, lv, as] = await Promise.all([
      fetch('https://raw.githubusercontent.com/Feashliaa/job-board-aggregator/main/data/greenhouse_companies.json').then(r => r.json()),
      fetch('https://raw.githubusercontent.com/Feashliaa/job-board-aggregator/main/data/lever_companies.json').then(r => r.json()),
      fetch('https://raw.githubusercontent.com/Feashliaa/job-board-aggregator/main/data/ashby_companies.json').then(r => r.json()),
    ]);
    ghSlugs = gh || [];
    leverSlugs = lv || [];
    ashbySlugs = as || [];
  } catch (err) {
    console.error('[Crawler] Failed to download community ATS dumps:', err.message);
    return;
  }

  const pool = [
    ...ashbySlugs.map(s => ({ ats: 'ashby', board: s })),
    ...leverSlugs.map(s => ({ ats: 'lever', board: s })),
    ...ghSlugs.map(s => ({ ats: 'greenhouse', board: s })),
  ].filter(c => !existingSet.has(`${c.ats}:${c.board.toLowerCase()}`));

  console.log(`[Crawler] Unchecked candidate pool size: ${pool.length}`);
  const candidates = pool.slice(0, limit);
  let newlyDiscovered = 0;

  for (let i = 0; i < candidates.length; i += 10) {
    const batch = candidates.slice(i, i + 10);
    const results = await Promise.all(batch.map(c => fetchBoard(c.ats, c.board)));

    for (const res of results) {
      if (res && res.totalJobs > 0) {
        // If it has India postings or backend postings, add it
        const name = res.board.replace(/[-_]/g, ' ').toUpperCase();
        console.log(`  [Discovered] ${res.ats.padEnd(10)} ${name.padEnd(20)} (${res.totalJobs} jobs, ${res.indiaJobs} India, ${res.backendJobs} Backend)`);

        existingRows.push({
          name,
          ats: res.ats,
          board: res.board,
          enabled: true,
        });

        upsertBoard({
          ats: res.ats,
          board: res.board,
          name,
          enabled: true,
          totalJobs: res.totalJobs,
          candidateJobs: res.backendJobs,
        }, db);

        existingSet.add(`${res.ats}:${res.board.toLowerCase()}`);
        newlyDiscovered++;
      }
    }
  }

  if (newlyDiscovered > 0) {
    await writeFile(resolve('companies.json'), JSON.stringify(existingRows, null, 2) + '\n', 'utf8');
    console.log(`[Crawler] Added ${newlyDiscovered} active boards to companies.json and SQLite boards table.`);
  } else {
    console.log('[Crawler] No new active boards found in this batch.');
  }
}

async function addManualBoard(ats, board, name) {
  const cleanAts = ats.toLowerCase().trim();
  const cleanBoard = board.toLowerCase().trim();
  const cleanName = (name || cleanBoard).trim();

  console.log(`[Crawler] Verifying board ${cleanAts}:${cleanBoard}...`);
  const check = await fetchBoard(cleanAts, cleanBoard);
  if (!check || !check.isValid) {
    console.error(`[Crawler] Failed: board '${cleanBoard}' on '${cleanAts}' did not return HTTP 200.`);
    return;
  }

  const db = getDb();
  const existingRows = JSON.parse(await readFile(resolve('companies.json'), 'utf8'));
  const idx = existingRows.findIndex(c => c.ats === cleanAts && c.board.toLowerCase() === cleanBoard);

  if (idx >= 0) {
    console.log(`[Crawler] Board ${cleanAts}:${cleanBoard} is already in registry.`);
  } else {
    existingRows.push({
      name: cleanName,
      ats: cleanAts,
      board: cleanBoard,
      enabled: true,
    });
    await writeFile(resolve('companies.json'), JSON.stringify(existingRows, null, 2) + '\n', 'utf8');
    console.log(`[Crawler] Added '${cleanName}' (${cleanAts}:${cleanBoard}) to companies.json`);
  }

  upsertBoard({
    ats: cleanAts,
    board: cleanBoard,
    name: cleanName,
    enabled: true,
    totalJobs: check.totalJobs,
    candidateJobs: check.backendJobs,
  }, db);

  console.log(`[Crawler] Successfully registered board with ${check.totalJobs} open jobs (${check.indiaJobs} in India).`);
}

async function syncAllToDb() {
  const db = getDb();
  const companies = JSON.parse(await readFile(resolve('companies.json'), 'utf8'));
  for (const c of companies) {
    upsertBoard({
      ats: c.ats,
      board: c.board,
      name: c.name,
      enabled: c.enabled !== false,
    }, db);
  }
  console.log(`[Crawler] Synced ${companies.length} companies to SQLite boards table.`);
}

async function main() {
  const args = process.argv.slice(2);
  if (args.includes('--help') || args.length === 0) {
    console.log('Job Board Discovery & Crawler CLI:');
    console.log('  node crawler.js --scan [limit]   Scan candidate ATS boards from public repositories');
    console.log('  node crawler.js --add <ats>:<slug>[:name]  Add and verify a new company board');
    console.log('  node crawler.js --sync           Sync companies.json into SQLite boards registry');
    console.log('\nExamples:');
    console.log('  node crawler.js --scan 50');
    console.log('  node crawler.js --add ashby:linear:Linear');
    console.log('  node crawler.js --add lever:acceldata:"Acceldata"');
    return;
  }

  if (args.includes('--sync')) {
    await syncAllToDb();
    return;
  }

  const addIdx = args.indexOf('--add');
  if (addIdx >= 0 && args[addIdx + 1]) {
    const parts = args[addIdx + 1].split(':');
    const ats = parts[0];
    const board = parts[1];
    const name = parts[2] || board;
    if (!ats || !board) {
      console.error('Invalid format for --add. Expected <ats>:<board_slug> or <ats>:<board_slug>:<name>');
      return;
    }
    await addManualBoard(ats, board, name);
    return;
  }

  const scanIdx = args.indexOf('--scan');
  if (scanIdx >= 0) {
    const limit = Number(args[scanIdx + 1]) || 50;
    await scanCommunitySlugs(limit);
    return;
  }
}

main().catch(err => {
  console.error('[Crawler FATAL]', err);
  process.exitCode = 1;
});
