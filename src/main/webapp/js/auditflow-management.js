(function() {
    var allLogs = [];
    var currentInsights = [];
    var currentSummary = {};
    var currentPage = 1;
    var pageSize = 100;
    var totalLogs = 0;
    var totalPages = 1;
    var sortField = 'timestampMs';
    var sortAsc = false;
    var anomalyConfig = {};
    var displayTimeZone = 'UTC';
    var displayToday = '';
    var anomalyActions = [];
    var anomalyDismissedKey = 'auditflow-anomaly-dismissed-' + new Date().toISOString().slice(0, 10);
    var anomalyDismissed = sessionStorage.getItem(anomalyDismissedKey) === 'true';

    function loadLogs(resetPage) {
        if (resetPage) {
            currentPage = 1;
        }

        var tbody = document.getElementById('tbody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="7">Loading...</td></tr>';
        }

        var params = buildRequestParams(true);
        fetch('api?' + params.toString())
            .then(function(response) { return response.json(); })
            .then(function(data) {
                allLogs = data.logs || [];
                currentInsights = data.insights || [];
                currentSummary = data.summary || {};
                anomalyConfig = data.anomalyConfig || {};
                totalLogs = data.total || 0;
                totalPages = data.totalPages || 1;
                currentPage = data.page || currentPage;
                displayTimeZone = data.displayTimeZone || 'UTC';
                displayToday = data.displayToday || '';

                renderStats(currentSummary);
                computeRiskPanel(currentSummary);
                computeAnomalies(allLogs);
                scaleStatsGrid();
                renderTable(allLogs);
                updateResultCount();
                updateTimeZoneLabel();
                updatePaginationBar(totalPages);
                refreshInsightsIfVisible();

                if (totalLogs > 0) {
                    var banner = document.getElementById('onboardingBanner');
                    if (banner) {
                        banner.style.display = 'none';
                    }
                }
            })
            .catch(function() {
                var tbodyEl = document.getElementById('tbody');
                if (tbodyEl) {
                    tbodyEl.innerHTML = '<tr><td colspan="7">Error loading logs.</td></tr>';
                }
            });
    }

    function buildRequestParams(includePaging) {
        var params = new URLSearchParams();
        params.set('searchColumn', document.getElementById('searchColumn').value || 'all');
        params.set('searchText', document.getElementById('searchText').value || '');
        params.set('action', document.getElementById('filterAction').value || '');

        var checkedViewMode = document.querySelector('input[name="viewMode"]:checked');
        params.set('viewMode', checkedViewMode ? checkedViewMode.value : 'user-only');
        params.set('sortField', sortField || 'timestampMs');
        params.set('sortDir', sortAsc ? 'asc' : 'desc');

        var from = document.getElementById('dateFrom').value || '';
        var to = document.getElementById('dateTo').value || '';
        if (from) {
            params.set('dateFrom', from);
        }
        if (to) {
            params.set('dateTo', to);
        }

        if (includePaging !== false) {
            params.set('page', currentPage);
            params.set('pageSize', pageSize);
        }
        return params;
    }

    function updateResultCount() {
        var label = document.getElementById('resultCount');
        if (!label) {
            return;
        }
        if (totalLogs === 0) {
            label.textContent = '0 events';
            return;
        }
        if (pageSize <= 0) {
            label.textContent = totalLogs + ' events';
            return;
        }
        label.textContent = 'Showing ' + allLogs.length + ' of ' + totalLogs + ' events';
    }

    function updateTimeZoneLabel() {
        var label = document.getElementById('displayTimeZoneLabel');
        if (label) {
            label.textContent = 'Audit Dashboard';
        }
    }

    function applySearch() {
        loadLogs(true);
    }

    function sortBy(field) {
        if (sortField === field) {
            sortAsc = !sortAsc;
        } else {
            sortField = field;
            sortAsc = true;
        }
        loadLogs(true);
    }

    function updatePaginationBar(totalPagesCount) {
        var bar = document.getElementById('paginationBar');
        if (!bar) {
            return;
        }
        if (pageSize <= 0 || totalPagesCount <= 1 || totalLogs === 0) {
            bar.style.display = 'none';
            return;
        }
        bar.style.display = 'flex';
        document.getElementById('pageInfo').textContent = 'Page ' + currentPage + ' of ' + totalPagesCount;
        document.getElementById('btnFirst').disabled = currentPage <= 1;
        document.getElementById('btnPrev').disabled = currentPage <= 1;
        document.getElementById('btnNext').disabled = currentPage >= totalPagesCount;
        document.getElementById('btnLast').disabled = currentPage >= totalPagesCount;
    }

    function changePageSize() {
        pageSize = parseInt(document.getElementById('pageSize').value, 10) || 0;
        currentPage = 1;
        loadLogs(false);
    }

    function firstPage() {
        if (currentPage === 1) {
            return;
        }
        currentPage = 1;
        loadLogs(false);
    }

    function prevPage() {
        if (currentPage <= 1) {
            return;
        }
        currentPage--;
        loadLogs(false);
    }

    function nextPage() {
        if (currentPage >= totalPages) {
            return;
        }
        currentPage++;
        loadLogs(false);
    }

    function lastPage() {
        if (currentPage === totalPages) {
            return;
        }
        currentPage = totalPages;
        loadLogs(false);
    }

    function renderTable(logs) {
        var tbody = document.getElementById('tbody');
        tbody.innerHTML = '';
        if (!logs || logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty-state">'
                + '<h2>No audit events found</h2>'
                + '<p>Events will appear here as users log in, run builds, and modify configuration. Try adjusting your filters or check back later.</p>'
                + '</td></tr>';
            return;
        }

        for (var i = 0; i < logs.length; i++) {
            var entry = logs[i];
            var row = document.createElement('tr');
            var severityClass = severityBadgeClass(entry.severity, entry.action);
            row.innerHTML = ''
                + '<td class="timestamp-cell">' + esc(entry.readable || '') + '</td>'
                + '<td><strong>' + esc(entry.user || '') + '</strong></td>'
                + '<td><span class="badge ' + severityClass + '" title="Severity: ' + esc(entry.severity || 'INFO') + '">' + esc(formatAction(entry.action)) + '</span></td>'
                + '<td class="target-cell">' + esc(entry.target || '') + '</td>'
                + '<td class="detail-cell" title="' + escAttr(entry.details || '') + '">' + esc(entry.details || '') + '</td>'
                + '<td class="ip-cell">' + esc(entry.sourceIp || '-') + '</td>'
                + '<td>' + esc(entry.authMethod || entry.triggerType || '-') + '</td>';
            tbody.appendChild(row);
        }
    }

    function computeRiskPanel(summary) {
        var failedLogins = summary.riskFailedCount || 0;
        var credentialEvents = summary.riskCredentialCount || 0;
        var ipCount = summary.riskIpCount || 0;

        document.getElementById('riskFailedCount').textContent = failedLogins;
        document.getElementById('riskCredentialCount').textContent = credentialEvents;
        document.getElementById('riskIpCount').textContent = ipCount;

        var failedCard = document.getElementById('riskFailedLogins');
        failedCard.className = 'risk-card ' + (failedLogins >= 5 ? 'risk-red' : failedLogins >= 2 ? 'risk-orange' : 'risk-green');
        var credentialCard = document.getElementById('riskCredentials');
        credentialCard.className = 'risk-card ' + (credentialEvents >= 10 ? 'risk-red' : credentialEvents >= 3 ? 'risk-orange' : 'risk-green');
    }

    function renderStats(summary) {
        var element;
        element = document.getElementById('stat-total'); if (element) { element.textContent = summary.todayTotal || 0; }
        element = document.getElementById('stat-logins'); if (element) { element.textContent = summary.todayLogins || 0; }
        element = document.getElementById('stat-failed'); if (element) { element.textContent = summary.todayFailed || 0; }
        element = document.getElementById('stat-builds'); if (element) { element.textContent = summary.todayBuilds || 0; }
        element = document.getElementById('stat-jobs'); if (element) { element.textContent = summary.todayJobs || 0; }
        element = document.getElementById('stat-config'); if (element) { element.textContent = summary.todayConfig || 0; }
    }

    function scaleStatsGrid() {
        var statsDiv = document.getElementById('stats');
        if (!statsDiv) {
            return;
        }
        var boxes = statsDiv.querySelectorAll('.stat-box');
        var count = boxes.length;
        if (count > 0) {
            statsDiv.style.gridTemplateColumns = 'repeat(' + count + ', 1fr)';
        }
    }

    function computeAnomalies() {
        anomalyActions = [];
    }

    function parsePatterns(str) {
        if (!str) {
            return [];
        }
        return str.split(/[\n,]+/).map(function(segment) {
            return segment.trim();
        }).filter(function(segment) {
            return segment.length > 0;
        });
    }

    function matchesAnyPattern(name, patterns) {
        for (var i = 0; i < patterns.length; i++) {
            if (globMatch(name, patterns[i])) {
                return true;
            }
        }
        return false;
    }

    function globMatch(str, pattern) {
        var escaped = '';
        for (var index = 0; index < pattern.length; index++) {
            var ch = pattern.charAt(index);
            if (ch === '*') {
                escaped += '.*';
            } else if (ch === '?') {
                escaped += '.';
            } else if ('.+^(){}|[]\\'.indexOf(ch) >= 0) {
                escaped += '\\' + ch;
            } else {
                escaped += ch;
            }
        }
        try {
            return new RegExp('^' + escaped + '$', 'i').test(str);
        } catch (ignored) {
            return false;
        }
    }

    function dismissAnomaly() {
        anomalyDismissed = true;
        sessionStorage.setItem(anomalyDismissedKey, 'true');
        var box = document.getElementById('anomalyBox');
        var status = document.getElementById('anomalyStatus');
        if (box) {
            box.className = 'anomaly-box';
        }
        if (status) {
            status.textContent = 'No anomaly detected';
        }
        anomalyActions = [];
    }

    function investigateAnomalies() {
        if (anomalyActions.length === 0) {
            return;
        }
        var today = displayToday || formatDateForInput(getPresetBaseDate());
        document.getElementById('dateFrom').value = today;
        document.getElementById('dateTo').value = today;
        document.getElementById('datePreset').value = 'today';
        document.getElementById('searchText').value = '';
        document.getElementById('filterAction').value = '';
        document.getElementById('searchColumn').value = 'all';
        loadLogs(true);
    }

    function severityBadgeClass(sev, action) {
        if (action === 'USER_CONFIG_UPDATED') {
            return 'badge-info';
        }
        if (sev === 'CRITICAL') {
            return 'badge-critical';
        }
        if (sev === 'HIGH') {
            return 'badge-high';
        }
        if (sev === 'MEDIUM') {
            return 'badge-medium';
        }
        if (sev === 'LOW') {
            return 'badge-low';
        }
        return 'badge-info';
    }

    function formatAction(action) {
        if (!action) {
            return '';
        }
        return action.replace(/_/g, ' ');
    }

    function clearAll() {
        document.getElementById('searchText').value = '';
        document.getElementById('searchColumn').value = 'all';
        document.getElementById('filterAction').value = '';
        document.getElementById('datePreset').value = '';
        document.getElementById('dateFrom').value = '';
        document.getElementById('dateTo').value = '';
        document.getElementById('pageSize').value = '100';
        pageSize = 100;
        currentPage = 1;
        sortField = 'timestampMs';
        sortAsc = false;
        loadLogs(false);
    }

    function applyDatePreset() {
        var preset = document.getElementById('datePreset').value;
        if (!preset) {
            document.getElementById('dateFrom').value = '';
            document.getElementById('dateTo').value = '';
            loadLogs(true);
            return;
        }
        var base = getPresetBaseDate();
        var to = formatDateForInput(base);
        var fromDate = new Date(base.getTime());
        if (preset === 'today') {
            fromDate = new Date(base.getTime());
        } else if (preset === '7d') {
            fromDate.setUTCDate(fromDate.getUTCDate() - 7);
        } else if (preset === '30d') {
            fromDate.setUTCDate(fromDate.getUTCDate() - 30);
        } else {
            var months = parseInt(preset, 10);
            fromDate.setUTCMonth(fromDate.getUTCMonth() - months);
        }
        document.getElementById('dateFrom').value = formatDateForInput(fromDate);
        document.getElementById('dateTo').value = to;
        loadLogs(true);
    }

    function onDateManualChange() {
        document.getElementById('datePreset').value = '';
        loadLogs(true);
    }

    function getPresetBaseDate() {
        if (displayToday) {
            var parsed = new Date(displayToday + 'T00:00:00Z');
            if (!isNaN(parsed.getTime())) {
                return parsed;
            }
        }
        var now = new Date();
        return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
    }

    function formatDateForInput(date) {
        var year = date.getUTCFullYear();
        var month = String(date.getUTCMonth() + 1).padStart(2, '0');
        var day = String(date.getUTCDate()).padStart(2, '0');
        return year + '-' + month + '-' + day;
    }

    function toggleExportMenu() {
        var menu = document.getElementById('exportMenu');
        if (menu) {
            menu.classList.toggle('show');
        }
    }

    document.addEventListener('click', function(event) {
        var wrapper = document.querySelector('.export-wrapper');
        if (wrapper && !wrapper.contains(event.target)) {
            var menu = document.getElementById('exportMenu');
            if (menu) {
                menu.classList.remove('show');
            }
        }
    });

    function exportData(fmt) {
        var menu = document.getElementById('exportMenu');
        if (menu) {
            menu.classList.remove('show');
        }
        var params = buildRequestParams(false).toString();
        if (fmt === 'json') {
            window.location.href = 'exportJson?' + params;
        } else {
            window.location.href = 'exportCsv?' + params;
        }
    }

    function dismissOnboarding() {
        var banner = document.getElementById('onboardingBanner');
        if (banner) {
            banner.style.display = 'none';
        }
        try {
            localStorage.setItem('auditflow-onboarded', '1');
        } catch (ignored) {
            // Ignore localStorage availability issues.
        }
    }

    function esc(value) {
        var element = document.createElement('span');
        element.textContent = String(value);
        return element.innerHTML;
    }

    function escAttr(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/"/g, '&quot;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function refreshInsightsIfVisible() {
        var panel = document.getElementById('insightsPanel');
        if (panel && panel.classList.contains('show')) {
            renderInsights(currentInsights);
        }
    }

    function toggleInsights() {
        var panel = document.getElementById('insightsPanel');
        if (!panel) {
            return;
        }
        var showing = panel.classList.toggle('show');
        if (showing) {
            renderInsights(currentInsights);
        }
    }

    function closeInsights() {
        var panel = document.getElementById('insightsPanel');
        if (panel) {
            panel.classList.remove('show');
        }
    }

    function renderInsights(insights) {
        var list = document.getElementById('insightsList');
        if (!list) {
            return;
        }
        if (insights.length === 0) {
            list.innerHTML = '<li class="insights-empty">No notable activity recorded today.</li>';
            return;
        }

        var html = '';
        for (var i = 0; i < insights.length; i++) {
            var insight = insights[i];
            var severityClass = insight.severity === 'critical'
                ? 'badge-critical'
                : insight.severity === 'high'
                    ? 'badge-high'
                    : insight.severity === 'medium'
                        ? 'badge-medium'
                        : 'badge-low';
            html += '<li>'
                + '<span class="insights-icon">' + insight.icon + '</span>'
                + '<span class="insights-text">' + esc(insight.text) + '</span>'
                + '<span class="badge insights-badge ' + severityClass + '">' + insight.count + '</span>'
                + '</li>';
        }
        list.innerHTML = html;
    }

    function bindUiHandlers() {
        var searchText = document.getElementById('searchText');
        if (searchText) {
            searchText.addEventListener('keydown', function(event) {
                if (event.key === 'Enter') {
                    applySearch();
                }
            });
        }

        var applySearchButton = document.getElementById('applySearchButton');
        if (applySearchButton) {
            applySearchButton.addEventListener('click', applySearch);
        }

        var clearAllButton = document.getElementById('clearAllButton');
        if (clearAllButton) {
            clearAllButton.addEventListener('click', clearAll);
        }

        var toggleExportMenuButton = document.getElementById('toggleExportMenuButton');
        if (toggleExportMenuButton) {
            toggleExportMenuButton.addEventListener('click', toggleExportMenu);
        }

        Array.prototype.forEach.call(document.querySelectorAll('[data-export-format]'), function(button) {
            button.addEventListener('click', function() {
                exportData(button.getAttribute('data-export-format'));
            });
        });

        var refreshLogsButton = document.getElementById('refreshLogsButton');
        if (refreshLogsButton) {
            refreshLogsButton.addEventListener('click', function() {
                loadLogs(false);
            });
        }

        var toggleInsightsButton = document.getElementById('toggleInsightsButton');
        if (toggleInsightsButton) {
            toggleInsightsButton.addEventListener('click', toggleInsights);
        }

        var closeInsightsButton = document.getElementById('closeInsightsButton');
        if (closeInsightsButton) {
            closeInsightsButton.addEventListener('click', closeInsights);
        }

        var dismissOnboardingButton = document.getElementById('dismissOnboardingButton');
        if (dismissOnboardingButton) {
            dismissOnboardingButton.addEventListener('click', dismissOnboarding);
        }

        var investigateButton = document.getElementById('btnInvestigate');
        if (investigateButton) {
            investigateButton.addEventListener('click', investigateAnomalies);
        }

        var dismissButton = document.getElementById('btnDismiss');
        if (dismissButton) {
            dismissButton.addEventListener('click', dismissAnomaly);
        }

        Array.prototype.forEach.call(document.querySelectorAll('input[name="viewMode"]'), function(input) {
            input.addEventListener('change', applySearch);
        });

        var filterAction = document.getElementById('filterAction');
        if (filterAction) {
            filterAction.addEventListener('change', applySearch);
        }

        var pageSizeSelect = document.getElementById('pageSize');
        if (pageSizeSelect) {
            pageSizeSelect.addEventListener('change', changePageSize);
        }

        var datePreset = document.getElementById('datePreset');
        if (datePreset) {
            datePreset.addEventListener('change', applyDatePreset);
        }

        var dateFrom = document.getElementById('dateFrom');
        if (dateFrom) {
            dateFrom.addEventListener('change', onDateManualChange);
        }

        var dateTo = document.getElementById('dateTo');
        if (dateTo) {
            dateTo.addEventListener('change', onDateManualChange);
        }

        Array.prototype.forEach.call(document.querySelectorAll('th[data-sort-field]'), function(header) {
            header.addEventListener('click', function() {
                sortBy(header.getAttribute('data-sort-field'));
            });
        });

        var btnFirst = document.getElementById('btnFirst');
        if (btnFirst) {
            btnFirst.addEventListener('click', firstPage);
        }

        var btnPrev = document.getElementById('btnPrev');
        if (btnPrev) {
            btnPrev.addEventListener('click', prevPage);
        }

        var btnNext = document.getElementById('btnNext');
        if (btnNext) {
            btnNext.addEventListener('click', nextPage);
        }

        var btnLast = document.getElementById('btnLast');
        if (btnLast) {
            btnLast.addEventListener('click', lastPage);
        }
    }

    window.addEventListener('load', function() {
        bindUiHandlers();
        try {
            if (localStorage.getItem('auditflow-onboarded') === '1') {
                var banner = document.getElementById('onboardingBanner');
                if (banner) {
                    banner.style.display = 'none';
                }
            }
        } catch (ignored) {
            // Ignore localStorage availability issues.
        }
        loadLogs(false);
    });

    window.auditflowDebug = {
        parsePatterns: parsePatterns,
        matchesAnyPattern: matchesAnyPattern,
        globMatch: globMatch,
        getAnomalyConfig: function() { return anomalyConfig; }
    };
})();