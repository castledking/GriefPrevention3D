import { existsSync, readFileSync } from 'fs';

const API_BASE = 'https://minecraft.curseforge.com/api';

function log(...args) {
  console.log(`[${new Date().toISOString()}] [CurseForge]`, ...args);
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

function loadRepoConfig(repoName) {
  const configPath = new URL('spigot-resource-ids.json', import.meta.url);
  const config = JSON.parse(readFileSync(configPath, 'utf-8'));
  return config[repoName] || null;
}

function releaseTypeFor(version) {
  const normalized = version.toLowerCase();
  if (normalized.includes('alpha')) return 'alpha';
  if (normalized.includes('beta')) return 'beta';
  return env('CURSEFORGE_RELEASE_TYPE', 'release');
}

function releaseNotesFor(version) {
  const notesPath = `/tmp/release-notes/v${version}.md`;
  if (!existsSync(notesPath)) {
    return `Version ${version}`;
  }

  const content = readFileSync(notesPath, 'utf-8').replace(/\r\n/g, '\n');
  const lines = content.split('\n');
  return lines.slice(1).join('\n').trim() || `Version ${version}`;
}

function normalizeVersionKey(value) {
  return value.trim().toLowerCase();
}

function slugFor(value) {
  return normalizeVersionKey(value)
    .replace(/\./g, '-')
    .replace(/[^a-z0-9-]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

async function fetchGameVersions(token) {
  const response = await fetch(`${API_BASE}/game/versions`, {
    headers: {
      'X-Api-Token': token,
      'User-Agent': 'GriefPrevention3DReleaseBot/1.0',
    },
  });
  const responseText = await response.text();
  if (!response.ok) {
    throw new Error(`Game versions API ${response.status}: ${responseText}`);
  }

  const parsed = JSON.parse(responseText);
  if (Array.isArray(parsed)) return parsed;
  if (Array.isArray(parsed.data)) return parsed.data;
  if (Array.isArray(parsed.versions)) return parsed.versions;
  throw new Error('Game versions API returned an unrecognized response shape');
}

function resolveGameVersionIds(availableVersions, configuredVersions) {
  const byName = new Map();
  const bySlug = new Map();

  for (const version of availableVersions) {
    if (version && version.name) byName.set(normalizeVersionKey(version.name), version.id);
    if (version && version.slug) bySlug.set(normalizeVersionKey(version.slug), version.id);
  }

  const ids = [];
  const missing = [];
  for (const versionName of configuredVersions) {
    const key = normalizeVersionKey(versionName);
    const id = byName.get(key) || bySlug.get(slugFor(versionName));
    if (id === undefined) {
      missing.push(versionName);
    } else if (!ids.includes(id)) {
      ids.push(id);
    }
  }

  if (missing.length) {
    throw new Error(`Could not resolve CurseForge game version IDs for: ${missing.join(', ')}`);
  }

  return ids;
}

function buildMultipartBody(metadata, jarPath) {
  const boundary = `----GP3DCurseForge${Math.random().toString(36).slice(2, 10)}`;
  const encoder = new TextEncoder();
  const chunks = [];
  const jarBytes = readFileSync(jarPath);
  const jarName = jarPath.split('/').pop() || 'GriefPrevention3D.jar';

  chunks.push(encoder.encode(`--${boundary}\r\n`));
  chunks.push(encoder.encode('Content-Disposition: form-data; name="metadata"\r\n'));
  chunks.push(encoder.encode('Content-Type: application/json\r\n\r\n'));
  chunks.push(encoder.encode(JSON.stringify(metadata)));
  chunks.push(encoder.encode('\r\n'));

  chunks.push(encoder.encode(`--${boundary}\r\n`));
  chunks.push(encoder.encode(`Content-Disposition: form-data; name="file"; filename="${jarName}"\r\n`));
  chunks.push(encoder.encode('Content-Type: application/java-archive\r\n\r\n'));
  chunks.push(jarBytes);
  chunks.push(encoder.encode('\r\n'));

  chunks.push(encoder.encode(`--${boundary}--\r\n`));

  const bodyLength = chunks.reduce((sum, chunk) => sum + chunk.byteLength, 0);
  const body = new Uint8Array(bodyLength);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }

  return { body, boundary, bodyLength };
}

async function uploadToCurseForge({ token, projectId, metadata, jarPath }) {
  const { body, boundary, bodyLength } = buildMultipartBody(metadata, jarPath);

  log(`Uploading ${jarPath} to project ${projectId}`);
  log(`Request body size: ${(bodyLength / 1024 / 1024).toFixed(2)} MB`);

  const response = await fetch(`${API_BASE}/projects/${projectId}/upload-file`, {
    method: 'POST',
    headers: {
      'X-Api-Token': token,
      'User-Agent': 'GriefPrevention3DReleaseBot/1.0',
      'Content-Type': `multipart/form-data; boundary=${boundary}`,
      'Content-Length': String(bodyLength),
    },
    body,
  });
  const responseText = await response.text();

  if (!response.ok) {
    let detail = responseText;
    try {
      const parsed = JSON.parse(responseText);
      detail = parsed.error || parsed.message || parsed.description || responseText;
    } catch {}
    throw new Error(`CurseForge upload API ${response.status}: ${detail}`);
  }

  return JSON.parse(responseText);
}

async function main() {
  log('=== CurseForge Release ===');

  const token = env('CURSEFORGE_API_TOKEN');
  const repoName = env('REPO_NAME');
  const version = env('VERSION');
  const jarPath = env('JAR_PATH');
  const dryRun = env('DRY_RUN', 'false') === 'true';

  if (!existsSync(jarPath)) {
    log(`JAR not found at ${jarPath}, skipping CurseForge release`);
    return;
  }

  const repoConfig = loadRepoConfig(repoName);
  const curseforge = repoConfig && repoConfig.curseforge;
  if (!curseforge) {
    log(`No CurseForge config for ${repoName}, skipping`);
    return;
  }

  const projectId = env('CURSEFORGE_PROJECT_ID', String(curseforge.project_id));
  const configuredVersions = curseforge.game_versions || [];
  const availableVersions = await fetchGameVersions(token);
  const gameVersions = resolveGameVersionIds(availableVersions, configuredVersions);
  const displayVersion = version.startsWith('v') ? version : `v${version}`;
  const metadata = {
    changelog: releaseNotesFor(version),
    changelogType: 'markdown',
    displayName: `${repoName} ${displayVersion}`,
    gameVersions,
    releaseType: releaseTypeFor(version),
  };

  if (curseforge.relations) {
    metadata.relations = curseforge.relations;
  }

  log(`Resolved ${gameVersions.length} CurseForge game version IDs`);
  log(`Release type: ${metadata.releaseType}`);
  if (dryRun) {
    log('DRY RUN - skipping CurseForge submission');
    log(JSON.stringify(metadata, null, 2));
    return;
  }

  const result = await uploadToCurseForge({ token, projectId, metadata, jarPath });
  log(`CurseForge upload complete: file id ${result.id}`);
}

main().catch(err => die(err.message));
