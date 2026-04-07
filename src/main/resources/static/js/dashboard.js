/* ═══════════════════════════════════════════
   SignOff Dashboard — Real-time Controller
   ═══════════════════════════════════════════ */

(function () {
    'use strict';

    // ── State ──
    const state = {
        connected: false,
        deviceState: null,
        lockEnabled: true,
        motionHistory: [], // { time, value } - last 60 seconds
        stompClient: null,
    };

    // ── DOM Elements ──
    const els = {
        connectionBadge: document.getElementById('connection-badge'),
        connectionText: document.getElementById('connection-text'),
        pulseDot: document.getElementById('pulse-dot'),
        toggleLockBtn: document.getElementById('toggle-lock-btn'),
        lockStatusText: document.getElementById('lock-status-text'),
        deviceId: document.getElementById('device-id'),
        connectionMode: document.getElementById('connection-mode'),
        motionBadge: document.getElementById('motion-badge'),
        metricLabel: document.getElementById('metric-label'),
        accelerationValue: document.getElementById('acceleration-value'),
        heartbeatCount: document.getElementById('heartbeat-count'),
        heartbeatRing: document.getElementById('heartbeat-ring'),
        heartbeatTimer: document.getElementById('heartbeat-timer'),
        lastHeartbeatTime: document.getElementById('last-heartbeat-time'),
        motionCanvas: document.getElementById('motion-canvas'),
        historyList: document.getElementById('history-list'),
        serverUrl: document.getElementById('server-url'),
        toastContainer: document.getElementById('toast-container'),
        // Settings
        timeoutSlider: document.getElementById('timeout-slider'),
        timeoutValue: document.getElementById('timeout-value'),
        graceSlider: document.getElementById('grace-slider'),
        graceValue: document.getElementById('grace-value'),
        thresholdSlider: document.getElementById('threshold-slider'),
        thresholdValue: document.getElementById('threshold-value'),
        cooldownSlider: document.getElementById('cooldown-slider'),
        cooldownValue: document.getElementById('cooldown-value'),
        bleThresholdSlider: document.getElementById('ble-threshold-slider'),
        bleThresholdValue: document.getElementById('ble-threshold-value'),
        saveSettingsBtn: document.getElementById('save-settings-btn'),
        copyUrlBtn: document.getElementById('copy-url-btn'),
    };

    // ── Initialize ──
    function init() {
        displayServerUrl();
        connectWebSocket();
        setupEventListeners();
        setupCanvas();
        startTimerLoop();
        fetchInitialStatus();
    }

    // ── Server URL ──
    function displayServerUrl() {
        const host = window.location.hostname || 'localhost';
        const port = window.location.port || '8080';
        els.serverUrl.textContent = `ws://${host}:${port}/ws`;
    }

    // ── WebSocket Connection ──
    function connectWebSocket() {
        const socket = new SockJS('/ws');
        state.stompClient = Stomp.over(socket);

        // Disable debug logging
        state.stompClient.debug = null;

        state.stompClient.connect({}, function () {
            console.log('✅ Dashboard connected to WebSocket');
            showToast('Connected to SignOff server', 'success');

            // Subscribe to dashboard updates
            state.stompClient.subscribe('/topic/dashboard', function (message) {
                const update = JSON.parse(message.body);
                handleDashboardUpdate(update);
            });
        }, function (error) {
            console.error('❌ WebSocket error:', error);
            showToast('Connection lost — retrying...', 'lock');
            setTimeout(connectWebSocket, 3000);
        });
    }

    // ── Handle Dashboard Updates ──
    function handleDashboardUpdate(update) {
        if (update.deviceState) {
            state.deviceState = update.deviceState;
            updatePhoneCard(update.deviceState);
            updateHeartbeatCard(update.deviceState);
            updateConnectionBadge(update.deviceState.connected);

            // Add to motion graph
            if (update.type === 'heartbeat') {
                const mode = update.deviceState.mode || 'WIFI';
                state.motionHistory.push({
                    time: Date.now(),
                    value: mode === 'BLUETOOTH' ? update.deviceState.avgRssi : update.deviceState.lastAcceleration
                });
                // Keep last 60 seconds
                const cutoff = Date.now() - 60000;
                state.motionHistory = state.motionHistory.filter(d => d.time > cutoff);
                triggerHeartbeatPulse();
            }
        }

        if (update.type === 'lock_event' && update.lockEvent) {
            addLockEvent(update.lockEvent);
            showToast('🔒 Laptop LOCKED — ' + update.lockEvent.reason, 'lock');
        }

        if (update.type === 'status_change') {
            if (update.deviceState && update.deviceState.connected) {
                showToast('📱 Phone connected: ' + update.deviceState.deviceId, 'success');
            } else if (update.deviceState) {
                showToast('⚠️ Phone disconnected', 'info');
            }
        }
    }

    // ── Fetch initial status ──
    function fetchInitialStatus() {
        fetch('/api/status')
            .then(r => r.json())
            .then(data => {
                state.lockEnabled = data.lockEnabled;
                updateLockButton();

                if (data.config) {
                    els.timeoutSlider.value = data.config.heartbeatTimeoutMs;
                    els.timeoutValue.textContent = data.config.heartbeatTimeoutMs + 'ms';
                    els.graceSlider.value = data.config.gracePeriodMs;
                    els.graceValue.textContent = data.config.gracePeriodMs + 'ms';
                    els.thresholdSlider.value = data.config.motionThreshold;
                    els.thresholdValue.textContent = data.config.motionThreshold + ' m/s²';
                    els.cooldownSlider.value = data.config.lockCooldownMs;
                    els.cooldownValue.textContent = data.config.lockCooldownMs + 'ms';
                    if (data.config.bleThresholdDbm) {
                        els.bleThresholdSlider.value = data.config.bleThresholdDbm;
                        els.bleThresholdValue.textContent = data.config.bleThresholdDbm + ' dBm';
                    }
                }

                if (data.deviceState) {
                    state.deviceState = data.deviceState;
                    updatePhoneCard(data.deviceState);
                    updateHeartbeatCard(data.deviceState);
                    updateConnectionBadge(data.deviceState.connected);
                }

                if (data.recentLockEvents) {
                    data.recentLockEvents.forEach(e => addLockEvent(e, false));
                }
            })
            .catch(err => console.warn('Failed to fetch status:', err));
    }

    // ── Update UI ──
    function updatePhoneCard(device) {
        els.deviceId.textContent = device.deviceId || '—';
        const mode = device.mode || 'WIFI';
        els.connectionMode.textContent = mode;
        
        if (mode === 'BLUETOOTH') {
            els.metricLabel.textContent = 'Signal (RSSI)';
            els.accelerationValue.textContent = (device.avgRssi || 0) + ' dBm';
            
            const badge = els.motionBadge;
            const threshold = parseFloat(els.bleThresholdSlider.value) || -80;
            const isGood = (device.avgRssi || -100) >= threshold;
            badge.textContent = isGood ? '📡 Good Signal' : '⚠️ Weak Signal';
            badge.className = 'state-badge ' + (isGood ? 'moving' : 'still');
            
        } else {
            els.metricLabel.textContent = 'Acceleration';
            els.accelerationValue.textContent = (device.lastAcceleration || 0).toFixed(2) + ' m/s²';
            
            const badge = els.motionBadge;
            badge.textContent = device.isMoving ? '🚶 Moving' : '🧘 Still';
            badge.className = 'state-badge ' + (device.isMoving ? 'moving' : 'still');
        }
        
        els.heartbeatCount.textContent = device.heartbeatCount || 0;
    }

    function updateHeartbeatCard(device) {
        // Timer is updated in the startTimerLoop
    }

    function updateConnectionBadge(connected) {
        state.connected = connected;
        const badge = els.connectionBadge;
        if (connected) {
            badge.className = 'connection-badge connected';
            els.connectionText.textContent = 'Connected';
        } else if (state.deviceState) {
            badge.className = 'connection-badge disconnected';
            els.connectionText.textContent = 'Disconnected';
        } else {
            badge.className = 'connection-badge';
            els.connectionText.textContent = 'Waiting...';
        }
    }

    function updateLockButton() {
        const btn = els.toggleLockBtn;
        if (state.lockEnabled) {
            btn.className = 'btn btn-lock';
            els.lockStatusText.textContent = 'Lock ON';
        } else {
            btn.className = 'btn btn-lock disabled';
            els.lockStatusText.textContent = 'Lock OFF';
        }
    }

    function triggerHeartbeatPulse() {
        const ring = els.heartbeatRing;
        ring.classList.remove('beat');
        // Force reflow
        void ring.offsetWidth;
        ring.classList.add('beat');
    }

    // ── Timer Loop (updates heartbeat countdown) ──
    function startTimerLoop() {
        setInterval(() => {
            if (!state.deviceState || !state.deviceState.lastHeartbeat) {
                els.heartbeatTimer.textContent = '—';
                els.heartbeatRing.className = 'heartbeat-ring';
                return;
            }

            const elapsed = Date.now() - state.deviceState.lastHeartbeat;
            const seconds = (elapsed / 1000).toFixed(1);
            els.heartbeatTimer.textContent = seconds + 's';

            // Update last heartbeat time
            const date = new Date(state.deviceState.lastHeartbeat);
            els.lastHeartbeatTime.textContent = date.toLocaleTimeString();

            // Ring state
            const ring = els.heartbeatRing;
            if (elapsed < 3000) {
                ring.className = 'heartbeat-ring active';
            } else if (elapsed < 5000) {
                ring.className = 'heartbeat-ring warning';
            } else {
                ring.className = 'heartbeat-ring danger';
            }

            // Redraw motion graph
            drawMotionGraph();
        }, 200);
    }

    // ── Motion Graph (Canvas) ──
    let canvasCtx = null;

    function setupCanvas() {
        const canvas = els.motionCanvas;
        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        canvas.width = rect.width * dpr;
        canvas.height = 250 * dpr;
        canvasCtx = canvas.getContext('2d');
        canvasCtx.scale(dpr, dpr);
        canvas.style.height = '250px';
    }

    function drawMotionGraph() {
        if (!canvasCtx) return;

        const canvas = els.motionCanvas;
        const w = canvas.clientWidth;
        const h = 250;
        const ctx = canvasCtx;

        ctx.clearRect(0, 0, w, h);

        const data = state.motionHistory;
        if (data.length < 2) {
            // Draw "No data" text
            ctx.fillStyle = 'rgba(148, 163, 184, 0.3)';
            ctx.font = '14px Inter, sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('Waiting for motion data...', w / 2, h / 2);
            return;
        }

        const now = Date.now();
        const windowMs = 60000; // 60 seconds
        const mode = (state.deviceState && state.deviceState.mode) || 'WIFI';
        
        let maxVal, minVal, threshold, unit;
        let isDanger = (val) => val < threshold;
        
        if (mode === 'BLUETOOTH') {
            minVal = -100;
            maxVal = -40; // closer means higher max RSSI
            threshold = parseFloat(els.bleThresholdSlider.value) || -80;
            unit = 'dBm';
        } else {
            minVal = 0;
            maxVal = Math.max(8, ...data.map(d => d.value)); // Dynamic max
            threshold = parseFloat(els.thresholdSlider.value) || 2.0;
            unit = 'm/s²';
            isDanger = (val) => val < threshold; // Warning: inverted logic, for acceleration we want them to be moving, so < threshold is dangerous?
            // Wait, for WIFI, if they drop below threshold and heartbeat lost, they lock. 
            // So < threshold is still/away.
        }

        const padding = { top: 20, bottom: 30, left: 10, right: 10 };
        const plotW = w - padding.left - padding.right;
        const plotH = h - padding.top - padding.bottom;

        // Function to map data value to Y coordinate
        const getY = (val) => {
            let clamped = Math.max(minVal, Math.min(maxVal, val));
            return padding.top + plotH - ((clamped - minVal) / (maxVal - minVal)) * plotH;
        };

        // Threshold line
        const thresholdY = getY(threshold);
        ctx.strokeStyle = 'rgba(245, 158, 11, 0.3)';
        ctx.setLineDash([4, 4]);
        ctx.beginPath();
        ctx.moveTo(padding.left, thresholdY);
        ctx.lineTo(w - padding.right, thresholdY);
        ctx.stroke();
        ctx.setLineDash([]);

        // Threshold label
        ctx.fillStyle = 'rgba(245, 158, 11, 0.6)';
        ctx.font = '10px JetBrains Mono, monospace';
        ctx.textAlign = 'right';
        ctx.fillText(mode === 'BLUETOOTH' ? `${threshold} ${unit}` : `${threshold.toFixed(1)} ${unit}`, w - padding.right - 4, thresholdY - 4);

        // Fill area under curve
        const gradient = ctx.createLinearGradient(0, padding.top, 0, h - padding.bottom);
        gradient.addColorStop(0, 'rgba(6, 182, 212, 0.25)');
        gradient.addColorStop(1, 'rgba(6, 182, 212, 0.0)');

        ctx.beginPath();
        data.forEach((point, i) => {
            const actualX = padding.left + plotW - ((now - point.time) / windowMs) * plotW;
            const y = getY(point.value);

            if (i === 0) ctx.moveTo(actualX, y);
            else ctx.lineTo(actualX, y);
        });

        // Close the area
        const lastX = padding.left + plotW - ((now - data[data.length - 1].time) / windowMs) * plotW;
        const firstX = padding.left + plotW - ((now - data[0].time) / windowMs) * plotW;
        ctx.lineTo(lastX, h - padding.bottom);
        ctx.lineTo(firstX, h - padding.bottom);
        ctx.closePath();
        ctx.fillStyle = gradient;
        ctx.fill();

        // Draw line
        ctx.beginPath();
        data.forEach((point, i) => {
            const actualX = padding.left + plotW - ((now - point.time) / windowMs) * plotW;
            const y = getY(point.value);

            if (i === 0) ctx.moveTo(actualX, y);
            else ctx.lineTo(actualX, y);
        });
        ctx.strokeStyle = '#06b6d4';
        ctx.lineWidth = 2;
        ctx.lineJoin = 'round';
        ctx.stroke();

        // Draw latest point
        if (data.length > 0) {
            const latest = data[data.length - 1];
            const lx = padding.left + plotW - ((now - latest.time) / windowMs) * plotW;
            const ly = getY(latest.value);
            
            const pointIsDanger = mode === 'BLUETOOTH' ? latest.value < threshold : latest.value > threshold;

            ctx.beginPath();
            ctx.arc(lx, ly, 4, 0, Math.PI * 2);
            ctx.fillStyle = pointIsDanger ? '#f59e0b' : '#06b6d4';
            ctx.fill();

            // Glow
            ctx.beginPath();
            ctx.arc(lx, ly, 8, 0, Math.PI * 2);
            ctx.fillStyle = pointIsDanger ? 'rgba(245, 158, 11, 0.2)' : 'rgba(6, 182, 212, 0.2)';
            ctx.fill();

            // Value label
            ctx.fillStyle = '#f1f5f9';
            ctx.font = '11px JetBrains Mono, monospace';
            ctx.textAlign = 'center';
            ctx.fillText(mode === 'BLUETOOTH' ? latest.value : latest.value.toFixed(1), lx, ly - 12);
        }

        // X-axis labels
        ctx.fillStyle = 'rgba(148, 163, 184, 0.4)';
        ctx.font = '10px Inter, sans-serif';
        ctx.textAlign = 'center';
        for (let s = 0; s <= 60; s += 15) {
            const x = padding.left + plotW - (s / 60) * plotW;
            ctx.fillText(s === 0 ? 'Now' : `-${s}s`, x, h - 8);
        }
    }

    // ── Lock History ──
    function addLockEvent(event, animate = true) {
        // Remove empty state
        const empty = els.historyList.querySelector('.history-empty');
        if (empty) empty.remove();

        const item = document.createElement('div');
        item.className = 'history-item';
        if (!animate) item.style.animation = 'none';

        const time = new Date(event.timestamp).toLocaleTimeString();
        item.innerHTML = `
            <div class="history-dot"></div>
            <div class="history-details">
                <div class="history-time">${time}</div>
                <div class="history-reason">${escapeHtml(event.reason)}</div>
            </div>
        `;

        els.historyList.prepend(item);

        // Keep max 20 items
        while (els.historyList.children.length > 20) {
            els.historyList.removeChild(els.historyList.lastChild);
        }
    }

    // ── Event Listeners ──
    function setupEventListeners() {
        // Toggle Lock
        els.toggleLockBtn.addEventListener('click', () => {
            fetch('/api/lock/toggle', { method: 'POST' })
                .then(r => r.json())
                .then(data => {
                    state.lockEnabled = data.lockEnabled;
                    updateLockButton();
                    showToast(
                        state.lockEnabled ? '🔒 Auto-lock ENABLED' : '🔓 Auto-lock DISABLED',
                        state.lockEnabled ? 'success' : 'info'
                    );
                })
                .catch(err => showToast('Failed to toggle lock', 'lock'));
        });

        // Sliders
        els.timeoutSlider.addEventListener('input', () => {
            els.timeoutValue.textContent = els.timeoutSlider.value + 'ms';
        });
        els.graceSlider.addEventListener('input', () => {
            els.graceValue.textContent = els.graceSlider.value + 'ms';
        });
        els.thresholdSlider.addEventListener('input', () => {
            els.thresholdValue.textContent = parseFloat(els.thresholdSlider.value).toFixed(1) + ' m/s²';
        });
        els.cooldownSlider.addEventListener('input', () => {
            els.cooldownValue.textContent = els.cooldownSlider.value + 'ms';
        });

        els.bleThresholdSlider.addEventListener('input', () => {
            els.bleThresholdValue.textContent = els.bleThresholdSlider.value + ' dBm';
        });

        // Save Settings
        els.saveSettingsBtn.addEventListener('click', () => {
            const config = {
                heartbeatTimeoutMs: parseInt(els.timeoutSlider.value),
                gracePeriodMs: parseInt(els.graceSlider.value),
                motionThreshold: parseFloat(els.thresholdSlider.value),
                lockCooldownMs: parseInt(els.cooldownSlider.value),
                bleThresholdDbm: parseInt(els.bleThresholdSlider.value)
            };

            fetch('/api/config', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            })
                .then(r => r.json())
                .then(() => showToast('⚙️ Settings saved', 'success'))
                .catch(err => showToast('Failed to save settings', 'lock'));
        });

        // Copy URL
        els.copyUrlBtn.addEventListener('click', () => {
            navigator.clipboard.writeText(els.serverUrl.textContent)
                .then(() => showToast('📋 URL copied to clipboard', 'info'))
                .catch(() => showToast('Failed to copy', 'lock'));
        });

        // Resize canvas
        window.addEventListener('resize', () => {
            setupCanvas();
        });
    }

    // ── Toast Notifications ──
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;
        els.toastContainer.appendChild(toast);

        setTimeout(() => {
            toast.style.animation = 'toastOut 0.3s ease forwards';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    // ── Utilities ──
    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // ── Start ──
    document.addEventListener('DOMContentLoaded', init);
})();
