import { readFileSync, statSync } from 'fs';
import { resolve } from 'path';

const API_BASE = 'https://api.modrinth.com/v3';

function log(...args) {
  console.log(`[${new Date().toISOString()}] [Modrinth]`, ...args);
}

export async function releaseToModrinth({
  token,
  projectId,
  version,
  name,
  changelogMd,
  gameVersions,
  loaders,
  jarPath,
  jarPaths,
  dependencies = [],
  versionType = 'release',
  featured = false,
  status = 'listed',
}) {
  const files = jarPaths ? (Array.isArray(jarPaths) ? jarPaths : [jarPaths]) : (jarPath ? [jarPath] : []);
  if (!files.length) throw new Error('No jar files provided');

  log(`Creating version ${version} for project ${projectId}`);
  log(`Game versions: ${gameVersions.join(', ')}`);
  log(`Loaders: ${loaders.join(', ')}`);
  log(`Files: ${files.join(', ')}`);

  const file_parts = files.map((_, i) => `file${i}`);

  const metadata = {
    name: name || version,
    project_id: projectId,
    version_number: version,
    changelog: changelogMd || '',
    game_versions: gameVersions,
    version_type: versionType,
    loaders: loaders,
    featured: featured,
    status: status,
    file_parts: file_parts,
    primary_file: file_parts[0],
    dependencies: dependencies,
  };

  const metadataJson = JSON.stringify(metadata);

  const boundary = `----WebKitFormBoundary${Math.random().toString(36).slice(2, 10)}`;
  const encoder = new TextEncoder();
  const chunks = [];

  // metadata part
  chunks.push(encoder.encode(`--${boundary}\r\n`));
  chunks.push(encoder.encode('Content-Disposition: form-data; name="data"\r\n'));
  chunks.push(encoder.encode('Content-Type: application/json\r\n\r\n'));
  chunks.push(encoder.encode(metadataJson));
  chunks.push(encoder.encode('\r\n'));

  // file parts
  for (let i = 0; i < files.length; i++) {
    const filePath = files[i];
    const jarBytes = readFileSync(filePath);
    const jarName = filePath.split('/').pop() || `${version}.jar`;
    chunks.push(encoder.encode(`--${boundary}\r\n`));
    chunks.push(encoder.encode(`Content-Disposition: form-data; name="file${i}"; filename="${jarName}"\r\n`));
    chunks.push(encoder.encode('Content-Type: application/java-archive\r\n\r\n'));
    chunks.push(jarBytes);
    chunks.push(encoder.encode('\r\n'));
  }

  // end boundary
  chunks.push(encoder.encode(`--${boundary}--\r\n`));

  // Combine all chunks
  const bodyLength = chunks.reduce((sum, chunk) => sum + chunk.byteLength, 0);
  const body = new Uint8Array(bodyLength);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }

  log(`Request body size: ${(bodyLength / 1024 / 1024).toFixed(2)} MB`);

  const response = await fetch(`${API_BASE}/version`, {
    method: 'POST',
    headers: {
      'Authorization': token,
      'User-Agent': 'spigot-release-automation/1.0',
      'Content-Type': `multipart/form-data; boundary=${boundary}`,
      'Content-Length': String(bodyLength),
    },
    body: body,
  });

  const responseText = await response.text();

  if (!response.ok) {
    let detail = responseText;
    try {
      const parsed = JSON.parse(responseText);
      detail = parsed.description || parsed.error || responseText;
    } catch {}
    throw new Error(`Modrinth API ${response.status}: ${detail}`);
  }

  const result = JSON.parse(responseText);
  log(`Version created: ${result.id} (${result.version_number})`);
  return result;
}
