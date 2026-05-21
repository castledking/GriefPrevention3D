import { readFileSync, existsSync } from 'fs';

function log(...args) {
  console.log(`[${new Date().toISOString()}] [Discord]`, ...args);
}

function die(...args) {
  console.error(`[${new Date().toISOString()}] FATAL:`, ...args);
  process.exit(1);
}

function env(key, fallback) {
  const val = process.env[key];
  if (val === undefined || val === '') {
    if (fallback !== undefined) return fallback;
    die(`Missing env var: ${key}`);
  }
  return val;
}

async function sendDiscordNotification() {
  log('=== Discord Notification ===');

  const webhookUrl = env('DISCORD_WEBHOOK_URL');
  const version = env('VERSION');
  const repoName = env('REPO_NAME', 'unknown');
  const configPath = env('DISCORD_CONFIG_PATH', '/tmp/discord-config.yml');

  if (!webhookUrl || webhookUrl === '') {
    log('No DISCORD_WEBHOOK_URL set, skipping');
    return;
  }

  // Build the embed payload
  const payload = {
    username: repoName,
    avatar_url: undefined,
    content: '',
    embeds: [],
  };

  let embed = {
    title: `${repoName} v${version}`,
    description: '',
    url: `https://github.com/${env('REPO_OWNER', 'castledking')}/${repoName}`,
    color: 1474606,
    timestamp: new Date().toISOString(),
    footer: { text: 'Released via GitHub Actions' },
  };

  // Read release notes
  const notesPath = `/tmp/release-notes/v${version}.md`;
  if (existsSync(notesPath)) {
    const content = readFileSync(notesPath, 'utf-8').replace(/\r\n/g, '\n');
    const lines = content.split('\n');
    if (lines[0]) embed.title = lines[0].trim();
    const body = lines.slice(1).join('\n').trim().substring(0, 4000);
    if (body) embed.description = body;
  }

  // Try reading discord-updater-webhook.yml for custom config
  if (existsSync(configPath)) {
    try {
      const yaml = readFileSync(configPath, 'utf-8');
      const config = parseSimpleYaml(yaml);

      if (config.message?.username) payload.username = config.message.username;
      if (config.message?.avatar_url) payload.avatar_url = config.message.avatar_url;
      if (config.message?.content) payload.content = config.message.content;
      if (config.embed?.color) embed.color = config.embed.color;
      if (config.embed?.footer?.text) embed.footer.text = config.embed.footer.text;
      if (config.embed?.author) {
        embed.author = {
          name: config.embed.author.name || '',
          url: config.embed.author.url || '',
          icon_url: config.embed.author.icon_url || '',
        };
      }
      if (config.embed?.thumbnail?.url) embed.thumbnail = { url: config.embed.thumbnail.url };
      if (config.embed?.image?.url) embed.image = { url: config.embed.image.url };
      if (config.embed?.timestamp === false) delete embed.timestamp;
      if (config.embed?.fields) embed.fields = config.embed.fields;
    } catch (e) {
      log('Failed to parse discord config:', e.message);
    }
  }

  payload.embeds = [embed];

  log(`Sending Discord notification for ${repoName} v${version}`);

  const response = await fetch(webhookUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Discord webhook ${response.status}: ${text}`);
  }

  log('Discord notification sent');
}

function parseSimpleYaml(text) {
  const result = {};
  let current = result;
  const path = [result];

  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    const indent = line.search(/\S/);
    const keyMatch = trimmed.match(/^(\w[\w-]*):\s*(.*)$/);
    if (!keyMatch) continue;

    const [, key, val] = keyMatch;

    // Adjust depth based on indent
    const depth = Math.floor(indent / 2);
    while (path.length > depth + 1) path.pop();
    current = path[path.length - 1];

    if (val === '') {
      // Nested object
      const obj = {};
      current[key] = obj;
      path.push(obj);
      current = obj;
    } else if (val === '[]') {
      current[key] = [];
    } else {
      // Parse value
      let parsed = val;
      if (parsed === 'true') parsed = true;
      else if (parsed === 'false') parsed = false;
      else if (/^\d+$/.test(parsed)) parsed = parseInt(parsed, 10);
      else parsed = parsed.replace(/^"(.*)"$/, '$1');
      current[key] = parsed;
    }
  }

  return result;
}

sendDiscordNotification().catch(err => die(err.message));
