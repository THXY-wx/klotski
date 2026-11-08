(function () {
    'use strict';

    const $ = function (id) { return document.getElementById(id); };
    const canvas = $('boardCanvas');
    const ctx = canvas.getContext('2d');
    const boardWrap = $('boardWrap');

    const els = {
        sizeInput: $('sizeInput'),
        sizeMinus: $('sizeMinus'),
        sizePlus: $('sizePlus'),
        maxSizeLabel: $('maxSizeLabel'),
        newGame: $('newGame'),
        statusText: $('statusText'),
        sizeChip: $('sizeChip'),
        timer: $('timer'),
        moveCount: $('moveCount'),
        sizeStat: $('sizeStat'),
        undoBtn: $('undoBtn'),
        redoBtn: $('redoBtn'),
        shuffleBtn: $('shuffleBtn'),
        restartBtn: $('restartBtn'),
        zoomOut: $('zoomOut'),
        zoomIn: $('zoomIn'),
        zoomFit: $('zoomFit'),
        zoomLabel: $('zoomLabel'),
        winOverlay: $('winOverlay'),
        winTime: $('winTime'),
        winMoves: $('winMoves'),
        playAgain: $('playAgain'),
        winShuffle: $('winShuffle'),
        fullscreenBtn: $('fullscreenBtn'),
        borderlessBtn: $('borderlessBtn'),
        exitBtn: $('exitBtn'),
        toast: $('toast')
    };

    const PALETTE = [
        '#1e3a8a', '#0f766e', '#7c3aed', '#9f1239',
        '#b45309', '#1d4ed8', '#0e7490', '#4c1d95'
    ];
    const MIN_SIZE = 2;
    const MAX_FIT_CELL = 160;
    const MAX_ZOOM = 40;

    let bridge = null;
    let windowApi = null;
    let maxSize = 215;
    let state = null;
    let windowState = { fullscreen: false, borderless: false };
    let zoom = 1;
    let fitMode = true;
    let hoverIndex = -1;
    let drag = null;
    let redrawQueued = false;
    let toastTimer = null;
    let winVisible = false;

    const layout = {
        width: 0,
        height: 0,
        dpr: 1,
        baseCell: 1,
        panX: 0,
        panY: 0
    };

    const timer = {
        started: false,
        running: false,
        startedAt: 0,
        elapsed: 0,
        handle: null
    };

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function formatTime(ms) {
        const total = Math.max(0, Math.floor(ms / 1000));
        const seconds = total % 60;
        const minutes = Math.floor(total / 60) % 60;
        const hours = Math.floor(total / 3600);
        return hours > 0
            ? hours + ':' + pad(minutes) + ':' + pad(seconds)
            : pad(minutes) + ':' + pad(seconds);
    }

    function clamp(value, min, max) {
        return Math.max(min, Math.min(max, value));
    }

    function cellSize() {
        return layout.baseCell * zoom;
    }

    function computeBaseCell() {
        if (!state) return 1;
        const available = Math.min(layout.width - 28, layout.height - 28);
        const fit = available / state.size;
        return Math.max(1, Math.min(MAX_FIT_CELL, fit));
    }

    function maxZoom() {
        return Math.max(1, Math.min(MAX_ZOOM, MAX_FIT_CELL / Math.max(1, layout.baseCell)));
    }

    function centerBoard() {
        if (!state) return;
        const size = state.size;
        const cell = cellSize();
        const boardSize = cell * size;
        layout.panX = (layout.width - boardSize) / 2;
        layout.panY = (layout.height - boardSize) / 2;
    }

    function clampPan() {
        if (!state) return;
        const boardSize = cellSize() * state.size;
        if (boardSize <= layout.width) {
            layout.panX = (layout.width - boardSize) / 2;
        } else {
            layout.panX = clamp(layout.panX, layout.width - boardSize, 0);
        }
        if (boardSize <= layout.height) {
            layout.panY = (layout.height - boardSize) / 2;
        } else {
            layout.panY = clamp(layout.panY, layout.height - boardSize, 0);
        }
    }

    function canPan() {
        if (!state) return false;
        const boardSize = cellSize() * state.size;
        return boardSize > layout.width + 1 || boardSize > layout.height + 1;
    }

    function updateZoomLabel() {
        els.zoomLabel.textContent = Math.round(zoom * 100) + '%';
        els.zoomIn.disabled = !state || zoom >= maxZoom() - 0.001;
        els.zoomOut.disabled = !state || zoom <= 1.001;
    }

    function setZoom(nextZoom, anchorX, anchorY) {
        if (!state) return;
        const oldCell = cellSize();
        const target = clamp(nextZoom, 1, maxZoom());
        if (Math.abs(target - zoom) < 0.001) return;

        const ax = anchorX === undefined ? layout.width / 2 : anchorX;
        const ay = anchorY === undefined ? layout.height / 2 : anchorY;
        const boardX = (ax - layout.panX) / oldCell;
        const boardY = (ay - layout.panY) / oldCell;

        zoom = target;
        fitMode = zoom <= 1.001;
        const newCell = cellSize();
        layout.panX = ax - boardX * newCell;
        layout.panY = ay - boardY * newCell;
        clampPan();
        updateZoomLabel();
        updateStatus();
        updateCursor();
        requestDraw();
    }

    function fitView() {
        if (!state) return;
        zoom = 1;
        fitMode = true;
        layout.baseCell = computeBaseCell();
        centerBoard();
        updateZoomLabel();
        updateStatus();
        updateCursor();
        requestDraw();
    }

    function requestDraw() {
        if (redrawQueued) return;
        redrawQueued = true;
        requestAnimationFrame(function () {
            redrawQueued = false;
            draw();
        });
    }

    function roundedRectPath(x, y, width, height, radius) {
        const r = Math.min(radius, width / 2, height / 2);
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.lineTo(x + width - r, y);
        ctx.quadraticCurveTo(x + width, y, x + width, y + r);
        ctx.lineTo(x + width, y + height - r);
        ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
        ctx.lineTo(x + r, y + height);
        ctx.quadraticCurveTo(x, y + height, x, y + height - r);
        ctx.lineTo(x, y + r);
        ctx.quadraticCurveTo(x, y, x + r, y);
        ctx.closePath();
    }

    function draw() {
        if (!ctx) return;
        ctx.clearRect(0, 0, layout.width, layout.height);
        if (!state) return;

        const size = state.size;
        const cell = cellSize();
        const boardSize = cell * size;
        const gap = cell >= 10 ? Math.min(4, Math.max(1, cell * 0.06)) : 0;
        const useRound = cell >= 26;
        const cells = state.cells;
        const finished = state.finished;
        let showText = cell >= 16;
        let fontSize = 0;
        const maxLabel = String(size * size - 1);
        if (showText) {
            fontSize = Math.min(72, cell * 0.42);
            ctx.font = 'bold ' + fontSize + 'px "Segoe UI", "Microsoft YaHei", sans-serif';
            while (fontSize > 6 && ctx.measureText(maxLabel).width > cell * 0.8) {
                fontSize -= 1;
                ctx.font = 'bold ' + fontSize + 'px "Segoe UI", "Microsoft YaHei", sans-serif';
            }
            if (ctx.measureText(maxLabel).width > cell * 0.86) showText = false;
        }
        const textColor = finished
            ? 'rgba(45, 31, 3, 0.92)'
            : 'rgba(255, 255, 255, 0.94)';

        ctx.save();
        ctx.beginPath();
        ctx.rect(0, 0, layout.width, layout.height);
        ctx.clip();

        if (boardSize > 0) {
            const inset = Math.max(4, Math.min(12, cell * 0.4));
            ctx.fillStyle = 'rgba(1, 8, 16, 0.58)';
            roundedRectPath(
                layout.panX - inset,
                layout.panY - inset,
                boardSize + inset * 2,
                boardSize + inset * 2,
                Math.max(6, Math.min(22, cell * 0.16))
            );
            ctx.fill();
            ctx.strokeStyle = 'rgba(94, 234, 212, 0.1)';
            ctx.lineWidth = 1;
            ctx.stroke();
        }

        const startCol = Math.max(0, Math.floor(-layout.panX / cell));
        const endCol = Math.min(size - 1, Math.ceil((layout.width - layout.panX) / cell));
        const startRow = Math.max(0, Math.floor(-layout.panY / cell));
        const endRow = Math.min(size - 1, Math.ceil((layout.height - layout.panY) / cell));

        if (showText) {
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
        }

        for (let row = startRow; row <= endRow; row++) {
            for (let col = startCol; col <= endCol; col++) {
                const index = row * size + col;
                const value = cells[index];
                const x = layout.panX + col * cell + gap;
                const y = layout.panY + row * cell + gap;
                const tileSize = Math.max(1, cell - gap * 2);

                if (value === 0) {
                    if (cell >= 8) {
                        const blankInset = Math.max(1, cell * 0.12);
                        ctx.fillStyle = 'rgba(255, 255, 255, 0.025)';
                        if (useRound) {
                            roundedRectPath(
                                x + blankInset,
                                y + blankInset,
                                Math.max(1, tileSize - blankInset * 2),
                                Math.max(1, tileSize - blankInset * 2),
                                Math.max(2, cell * 0.12)
                            );
                            ctx.fill();
                            ctx.strokeStyle = 'rgba(148, 180, 220, 0.1)';
                            ctx.stroke();
                        } else {
                            ctx.fillRect(x + blankInset, y + blankInset,
                                Math.max(1, tileSize - blankInset * 2),
                                Math.max(1, tileSize - blankInset * 2));
                        }
                    }
                    continue;
                }

                ctx.fillStyle = finished
                    ? '#f59e0b'
                    : PALETTE[Math.abs(value) % PALETTE.length];
                if (useRound) {
                    roundedRectPath(x, y, tileSize, tileSize, Math.max(2, cell * 0.16));
                    ctx.fill();
                } else {
                    ctx.fillRect(x, y, tileSize, tileSize);
                }

                if (showText) {
                    ctx.fillStyle = textColor;
                    ctx.fillText(String(value), x + tileSize / 2, y + tileSize / 2 + cell * 0.01);
                }
            }
        }

        if (hoverIndex >= 0 && hoverIndex < cells.length && cell >= 14
            && isAdjacent(hoverIndex) && !finished) {
            const row = Math.floor(hoverIndex / size);
            const col = hoverIndex % size;
            const x = layout.panX + col * cell + gap;
            const y = layout.panY + row * cell + gap;
            ctx.strokeStyle = 'rgba(94, 234, 212, 0.9)';
            ctx.lineWidth = Math.max(1.5, cell * 0.035);
            roundedRectPath(x, y, Math.max(1, cell - gap * 2), Math.max(1, cell - gap * 2),
                Math.max(2, cell * 0.16));
            ctx.stroke();
        }

        ctx.restore();
    }

    function resizeCanvas() {
        const rect = boardWrap.getBoundingClientRect();
        const width = Math.max(1, Math.floor(rect.width));
        const height = Math.max(1, Math.floor(rect.height));
        const dpr = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
        const pixelWidth = Math.round(width * dpr);
        const pixelHeight = Math.round(height * dpr);

        if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
            canvas.width = pixelWidth;
            canvas.height = pixelHeight;
        }

        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        layout.width = width;
        layout.height = height;
        layout.dpr = dpr;
        layout.baseCell = computeBaseCell();
        if (zoom > maxZoom()) {
            zoom = maxZoom();
        }

        if (fitMode || zoom <= 1.001) {
            zoom = 1;
            fitMode = true;
            layout.baseCell = computeBaseCell();
            centerBoard();
        } else {
            clampPan();
        }

        updateZoomLabel();
        updateStatus();
        updateCursor();
        requestDraw();
    }

    function isAdjacent(index) {
        if (!state || index < 0) return false;
        const size = state.size;
        const col = index % size;
        const row = Math.floor(index / size);
        const blankCol = state.blank % size;
        const blankRow = Math.floor(state.blank / size);
        return Math.abs(col - blankCol) + Math.abs(row - blankRow) === 1;
    }

    function cellAt(clientX, clientY) {
        if (!state) return -1;
        const rect = canvas.getBoundingClientRect();
        const x = clientX - rect.left;
        const y = clientY - rect.top;
        const cell = cellSize();
        const col = Math.floor((x - layout.panX) / cell);
        const row = Math.floor((y - layout.panY) / cell);
        if (col < 0 || col >= state.size || row < 0 || row >= state.size) return -1;
        return row * state.size + col;
    }

    function updateCursor() {
        if (!state) {
            canvas.style.cursor = 'default';
            return;
        }
        if (drag && drag.canPan) {
            canvas.style.cursor = 'grabbing';
        } else if (hoverIndex >= 0 && isAdjacent(hoverIndex) && !state.finished) {
            canvas.style.cursor = 'pointer';
        } else if (canPan()) {
            canvas.style.cursor = 'grab';
        } else {
            canvas.style.cursor = 'default';
        }
    }

    function setHover(index) {
        if (hoverIndex === index) return;
        hoverIndex = index;
        if (cellSize() >= 14) requestDraw();
        updateCursor();
    }

    function showToast(message) {
        els.toast.textContent = message;
        els.toast.classList.remove('hidden');
        if (toastTimer) clearTimeout(toastTimer);
        toastTimer = setTimeout(function () {
            els.toast.classList.add('hidden');
            toastTimer = null;
        }, 2200);
    }

    function invoke(action) {
        if (!bridge) {
            showToast('游戏尚未初始化，请稍候');
            return null;
        }
        try {
            const raw = action(bridge);
            if (typeof raw !== 'string') {
                throw new Error('桥接返回值无效');
            }
            return JSON.parse(raw);
        } catch (error) {
            console.error(error);
            showToast('操作失败：' + (error && error.message ? error.message : error));
            return null;
        }
    }

    function invokeWindow(action) {
        if (!windowApi) {
            showToast('窗口控制器尚未初始化');
            return null;
        }
        try {
            const raw = action(windowApi);
            if (typeof raw !== 'string') {
                throw new Error('窗口状态返回值无效');
            }
            return JSON.parse(raw);
        } catch (error) {
            console.error(error);
            showToast('窗口操作失败：' + (error && error.message ? error.message : error));
            return null;
        }
    }

    function applyWindowState(next) {
        if (!next) return;
        windowState = {
            fullscreen: !!next.fullscreen,
            borderless: !!next.borderless
        };
        els.fullscreenBtn.textContent = windowState.fullscreen ? '退出全屏' : '全屏';
        els.fullscreenBtn.classList.toggle('active', windowState.fullscreen);
        els.borderlessBtn.textContent = windowState.borderless ? '退出无边框' : '无边框';
        els.borderlessBtn.classList.toggle('active', windowState.borderless);
    }

    function toggleFullscreen() {
        const next = invokeWindow(function (api) { return api.toggleFullscreen(); });
        if (next) applyWindowState(next);
    }

    function toggleBorderless() {
        const next = invokeWindow(function (api) { return api.toggleBorderless(); });
        if (next) applyWindowState(next);
    }

    function exitDisplayMode() {
        if (!windowState.fullscreen && !windowState.borderless) return;
        const next = invokeWindow(function (api) { return api.exitDisplayMode(); });
        if (next) applyWindowState(next);
    }

    function exitApp() {
        if (!windowApi) return;
        try {
            windowApi.exit();
        } catch (error) {
            console.error(error);
            showToast('退出失败：' + (error && error.message ? error.message : error));
        }
    }

    function updateStats() {
        if (!state) return;
        els.moveCount.textContent = String(state.moves);
        els.sizeStat.textContent = state.size + ' × ' + state.size;
        els.sizeChip.textContent = state.size + ' × ' + state.size;
        els.timer.textContent = formatTime(timer.elapsed);
    }

    function updateButtons() {
        const ready = !!state;
        const finished = ready && state.finished;
        els.undoBtn.disabled = !ready || finished || !state.undo;
        els.redoBtn.disabled = !ready || finished || !state.redo;
        els.shuffleBtn.disabled = !ready;
        els.restartBtn.disabled = !ready;
        els.newGame.disabled = !ready;
        els.zoomIn.disabled = !ready || zoom >= maxZoom() - 0.001;
        els.zoomOut.disabled = !ready || zoom <= 1.001;
    }

    function updateStatus() {
        if (!state) {
            els.statusText.textContent = '正在准备棋盘…';
            return;
        }
        if (state.finished) {
            els.statusText.textContent = '完成！所有数字都已归位';
        } else if (state.moves === 0) {
            els.statusText.textContent = cellSize() < 14 && state.size > 12
                ? '棋盘较大，放大后可查看数字'
                : '点击空白格旁边的数字开始';
        } else {
            els.statusText.textContent = '继续，把数字归位';
        }
    }

    function applyState(nextState, options) {
        if (!nextState || typeof nextState.size !== 'number' || !Array.isArray(nextState.cells)) return;
        const opts = options || {};
        const sizeChanged = !state || state.size !== nextState.size;
        state = nextState;
        if (sizeChanged) {
            fitMode = true;
            zoom = 1;
            layout.baseCell = computeBaseCell();
            centerBoard();
        }
        if (opts.resetClock) {
            resetTimer();
        }
        if (opts.fit || sizeChanged) {
            fitView();
        }
        updateStats();
        updateButtons();
        updateStatus();
        updateZoomLabel();
        updateCursor();
        if (state.finished) {
            showWin();
        } else {
            hideWin();
        }
        requestDraw();
    }

    function resetTimer() {
        if (timer.handle) {
            clearInterval(timer.handle);
            timer.handle = null;
        }
        timer.started = false;
        timer.running = false;
        timer.startedAt = 0;
        timer.elapsed = 0;
        els.timer.textContent = formatTime(0);
    }

    function startTimer() {
        if (!state || state.finished || timer.running) return;
        if (!timer.started) {
            timer.started = true;
            timer.startedAt = performance.now() - timer.elapsed;
        }
        timer.running = true;
        if (timer.handle) clearInterval(timer.handle);
        timer.handle = setInterval(tickTimer, 250);
        tickTimer();
    }

    function tickTimer() {
        if (!timer.running) return;
        timer.elapsed = performance.now() - timer.startedAt;
        els.timer.textContent = formatTime(timer.elapsed);
    }

    function stopTimer() {
        if (!timer.running) return;
        timer.elapsed = performance.now() - timer.startedAt;
        timer.running = false;
        if (timer.handle) {
            clearInterval(timer.handle);
            timer.handle = null;
        }
        els.timer.textContent = formatTime(timer.elapsed);
    }

    function showWin() {
        if (!state || winVisible) return;
        stopTimer();
        winVisible = true;
        els.winTime.textContent = formatTime(timer.elapsed);
        els.winMoves.textContent = String(state.moves);
        els.winOverlay.classList.remove('hidden');
        updateButtons();
    }

    function hideWin() {
        winVisible = false;
        els.winOverlay.classList.add('hidden');
    }

    function sendMoveCell(index) {
        if (!state || state.finished) return;
        if (state.cells[index] === 0) return;
        if (!isAdjacent(index)) {
            showToast('只能移动空白格旁边的数字');
            return;
        }
        const size = state.size;
        const beforeMoves = state.moves;
        const data = invoke(function (api) {
            return api.move((index % size) + 1, Math.floor(index / size) + 1);
        });
        if (!data) return;
        if (data.moves > beforeMoves && !timer.started) startTimer();
        applyState(data);
    }

    function sendDirection(direction) {
        if (!state || state.finished) return;
        const beforeMoves = state.moves;
        const data = invoke(function (api) {
            return api.moveDirection(direction);
        });
        if (!data) return;
        if (data.moves > beforeMoves && !timer.started) startTimer();
        applyState(data);
    }

    function undo() {
        if (!state || state.finished || !state.undo) return;
        const data = invoke(function (api) { return api.undo(); });
        if (data) applyState(data);
    }

    function redo() {
        if (!state || state.finished || !state.redo) return;
        const data = invoke(function (api) { return api.redo(); });
        if (data) applyState(data);
    }

    function readSize() {
        const value = Number(els.sizeInput.value);
        if (!Number.isInteger(value) || value < MIN_SIZE || value > maxSize) {
            showToast('棋盘大小必须是 ' + MIN_SIZE + ' 到 ' + maxSize + ' 的整数');
            const fallback = state ? state.size : 4;
            els.sizeInput.value = String(fallback);
            return null;
        }
        return value;
    }

    function startNewGame(sizeOverride) {
        const size = sizeOverride === undefined ? readSize() : sizeOverride;
        if (size === null) return;
        if (!Number.isInteger(size) || size < MIN_SIZE || size > maxSize) {
            showToast('棋盘大小必须是 ' + MIN_SIZE + ' 到 ' + maxSize + ' 的整数');
            return;
        }
        const data = invoke(function (api) { return api.start(size); });
        if (!data) return;
        try {
            localStorage.setItem('klotski.size', String(size));
        } catch (ignored) {
            // WebView 的本地存储不可用时忽略即可。
        }
        els.sizeInput.value = String(size);
        applyState(data, { resetClock: true, fit: true });
    }

    function shuffleGame() {
        const data = invoke(function (api) { return api.shuffle(); });
        if (!data) return;
        applyState(data, { resetClock: true, fit: true });
    }

    function restartGame() {
        const data = invoke(function (api) { return api.restart(); });
        if (!data) return;
        applyState(data, { resetClock: true, fit: true });
    }

    function setSizeValue(value) {
        const next = clamp(value, MIN_SIZE, maxSize);
        els.sizeInput.value = String(next);
    }

    function bindEvents() {
        els.newGame.addEventListener('click', function () {
            startNewGame();
        });

        els.sizeMinus.addEventListener('click', function () {
            setSizeValue((Number(els.sizeInput.value) || MIN_SIZE) - 1);
        });

        els.sizePlus.addEventListener('click', function () {
            setSizeValue((Number(els.sizeInput.value) || MIN_SIZE) + 1);
        });

        els.sizeInput.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                startNewGame();
            }
        });

        document.querySelectorAll('[data-size]').forEach(function (button) {
            button.addEventListener('click', function () {
                const size = Number(button.getAttribute('data-size'));
                setSizeValue(size);
                startNewGame(size);
            });
        });

        els.undoBtn.addEventListener('click', undo);
        els.redoBtn.addEventListener('click', redo);
        els.shuffleBtn.addEventListener('click', shuffleGame);
        els.restartBtn.addEventListener('click', restartGame);
        els.playAgain.addEventListener('click', function () { startNewGame(state ? state.size : 4); });
        els.winShuffle.addEventListener('click', shuffleGame);

        els.fullscreenBtn.addEventListener('click', toggleFullscreen);
        els.borderlessBtn.addEventListener('click', toggleBorderless);
        els.exitBtn.addEventListener('click', exitApp);

        els.zoomOut.addEventListener('click', function () { setZoom(zoom / 1.25); });
        els.zoomIn.addEventListener('click', function () { setZoom(zoom * 1.25); });
        els.zoomFit.addEventListener('click', fitView);

        canvas.addEventListener('pointerdown', function (event) {
            if (event.button !== 0 || !state) return;
            drag = {
                pointerId: event.pointerId,
                startX: event.clientX,
                startY: event.clientY,
                panX: layout.panX,
                panY: layout.panY,
                moved: false,
                canPan: canPan()
            };
            canvas.setPointerCapture(event.pointerId);
            updateCursor();
        });

        canvas.addEventListener('pointermove', function (event) {
            if (drag && drag.pointerId === event.pointerId) {
                if (!drag.canPan) return;
                const dx = event.clientX - drag.startX;
                const dy = event.clientY - drag.startY;
                if (Math.abs(dx) > 4 || Math.abs(dy) > 4) drag.moved = true;
                if (drag.moved) {
                    layout.panX = drag.panX + dx;
                    layout.panY = drag.panY + dy;
                    clampPan();
                    requestDraw();
                }
            } else {
                setHover(cellAt(event.clientX, event.clientY));
            }
        });

        canvas.addEventListener('pointerup', function (event) {
            if (!drag || drag.pointerId !== event.pointerId) return;
            const wasClick = !drag.moved;
            drag = null;
            if (canvas.hasPointerCapture && canvas.hasPointerCapture(event.pointerId)) {
                canvas.releasePointerCapture(event.pointerId);
            }
            if (wasClick) {
                const index = cellAt(event.clientX, event.clientY);
                if (index >= 0) sendMoveCell(index);
            }
            updateCursor();
        });

        canvas.addEventListener('pointercancel', function () {
            drag = null;
            updateCursor();
        });

        canvas.addEventListener('pointerleave', function () {
            if (!drag) setHover(-1);
        });

        canvas.addEventListener('wheel', function (event) {
            if (!state || event.deltaY === 0) return;
            event.preventDefault();
            const factor = event.deltaY < 0 ? 1.12 : 0.89;
            const rect = canvas.getBoundingClientRect();
            setZoom(zoom * factor, event.clientX - rect.left, event.clientY - rect.top);
        }, { passive: false });

        canvas.addEventListener('dblclick', fitView);

        window.addEventListener('keydown', function (event) {
            if (event.target && /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName)) return;

            if (event.key === 'F11') {
                event.preventDefault();
                toggleFullscreen();
                return;
            }

            if (event.key === 'Escape') {
                if (windowState.fullscreen || windowState.borderless) {
                    event.preventDefault();
                    exitDisplayMode();
                }
                return;
            }

            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
                event.preventDefault();
                if (event.shiftKey) redo(); else undo();
                return;
            }
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'y') {
                event.preventDefault();
                redo();
                return;
            }

            const directions = {
                ArrowUp: 'up',
                ArrowDown: 'down',
                ArrowLeft: 'left',
                ArrowRight: 'right'
            };
            const direction = directions[event.key];
            if (direction) {
                event.preventDefault();
                sendDirection(direction);
            }
        });

        window.addEventListener('resize', resizeCanvas);
        if (typeof ResizeObserver === 'function') {
            new ResizeObserver(resizeCanvas).observe(boardWrap);
        }

        window.addEventListener('error', function (event) {
            console.error(event.error || event.message);
            showToast('界面出现错误：' + (event.message || '未知问题'));
        });
    }

    function boot() {
        bridge = window.bridge;
        windowApi = window.windowBridge;
        if (!bridge) {
            showToast('游戏初始化失败：缺少 Kotlin 桥接对象');
            return;
        }
        if (windowApi) {
            els.fullscreenBtn.disabled = false;
            els.borderlessBtn.disabled = false;
            applyWindowState(invokeWindow(function (api) { return api.state(); }));
        }

        maxSize = Number(bridge.maxSize()) || 215;
        els.sizeInput.max = String(maxSize);
        els.maxSizeLabel.textContent = MIN_SIZE + '–' + maxSize;
        els.sizeInput.min = String(MIN_SIZE);
        els.sizeMinus.disabled = false;
        els.sizePlus.disabled = false;

        let initialSize = 4;
        try {
            const saved = Number(localStorage.getItem('klotski.size'));
            if (Number.isInteger(saved) && saved >= MIN_SIZE && saved <= maxSize) {
                initialSize = saved;
            }
        } catch (ignored) {
            // 忽略本地存储不可用的情况。
        }

        resizeCanvas();
        startNewGame(initialSize);
    }

    function updateWindowState(next) {
        applyWindowState(next);
    }

    window.klotskiApp = { boot: boot, updateWindowState: updateWindowState };
    bindEvents();
})();
