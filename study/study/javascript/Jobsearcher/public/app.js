// AutoJob Tracker - Frontend Application Logic
document.addEventListener('DOMContentLoaded', () => {
  // State
  let currentTab = 'to_apply';
  let activeJobs = [];
  let currentModalJob = null;
  let searchPollingTimer = null;

  // DOM Elements
  const jobsContainer = document.getElementById('jobs-container');
  const emptyState = document.getElementById('empty-state');
  const inputSearch = document.getElementById('input-search');
  const selectAts = document.getElementById('select-ats');
  const selectMinScore = document.getElementById('select-min-score');
  const tabButtons = document.querySelectorAll('.tab-btn');
  const kpiCards = document.querySelectorAll('.kpi-card');
  const btnRunSearch = document.getElementById('btn-run-search');
  const btnRefresh = document.getElementById('btn-refresh');

  // KPI elements
  const kpiToApply = document.getElementById('kpi-to-apply');
  const kpiApplied = document.getElementById('kpi-applied');
  const kpiInterviewing = document.getElementById('kpi-interviewing');
  const kpiTotal = document.getElementById('kpi-total');

  // Tab badges
  const badgeToApply = document.getElementById('badge-tab-to-apply');
  const badgeNotApplied = document.getElementById('badge-tab-not-applied');
  const badgeApplied = document.getElementById('badge-tab-applied');
  const badgeInterviewing = document.getElementById('badge-tab-interviewing');
  const badgeRejected = document.getElementById('badge-tab-rejected');
  const badgeAll = document.getElementById('badge-tab-all');

  // Search Banner
  const searchBanner = document.getElementById('search-banner');
  const bannerTitle = document.getElementById('banner-title');
  const bannerMessage = document.getElementById('banner-message');
  const bannerProgressContainer = document.getElementById('banner-progress-bar');
  const bannerProgressFill = document.getElementById('banner-progress-fill');
  const statusIndicator = document.getElementById('search-status-indicator');
  const statusIndicatorText = document.getElementById('status-indicator-text');

  // Modal elements
  const jobModal = document.getElementById('job-modal');
  const modalClose = document.getElementById('modal-close');
  const modalCompany = document.getElementById('modal-company');
  const modalTitle = document.getElementById('modal-title');
  const modalLocation = document.getElementById('modal-location');
  const modalAts = document.getElementById('modal-ats');
  const modalFirstSeen = document.getElementById('modal-first-seen');
  const modalScoreNum = document.getElementById('modal-score-num');
  const modalScoreCircle = document.getElementById('modal-score-circle');
  const modalRecommendation = document.getElementById('modal-recommendation');
  const modalReason = document.getElementById('modal-reason');
  const modalTechStack = document.getElementById('modal-tech-stack');
  const modalExpFit = document.getElementById('modal-exp-fit');
  const modalMissing = document.getElementById('modal-missing');
  const modalStatusSelect = document.getElementById('modal-status-select');
  const modalRecruiterInput = document.getElementById('modal-recruiter-input');
  const modalNotesInput = document.getElementById('modal-notes-input');
  const btnModalSave = document.getElementById('btn-modal-save');
  const btnModalApply = document.getElementById('btn-modal-apply');

  // --- API Calls ---

  async function fetchStats() {
    try {
      const res = await fetch('/api/stats');
      if (!res.ok) return;
      const stats = await res.json();

      kpiToApply.textContent = stats.applyJobs || 0;
      kpiApplied.textContent = stats.appliedJobs || 0;
      kpiInterviewing.textContent = stats.interviewingJobs || 0;
      kpiTotal.textContent = stats.totalJobs || 0;

      badgeToApply.textContent = stats.applyJobs || 0;
      badgeNotApplied.textContent = stats.notAppliedJobs || 0;
      badgeApplied.textContent = stats.appliedJobs || 0;
      badgeInterviewing.textContent = stats.interviewingJobs || 0;
      badgeRejected.textContent = stats.skipJobs || 0;
      badgeAll.textContent = stats.totalJobs || 0;
    } catch (err) {
      console.error('Error fetching stats:', err);
    }
  }

  async function fetchJobs() {
    try {
      jobsContainer.innerHTML = `
        <div class="loading-state">
          <div class="spinner"></div>
          <p>Querying SQLite database...</p>
        </div>
      `;
      emptyState.classList.add('hidden');

      const params = new URLSearchParams({
        tab: currentTab,
        search: inputSearch.value.trim(),
        ats: selectAts.value,
      });

      if (selectMinScore.value) {
        params.append('minScore', selectMinScore.value);
      }

      const res = await fetch(`/api/jobs?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();

      activeJobs = data.jobs || [];
      renderJobs(activeJobs);
      await fetchStats();
    } catch (err) {
      jobsContainer.innerHTML = `<p style="color: #F87171; text-align: center; grid-column: 1/-1;">Failed to load jobs: ${err.message}</p>`;
    }
  }

  async function updateJobStatus(jobKey, applicationStatus, notes, recruiterEmail) {
    try {
      const res = await fetch(`/api/jobs/${encodeURIComponent(jobKey)}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ applicationStatus, notes, recruiterEmail })
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      await fetchStats();
      return true;
    } catch (err) {
      alert(`Error updating job: ${err.message}`);
      return false;
    }
  }

  // --- Rendering ---

  function renderJobs(jobs) {
    if (!jobs || !jobs.length) {
      jobsContainer.innerHTML = '';
      emptyState.classList.remove('hidden');
      return;
    }

    emptyState.classList.add('hidden');
    jobsContainer.innerHTML = jobs.map(job => {
      const score = job.match_score ?? '-';
      const scoreClass = score >= 80 ? 'score-high' : (score >= 65 ? 'score-mid' : 'score-low');
      const rec = job.recommendation || 'REVIEW';
      const recClass = rec === 'APPLY' ? 'rec-apply' : (rec === 'SKIP' ? 'rec-skip' : 'rec-review');
      const atsClass = `ats-${(job.ats || 'greenhouse').toLowerCase()}`;
      const appStatus = job.application_status || 'NOT_APPLIED';
      const initial = (job.company || 'C').charAt(0).toUpperCase();

      return `
        <article class="job-card" data-key="${escapeHtml(job.job_key)}">
          <div>
            <div class="job-card-top">
              <div class="company-badge-wrap">
                <div class="company-avatar">${escapeHtml(initial)}</div>
                <div>
                  <span class="company-name">${escapeHtml(job.company)}</span>
                </div>
              </div>
              <div class="score-pill ${scoreClass}">
                <span class="recommendation-badge ${recClass}">${escapeHtml(rec)}</span>
                <span>${score}</span>
              </div>
            </div>

            <a href="${escapeHtml(job.job_url)}" target="_blank" rel="noopener noreferrer" class="job-title-link">
              ${escapeHtml(job.title)}
            </a>

            <div class="meta-row">
              <span class="meta-tag ${atsClass}">${escapeHtml(job.ats.toUpperCase())}</span>
              <span class="meta-tag">📍 ${escapeHtml(job.location || 'Remote/India')}</span>
            </div>

            ${job.reason ? `<p class="job-reason">${escapeHtml(job.reason)}</p>` : ''}
            ${job.tech_stack_fit ? `<div class="job-tech-fit"><strong>Fit:</strong> ${escapeHtml(job.tech_stack_fit)}</div>` : ''}
          </div>

          <div class="card-footer">
            <select class="status-dropdown" data-key="${escapeHtml(job.job_key)}">
              <option value="NOT_APPLIED" ${appStatus === 'NOT_APPLIED' ? 'selected' : ''}>⚪ Not Applied</option>
              <option value="APPLIED" ${appStatus === 'APPLIED' ? 'selected' : ''}>🔵 Applied</option>
              <option value="INTERVIEWING" ${appStatus === 'INTERVIEWING' ? 'selected' : ''}>🟣 Interviewing</option>
              <option value="REJECTED" ${appStatus === 'REJECTED' ? 'selected' : ''}>🔴 Rejected</option>
            </select>

            <div class="card-btns">
              <button class="btn-icon btn-view-details" data-key="${escapeHtml(job.job_key)}" title="View Analysis & Notes">
                Details &amp; Notes
              </button>
              <a href="${escapeHtml(job.job_url)}" target="_blank" rel="noopener noreferrer" class="btn-icon" title="Open Job Posting">
                Apply &rarr;
              </a>
            </div>
          </div>
        </article>
      `;
    }).join('');

    // Attach card event listeners
    document.querySelectorAll('.status-dropdown').forEach(select => {
      select.addEventListener('change', async (e) => {
        const key = e.target.getAttribute('data-key');
        const newStatus = e.target.value;
        const ok = await updateJobStatus(key, newStatus);
        if (ok && currentTab !== 'all') {
          // If we changed status away from current tab filter, remove card with subtle transition
          const card = document.querySelector(`.job-card[data-key="${CSS.escape(key)}"]`);
          if (card && currentTab === 'to_apply' && newStatus === 'APPLIED') {
            card.remove();
            if (!jobsContainer.children.length) emptyState.classList.remove('hidden');
          }
        }
      });
    });

    document.querySelectorAll('.btn-view-details').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const key = e.target.getAttribute('data-key');
        const job = activeJobs.find(j => j.job_key === key);
        if (job) openModal(job);
      });
    });
  }

  // --- Modal Handling ---

  function openModal(job) {
    currentModalJob = job;
    modalCompany.textContent = job.company;
    modalTitle.textContent = job.title;
    modalLocation.textContent = `📍 ${job.location || 'Remote/India'}`;
    modalAts.textContent = job.ats.toUpperCase();
    modalAts.className = `meta-item badge-ats meta-tag ats-${job.ats.toLowerCase()}`;
    modalFirstSeen.textContent = job.first_seen_at ? `Seen: ${new Date(job.first_seen_at).toLocaleDateString()}` : '';

    const score = job.match_score ?? '-';
    modalScoreNum.textContent = score;
    modalRecommendation.textContent = job.recommendation || 'REVIEW';
    modalRecommendation.className = `badge-rec ${job.recommendation === 'APPLY' ? 'rec-apply' : (job.recommendation === 'SKIP' ? 'rec-skip' : 'rec-review')}`;
    modalReason.textContent = job.reason || 'No breakdown available';

    modalTechStack.textContent = job.tech_stack_fit || 'Not specified';
    modalExpFit.textContent = job.experience_fit ? `Experience: ${job.experience_fit}` : '';
    modalMissing.textContent = job.missing_requirements || 'None identified';

    modalStatusSelect.value = job.application_status || 'NOT_APPLIED';
    modalRecruiterInput.value = job.recruiter_email || '';
    modalNotesInput.value = job.notes || '';
    btnModalApply.href = job.job_url;

    jobModal.classList.remove('hidden');
  }

  function closeModal() {
    jobModal.classList.add('hidden');
    currentModalJob = null;
  }

  btnModalSave.addEventListener('click', async () => {
    if (!currentModalJob) return;
    btnModalSave.textContent = 'Saving...';
    const ok = await updateJobStatus(
      currentModalJob.job_key,
      modalStatusSelect.value,
      modalNotesInput.value.trim(),
      modalRecruiterInput.value.trim()
    );
    btnModalSave.textContent = 'Save Notes & Status';
    if (ok) {
      currentModalJob.application_status = modalStatusSelect.value;
      currentModalJob.notes = modalNotesInput.value.trim();
      currentModalJob.recruiter_email = modalRecruiterInput.value.trim();
      closeModal();
      await fetchJobs();
    }
  });

  modalClose.addEventListener('click', closeModal);
  jobModal.addEventListener('click', (e) => {
    if (e.target === jobModal) closeModal();
  });

  // --- Background Search Runner Monitoring ---

  async function checkSearchStatus() {
    try {
      const res = await fetch('/api/search/status');
      if (!res.ok) return;
      const state = await res.json();

      if (state.isRunning) {
        searchBanner.classList.remove('hidden');
        statusIndicator.className = 'status-indicator running';
        statusIndicatorText.textContent = 'Scoring in progress';
        btnRunSearch.disabled = true;
        btnRunSearch.textContent = 'Searching...';

        bannerTitle.textContent = 'Job Search & Scoring in Progress';
        bannerMessage.textContent = state.message;

        if (state.progress && state.progress.total > 0) {
          bannerProgressContainer.classList.remove('hidden');
          const pct = Math.round((state.progress.current / state.progress.total) * 100);
          bannerProgressFill.style.width = `${pct}%`;
        }

        if (!searchPollingTimer) {
          searchPollingTimer = setInterval(checkSearchStatus, 3000);
        }
      } else {
        if (searchPollingTimer) {
          clearInterval(searchPollingTimer);
          searchPollingTimer = null;
        }

        statusIndicator.className = 'status-indicator idle';
        statusIndicatorText.textContent = 'System Idle';
        btnRunSearch.disabled = false;
        btnRunSearch.innerHTML = `
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
          Run Job Search
        `;

        if (state.status === 'completed') {
          bannerTitle.textContent = 'Search Finished';
          bannerMessage.textContent = state.message;
          setTimeout(() => {
            searchBanner.classList.add('hidden');
          }, 6000);
          await fetchJobs();
        } else if (state.status === 'failed') {
          bannerTitle.textContent = 'Search Failed';
          bannerMessage.textContent = state.message;
        } else {
          searchBanner.classList.add('hidden');
        }
      }
    } catch (err) {
      console.error('Error checking search status:', err);
    }
  }

  btnRunSearch.addEventListener('click', async () => {
    const maxJobs = prompt('How many new eligible backend positions to evaluate with Gemini today?', '5');
    if (!maxJobs || isNaN(maxJobs)) return;

    try {
      btnRunSearch.disabled = true;
      const res = await fetch('/api/search/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ maxJobs: Number(maxJobs) })
      });
      const data = await res.json();
      searchBanner.classList.remove('hidden');
      bannerTitle.textContent = 'Job Search Started';
      bannerMessage.textContent = data.message;
      checkSearchStatus();
    } catch (err) {
      alert(`Could not start search: ${err.message}`);
      btnRunSearch.disabled = false;
    }
  });

  // --- Tab and Filter Handlers ---

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      tabButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentTab = btn.getAttribute('data-tab');
      fetchJobs();
    });
  });

  kpiCards.forEach(card => {
    card.addEventListener('click', () => {
      const targetTab = card.getAttribute('data-tab');
      const matchingTabBtn = document.querySelector(`.tab-btn[data-tab="${targetTab}"]`);
      if (matchingTabBtn) matchingTabBtn.click();
    });
  });

  // Debounced search
  let debounceTimeout;
  inputSearch.addEventListener('input', () => {
    clearTimeout(debounceTimeout);
    debounceTimeout = setTimeout(fetchJobs, 250);
  });

  selectAts.addEventListener('change', fetchJobs);
  selectMinScore.addEventListener('change', fetchJobs);
  btnRefresh.addEventListener('click', () => {
    fetchJobs();
    checkSearchStatus();
  });

  function escapeHtml(str) {
    return String(str ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // Initial Load
  fetchJobs();
  checkSearchStatus();
});
