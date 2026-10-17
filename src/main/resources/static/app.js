const roomIdInput = document.querySelector('#room-id');
const roomStatus = document.querySelector('#room-status');
const messageCount = document.querySelector('#message-count');
const messageInputs = document.querySelector('#message-inputs');
const createRoomButton = document.querySelector('#create-room-button');
const sendButton = document.querySelector('#send-button');
const sendStatus = document.querySelector('#send-status');
const clearResultsButton = document.querySelector('#clear-results-button');
const debugSummary = document.querySelector('#debug-summary');
const debugList = document.querySelector('#debug-list');

const timelineSteps = [
    ['PUBLISHED', 'XADD / PUBLISHED', '질문 이벤트가 Stream에 발행됨'],
    ['PENDING', 'XREADGROUP / CONSUMED', 'worker가 consume하여 PEL에 등록함'],
    ['ASSISTANT_SAVED', 'RPUSH / ASSISTANT_SAVED', 'assistant 답변이 messages 리스트에 저장됨'],
    ['ACKED', 'XACK / ACKED', '처리가 완료되어 PEL에서 제거됨']
];

function renderMessageInputs() {
    const count = Number(messageCount.value);
    const previous = [...messageInputs.querySelectorAll('textarea')].map((input) => input.value);
    messageInputs.replaceChildren();
    for (let index = 0; index < count; index += 1) {
        const card = document.createElement('div');
        card.className = 'message-card';
        card.innerHTML = `<div class="message-card-header"><label for="message-${index}">Question ${index + 1}</label><span>user</span></div><textarea id="message-${index}" class="message-textarea" placeholder="질문 내용을 입력하세요" data-message-index="${index}"></textarea>`;
        messageInputs.append(card);
        card.querySelector('textarea').value = previous[index] || '';
    }
}

async function createRoom() {
    createRoomButton.disabled = true;
    roomStatus.textContent = '채팅방을 생성하는 중...';
    try {
        const response = await fetch('/api/v1/chat-rooms', { method: 'POST' });
        const data = await response.json();
        if (!response.ok || !data.id) throw new Error(`HTTP ${response.status}`);
        roomIdInput.value = data.id;
        roomStatus.textContent = `생성 완료: ${data.id}`;
    } catch (error) { roomStatus.textContent = `채팅방 생성 실패: ${error.message}`; }
    finally { createRoomButton.disabled = false; }
}

function markEmptyInputs() {
    const inputs = [...messageInputs.querySelectorAll('textarea')];
    inputs.forEach((input) => input.closest('.message-card').classList.toggle('invalid', !input.value.trim()));
    return inputs.every((input) => input.value.trim());
}

async function sendMessage(content, index, roomId) {
    const startedAt = performance.now();
    try {
        const response = await fetch(`/api/v1/chat-rooms/${encodeURIComponent(roomId)}/messages`, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({ content }) });
        const data = await response.json().catch(() => ({}));
        return { question:content, index, ok:response.ok, status:response.status, elapsedMs:Math.round(performance.now()-startedAt), data, error:response.ok ? null : data.message || `HTTP ${response.status}` };
    } catch (error) { return { question:content, index, ok:false, status:'NETWORK', elapsedMs:Math.round(performance.now()-startedAt), data:{}, error:error.message }; }
}

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, (character) => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' })[character]);
}

function timelineMarkup(status = 'PUBLISHED', assistantSaved = false) {
    const completedUntil = status === 'ACKED' ? 4 : assistantSaved ? 3 : 0;
    const activeIndex = status === 'ACKED' ? -1 : assistantSaved ? 3 : status === 'PENDING' ? 1 : 0;
    return `<div class="timeline" data-timeline-status="${status}">${timelineSteps.map(([key, label, description], index) => {
        const state = index < completedUntil ? 'done' : index === activeIndex ? 'active' : 'waiting';
        return `<div class="timeline-step ${state}" data-step="${key}"><span class="timeline-dot"></span><div><strong>${label}</strong><small>${description}</small></div></div>`;
    }).join('')}</div>`;
}

function updateTimeline(item, statusResponse) {
    const timeline = item.querySelector('.timeline');
    if (timeline) timeline.outerHTML = timelineMarkup(statusResponse.status, statusResponse.assistantSaved);
}

