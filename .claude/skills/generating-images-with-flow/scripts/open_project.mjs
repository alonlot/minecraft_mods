// Open a Flow project in flow-mcp's own Chrome, so flow_generate / flow_assets stop failing with
// "Signed in, but no project is open".
//
//   node open_project.mjs            -> reports the current URL; creates a new project if none is open
//   node open_project.mjs <url>      -> navigates to that existing project instead
//
// flow-mcp starts Chrome with --remote-debugging-port=9333 and its own profile
// (~/.flow-mcp/chrome-profile). That window is not reachable from the Claude in Chrome extension
// (different profile) or computer-use (browsers are read-only there), so we drive it over CDP.
const PORT = process.env.FLOW_CDP_PORT || 9333;

let list;
try {
  list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
} catch {
  console.error(`No Chrome on port ${PORT}. Call mcp__flow__flow_status first; it launches Flow's Chrome.`);
  process.exit(1);
}
const page = list.find(t => t.type === 'page' && t.url.includes('flow.google.com'));
if (!page) { console.error('Flow Chrome is running but has no flow.google.com tab.'); process.exit(1); }

const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(r => (ws.onopen = r));
let id = 0;
const pending = new Map();
ws.onmessage = m => {
  const d = JSON.parse(m.data);
  if (pending.has(d.id)) { pending.get(d.id)(d.result?.result?.value); pending.delete(d.id); }
};
const evaluate = expression => new Promise(r => {
  pending.set(++id, r);
  ws.send(JSON.stringify({ id, method: 'Runtime.evaluate',
    params: { expression, awaitPromise: true, returnByValue: true } }));
});
// after a navigation the old execution context dies and evaluate can come back undefined: poll
const settle = async ms => {
  for (let waited = 0; waited < ms + 10000; waited += 1000) {
    const href = await evaluate(`new Promise(r => setTimeout(() => r(location.href), 1000))`);
    if (href && waited >= ms - 1000) return href;
  }
  return 'unknown (page did not answer)';
};

const target = process.argv[2];
let url = await evaluate('location.href');
if (target) {
  await evaluate(`location.href = ${JSON.stringify(target)}; 'ok'`);
  url = await settle(6000);
} else if (!url.includes('/project/')) {
  if (!url.startsWith('https://flow.google.com/?') && url !== 'https://flow.google.com/') {
    await evaluate(`location.href = 'https://flow.google.com/?hl=en'; 'ok'`);
    await settle(5000);
  }
  const clicked = await evaluate(`(() => { const b = document.querySelector('button.new-project-button');
    if (!b) return false; b.click(); return true; })()`);
  if (!clicked) { console.error('No "New project" button on the Flow home page (signed out?).'); ws.close(); process.exit(1); }
  url = await settle(6000);
}
console.log(url.includes('/project/') ? `project open: ${url}` : `still no project: ${url}`);
ws.close();
