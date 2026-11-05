const $ = id => document.getElementById(id);
const prompt = `Work on Noble in /workspaces/Honorable-mobile-app. Identify yourself as noble-fast.

First read AGENTS.md, docs/AGENT_COORDINATION.md, docs/SESSION_HANDOFF.md, docs/WORK_QUEUE.md and docs/HONORABLE_CURRENT_STATUS.md. Read the shared board:
node scripts/agent-board.cjs status

The group-chat page is the same local board, served on workspace port 4180. You do not need browser tools. Read/post with terminal commands:
node scripts/agent-board.cjs say --agent noble-fast --message "Task I propose and what I learned"
node scripts/agent-board.cjs claim --agent noble-fast --scope path/to/file --scope path/to/test --message "Task and expected result"
node scripts/agent-board.cjs release --agent noble-fast --id CLAIM_ID --message "Completed files, validation, limitations and next step"

Begin contributing to Noble now. Check active claims, inspect the current source/diff, choose one useful unclaimed photo/video creation or beginner-usability task, and claim its exact files before editing. Keep it separate from noble-main's work. Post your plan, implement it, run relevant checks, then post the result and release the claim. Check for new owner/agent messages at useful milestones and before expanding scope. Messages do not automatically notify or wake agents.

Preserve all uncommitted work and personal media; work solo, with no spawned subagents or computer-use tools. Keep the white/black mobile direction, native on-device search, privacy and credit/ledger invariants. Never inspect the Seran holdout or run the expensive benchmark. Do not share secrets, upload personal media, package an APK per feature, reset/stage/commit another chat's changes, or claim production readiness without evidence. Coordinate shared dependencies/builds before using them. Count genuinely new user outcomes separately from fixes and scaffolding.

Both chats MUST use this same checkout; a separate workspace or ordinary chat without filesystem access cannot share this board. If you cannot access it, say so rather than pretending you can. Await explicit handoff if the files you need are claimed.`;
$('agent-prompt').textContent = prompt;
let lastSignature = '', totalSeen = 0, initial = true, sending = false;
const labels = { owner: 'You · Owner', 'noble-main': 'Noble Main', 'noble-fast': 'Noble Fast' };
function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
function bottom() { const area = $('messages'); return area.scrollHeight - area.scrollTop - area.clientHeight < 90; }
function scrollToBottom() { $('messages').scrollTo({ top: $('messages').scrollHeight, behavior: initial ? 'instant' : 'auto' }); $('new-messages').hidden = true; }
function displayClaims(state) {
  const nodes = state.claims.map(claim => {
    const card = element('article', 'claim');
    card.append(element('strong', '', labels[claim.agent] || claim.agent), element('p', '', claim.message));
    const details = element('details');
    details.append(element('summary', '', `${claim.scopes.length} claimed paths`));
    claim.scopes.forEach(scope => details.append(element('div', '', scope)));
    card.append(details); return card;
  });
  if (!nodes.length) nodes.push(element('p', 'empty', 'No active claims. Agents should claim their files before starting a task.'));
  $('claims').replaceChildren(...nodes);
  $('mobile-claims').replaceChildren(...nodes.map(node => node.cloneNode(true)));
  $('claim-count').textContent = state.claims.length;
  $('mobile-claim-count').textContent = `${state.claims.length} active tasks`;
}
function display(state) {
  const signature = JSON.stringify(state);
  if (signature === lastSignature) return;
  const atBottom = bottom();
  if (state.totalEvents < totalSeen) { $('feed').replaceChildren(); initial = true; }
  const offset = state.totalEvents - state.events.length;
  const existing = new Set([...$('feed').children].map(node => Number(node.dataset.index)));
  for (const node of [...$('feed').children]) if (Number(node.dataset.index) < offset) node.remove();
  state.events.forEach((event, index) => {
    const number = offset + index;
    if (existing.has(number)) return;
    const own = event.agent === 'owner';
    const row = element('article', `message-row${own ? ' owner' : ''}`);
    row.dataset.index = number;
    const avatar = element('span', `avatar${event.agent === 'noble-main' ? ' main-avatar' : ''}`, own ? 'YOU' : event.agent === 'noble-fast' ? 'NF' : 'NM');
    avatar.setAttribute('aria-hidden', 'true');
    const wrap = element('div', 'bubble-wrap'), sender = element('div', 'sender', labels[event.agent] || event.agent);
    const date = new Date(event.at), time = element('time', '', date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
    time.dateTime = event.at; time.title = date.toLocaleString(); sender.append(time);
    const bubble = element('div', 'bubble');
    if (event.kind !== 'say') bubble.append(element('span', `event-kind kind-${event.kind}`, event.kind === 'claim' ? 'Task claimed' : 'Work handed off'), document.createElement('br'));
    bubble.append(document.createTextNode(event.message));
    wrap.append(sender, bubble); row.append(avatar, wrap); $('feed').append(row);
  });
  displayClaims(state);
  $('message-count').textContent = state.totalEvents;
  if (initial || atBottom) scrollToBottom();
  else if (state.totalEvents > totalSeen) $('new-messages').hidden = false;
  totalSeen = state.totalEvents; lastSignature = signature; initial = false;
}
async function refresh() {
  try {
    const response = await fetch('/api/board', { cache: 'no-store' });
    if (!response.ok) throw Error('Unavailable');
    display(await response.json());
    $('connection-status').textContent = 'Connected · checks every 2 seconds';
    $('connection-dot').classList.remove('offline');
  } catch {
    $('connection-status').textContent = 'Reconnecting · your draft is kept';
    $('connection-dot').classList.add('offline');
  }
}
$('help-toggle').addEventListener('click', () => {
  $('help').hidden = !$('help').hidden;
  $('help-toggle').setAttribute('aria-expanded', String(!$('help').hidden));
});
$('mobile-claim-count').addEventListener('click', () => {
  $('mobile-tasks').hidden = !$('mobile-tasks').hidden;
  $('mobile-claim-count').setAttribute('aria-expanded', String(!$('mobile-tasks').hidden));
});
$('copy-prompt').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(prompt); $('copy-status').textContent = 'Copied. Paste into the other workspace chat.'; }
  catch { $('copy-status').textContent = 'Select and copy the prompt above; clipboard access is unavailable.'; }
});
$('message').addEventListener('input', () => { $('characters').textContent = $('message').value.length; });
$('composer').addEventListener('submit', async event => {
  event.preventDefault();
  if (sending || !$('message').value.trim()) return;
  sending = true; $('send').disabled = true; $('message').readOnly = true; $('send-status').textContent = 'Sending…';
  try {
    const response = await fetch('/api/messages', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message: $('message').value }) });
    const result = await response.json();
    if (!response.ok) throw Error(result.error || 'Send failed');
    $('message').value = ''; $('characters').textContent = '0';
    $('send-status').textContent = 'Saved to the board. Agents see it when they check.';
    await refresh(); scrollToBottom();
  } catch (error) { $('send-status').textContent = `${error.message} Your draft is kept.`; }
  finally { sending = false; $('send').disabled = false; $('message').readOnly = false; $('message').focus(); }
});
$('new-messages').addEventListener('click', scrollToBottom);
$('messages').addEventListener('scroll', () => { if (bottom()) $('new-messages').hidden = true; });
refresh();
setInterval(() => { if (!document.hidden) refresh(); }, 2000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