function renderResults(results) {
    const successCount = results.filter((result) => result.ok).length;
    debugSummary.className = 'debug-summary';
    debugSummary.textContent = `${results.length}개 요청 완료 · 성공 ${successCount}개 · 실패 ${results.length - successCount}개`;
    debugList.replaceChildren();
    results.forEach((result) => {
        const item = document.createElement('article');
        item.className = `debug-item ${result.ok ? 'status-success' : 'status-error'}`;
        item.tabIndex = 0;
        item.setAttribute('role', 'button');
        item.innerHTML = `<div class="debug-item-header"><span>Message ${result.index + 1} · ${result.ok ? 'SUCCESS' : 'FAILED'}</span><span>${result.elapsedMs} ms</span></div><div class="debug-question"><span>question</span><strong>${escapeHtml(result.question)}</strong></div><div class="debug-meta"><div>HTTP: <strong>${escapeHtml(result.status)}</strong></div><div>message_id: <strong>${escapeHtml(result.data.message_id || '-')}</strong></div><div>stream_message_id: <strong>${escapeHtml(result.data.stream_message_id || '-')}</strong></div>${result.error ? `<div>error: <strong>${escapeHtml(result.error)}</strong></div>` : ''}</div>${result.ok ? `<div class="timeline-shell"><div class="timeline-hint">클릭하면 worker 처리 과정을 조회합니다.</div>${timelineMarkup()}</div>` : ''}`;
        const toggle = () => toggleResult(item, result);
        item.addEventListener('click', toggle);
        item.addEventListener('keydown', (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); toggle(); } });
        debugList.append(item);
    });
}

async function toggleResult(item, result) {
    if (!result.ok || !result.data.stream_message_id) return;
    const expanded = item.classList.toggle('expanded');
    if (!expanded || result.monitoring) return;
    result.monitoring = true;
    item.querySelector('.timeline-hint').textContent = 'Redis 상태를 조회하는 중...';
    await monitorResult(item, result);
}

function sleep(milliseconds) { return new Promise((resolve) => setTimeout(resolve, milliseconds)); }

async function monitorResult(item, result) {
    const deadline = Date.now() + 30000;
    const endpoint = `/api/v1/chat-rooms/${encodeURIComponent(roomIdInput.value.trim())}/question-events/${encodeURIComponent(result.data.stream_message_id)}/status`;
    let lastError = null;
    while (Date.now() < deadline) {
        try {
            const response = await fetch(endpoint);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const status = await response.json();
            lastError = null;
            updateTimeline(item, status);
            if (status.status === 'ACKED') {
                item.querySelector('.timeline-hint').textContent = '처리 완료: assistant 저장 및 XACK까지 완료되었습니다.';
                return;
            }
        } catch (error) {
            lastError = error;
            item.querySelector('.timeline-hint').textContent = `상태 조회 재시도 중... (${error.message})`;
        }
        await sleep(Math.min(500, Math.max(0, deadline - Date.now())));
    }
    item.querySelector('.timeline-hint').textContent = lastError
        ? `30초 동안 상태 조회를 재시도했지만 완료되지 않았습니다. 마지막 오류: ${lastError.message}`
        : '30초 동안 ACK 상태를 확인하지 못했습니다.';
}

async function sendMessages() {
    if (!roomIdInput.value.trim()) { roomStatus.textContent = 'Room ID를 입력하거나 새 채팅방을 생성하세요.'; roomIdInput.focus(); return; }
    if (!markEmptyInputs()) { sendStatus.textContent = '모든 메시지의 content를 입력하세요.'; return; }
    const roomId = roomIdInput.value.trim();
    const contents = [...messageInputs.querySelectorAll('textarea')].map((input) => input.value.trim());
    sendButton.disabled = true;
    sendStatus.textContent = `${contents.length}개 요청을 동시에 전송하는 중...`;
    const results = await Promise.all(contents.map((content, index) => sendMessage(content, index, roomId)));
    renderResults(results);
    sendStatus.textContent = '전송 완료. 결과 카드를 클릭해 worker 처리 과정을 확인하세요.';
    sendButton.disabled = false;
}

messageCount.addEventListener('change', renderMessageInputs);
createRoomButton.addEventListener('click', createRoom);
sendButton.addEventListener('click', sendMessages);
clearResultsButton.addEventListener('click', () => { debugList.replaceChildren(); debugSummary.className = 'debug-summary empty'; debugSummary.textContent = '아직 전송한 요청이 없습니다.'; });
renderMessageInputs();
