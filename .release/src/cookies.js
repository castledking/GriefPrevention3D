import { writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import readline from 'readline';

const __dirname = dirname(fileURLToPath(import.meta.url));
const COOKIE_FILE = resolve(__dirname, '..', '.spigot-cookies.json');

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
});

function ask(question) {
  return new Promise(resolve => rl.question(question, resolve));
}

console.log(`
=== SpigotMC Cookie Generator ===

Cloudflare blocks automated browsers, so we'll extract cookies
from your REAL browser where you're already logged into SpigotMC.

Steps:
1. Open Chrome/Edge/Firefox and go to spigotmc.org
2. Make sure you're LOGGED IN
3. Open DevTools (F12) → Application tab → Cookies → spigotmc.org
   - Chrome/Edge: Look under "Application" → "Cookies" in left sidebar
   - Firefox: Look under "Storage" → "Cookies" in left sidebar
4. Right-click ANY cookie row → Select All (Ctrl+A) → Copy (Ctrl+C)
5. Paste the cookie data below (it will be pasted into this terminal):

`);

async function main() {
  const raw = await ask('Paste cookies here (end with Ctrl+D on a blank line, or just paste and hit Enter twice):\n\n');

  if (!raw || raw.trim().length === 0) {
    console.log('\nNo input received. Generating manual instructions instead...\n');

    console.log(`
=== MANUAL COOKIE EXTRACTION ===

Method 1 — DevTools (Chrome/Edge):
  1. Go to spigotmc.org (logged in)
  2. F12 → Application → Cookies → spigotmc.org
  3. Click each cookie and note the Name/Value pairs
  4. Run this in the DevTools Console:

     const cookies = document.cookie.split('; ').map(c => {
       const [n, ...v] = c.split('=');
       return {name: n, value: v.join('='), domain: 'spigotmc.org', path: '/'};
     });
     console.log(JSON.stringify(cookies));
     console.log(btoa(JSON.stringify(cookies)));

  5. The base64 string printed is your SPIGOT_COOKIES secret

NOTE: document.cookie may miss HttpOnly cookies (xf_session, xf_user).
If the above doesn't work, use a cookie export extension like
"Get cookies.txt" or "Cookie Quick Manager" and export as JSON/Netscape format.

Method 2 — Extension (easier):
  1. Install "Cookie-Editor" extension (by Moustachauve)
  2. Go to spigotmc.org (logged in)
  3. Click the Cookie-Editor icon → "Export" (bottom) → copies JSON
  4. Base64 encode that JSON:
     echo -n 'PASTE_JSON_HERE' | base64
  5. Use the output as SPIGOT_COOKIES secret
`);
    process.exit(0);
  }

  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      writeFileSync(COOKIE_FILE, JSON.stringify(parsed, null, 2));
      const b64 = Buffer.from(JSON.stringify(parsed)).toString('base64');
      console.log(`\nCookies saved to: ${COOKIE_FILE}`);
      console.log(`\nAdd this as GitHub secret SPIGOT_COOKIES:\n${b64}\n`);
    } else {
      console.error('Expected a JSON array of cookie objects');
      process.exit(1);
    }
  } catch {
    console.error('Invalid JSON. Make sure you copied the cookie data correctly.');
    console.error('Try the manual method described above.');
    process.exit(1);
  }

  rl.close();
}

main();
