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

function gameVersionTypeId(version) {
  return version.gameVersionTypeID ?? version.gameVersionTypeId ?? version.gameVersionTypeID;
}

function resolveGameVersionIds(availableVersions, configuredVersions) {
  const selections = [];
  const missing = [];
  for (const versionName of configuredVersions) {
    const key = normalizeVersionKey(versionName);
    const slug = slugFor(versionName);
    const candidates = availableVersions.filter(version =>
      version &&
      version.id !== undefined &&
      (
        (version.name && normalizeVersionKey(version.name) === key) ||
        (version.slug && normalizeVersionKey(version.slug) === slug)
      )
    );

    if (!candidates.length) {
      missing.push(versionName);
    } else {
      selections.push({
        name: versionName,
        candidates,
        index: 0,
      });
    }
  }

  if (missing.length) {
    throw new Error(`Could not resolve CurseForge game version IDs for: ${missing.join(', ')}`);
  }

  return selectedGameVersionState(selections);
}

function selectedGameVersionState(selections) {
  const ids = [];
  const resolved = [];

  for (const selection of selections) {
    const version = selection.candidates[selection.index];
    if (!version || ids.includes(version.id)) continue;

    ids.push(version.id);
    const candidateCount = selection.candidates.length;
    resolved.push(
      `${selection.name}=${version.id}:${gameVersionTypeId(version) ?? 'unknown'}:${selection.index + 1}/${candidateCount}`
    );
  }

  return { ids, resolved, selections };
}

function replaceInvalidGameVersion(selections, invalidId) {
  const selected = selections.find(selection => selection.candidates[selection.index]?.id === invalidId);
  if (!selected) {
    return false;
  }

  selected.index += 1;
  if (selected.index < selected.candidates.length) {
    const replacement = selected.candidates[selected.index];
    log(
      `CurseForge rejected ${selected.name} ID ${invalidId}; trying alternate ID ${replacement.id}` +
      ` (${selected.index + 1}/${selected.candidates.length})`
    );
  } else {
    log(`CurseForge rejected ${selected.name} ID ${invalidId}; no alternate IDs remain, dropping this version`);
  }

  return true;
}

function invalidGameVersionId(error) {
  const match = String(error.message || error).match(/Invalid game version ID:\s*(\d+)/i);
  return match ? Number(match[1]) : null;
}

async function uploadWithInvalidVersionRetry({ token, projectId, metadata, jarPath }) {
  const rejected = [];
  const selections = metadata.gameVersionSelections;

  while (true) {
    const state = selectedGameVersionState(selections);
    if (!state.ids.length) {
      break;
    }

    log(`Trying CurseForge game versions: ${state.resolved.join(', ')}`);
    try {
      return await uploadToCurseForge({
        token,
        projectId,
        metadata: {
          ...metadata,
          gameVersionSelections: undefined,
          gameVersions: state.ids,
        },
        jarPath,
      });
    } catch (error) {
      const badId = invalidGameVersionId(error);
      if (badId == null || !state.ids.includes(badId)) {
        throw error;
      }

      rejected.push(badId);
      replaceInvalidGameVersion(selections, badId);
    }
  }

  throw new Error(`CurseForge rejected every resolved game version ID: ${rejected.join(', ')}`);
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
  const resolvedGameVersions = resolveGameVersionIds(availableVersions, configuredVersions);
  const gameVersions = resolvedGameVersions.ids;
  const displayVersion = version.startsWith('v') ? version : `v${version}`;
  const metadata = {
    changelog: releaseNotesFor(version),
    changelogType: 'markdown',
    displayName: `${repoName} ${displayVersion}`,
    gameVersions,
    gameVersionSelections: resolvedGameVersions.selections,
    releaseType: releaseTypeFor(version),
  };

  if (curseforge.relations) {
    metadata.relations = curseforge.relations;
  }

  log(`Resolved ${gameVersions.length} CurseForge game version IDs`);
  log(`Resolved versions: ${resolvedGameVersions.resolved.join(', ')}`);
  log(`Release type: ${metadata.releaseType}`);
  if (dryRun) {
    log('DRY RUN - skipping CurseForge submission');
    log(JSON.stringify({
      ...metadata,
      gameVersionSelections: undefined,
    }, null, 2));
    return;
  }

  const result = await uploadWithInvalidVersionRetry({ token, projectId, metadata, jarPath });
  log(`CurseForge upload complete: file id ${result.id}`);
}

main().catch(err => die(err.message));
