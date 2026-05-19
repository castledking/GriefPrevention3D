import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';
import { execSync } from 'child_process';
import { releaseToModrinth } from './modrinth.js';

function log(...args) {
  console.log(`[${new Date().toISOString()}] [Modrinth]`, ...args);
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

function loadExternalDependencies(repoName) {
  const depPath = resolve(`.modrinth/${repoName.toLowerCase()}-dependencies.json`);
  if (existsSync(depPath)) {
    log(`Loading external dependencies from ${depPath}`);
    return JSON.parse(readFileSync(depPath, 'utf-8'));
  }
  return null;
}

function findJar(jarDir, pattern) {
  if (!pattern) return '';
  const glob = pattern.replace(/\{version\}/g, env('VERSION'));
  try {
    const result = execSync(`find ${jarDir} -path "${glob}" -type f 2>/dev/null | head -1`).toString().trim();
    return result || '';
  } catch { return ''; }
}

function findAllJars(jarDir, pattern) {
  if (!pattern) return [];
  const glob = pattern.replace(/\{version\}/g, env('VERSION'));
  try {
    const result = execSync(`find ${jarDir} -path "${glob}" -type f 2>/dev/null`).toString().trim();
    return result ? result.split('\n') : [];
  } catch { return []; }
}

async function main() {
  log('=== Modrinth Release ===');

  const token = env('MODRINTH_API_KEY');
  const repoName = env('REPO_NAME');
  const version = env('VERSION');
  const jarDir = env('JAR_DIR', '.');

  const repoConfig = loadRepoConfig(repoName);
  if (!repoConfig || !repoConfig.modrinth) {
    log(`No Modrinth config for ${repoName}, skipping`);
    return;
  }

  const notesPath = `/tmp/release-notes/v${version}.md`;
  let changelogMd = `Version ${version}`;
  if (existsSync(notesPath)) {
    const content = readFileSync(notesPath, 'utf-8').replace(/\r\n/g, '\n');
    const lines = content.split('\n');
    changelogMd = lines.slice(1).join('\n').trim() || changelogMd;
  }

  const allErrors = [];

  if (Array.isArray(repoConfig.modrinth)) {
    const projectIndex = env('PROJECT_INDEX', '');
    const projects = projectIndex !== ''
      ? [repoConfig.modrinth[parseInt(projectIndex, 10)]].filter(Boolean)
      : repoConfig.modrinth;

    for (const project of projects) {
      const jarsFromEnv = env('JAR_PATHS', '');
      const jars = jarsFromEnv
        ? jarsFromEnv.split(',').filter(p => p && existsSync(p))
        : findAllJars(jarDir, project.jar_pattern);
      if (!jars.length) {
        log(`No JARs found for ${project.slug}, skipping`);
        continue;
      }
      try {
        const result = await releaseToModrinth({
          token, projectId: project.project_id, version,
          name: `${repoName} v${version}`,
          changelogMd,
          gameVersions: project.game_versions || [],
          loaders: project.loaders || ['paper'],
          jarPaths: jars,
          dependencies: project.dependencies || [],
          versionType: 'release', featured: false, status: 'listed',
        });
        log(`Released ${project.slug}: ${result.id} (${result.version_number})`);
      } catch (err) {
        allErrors.push(`${project.slug}: ${err.message}`);
      }
    }
  } else {
    const jarPath = env('JAR_PATH');
    if (!jarPath || !existsSync(jarPath)) {
      log(`JAR not found at ${jarPath}, skipping Modrinth release`);
      return;
    }
    const projectId = repoConfig.modrinth_project_id || repoConfig.modrinth;
    const dependencies = loadExternalDependencies(repoName) || repoConfig.dependencies || [];
    try {
      const result = await releaseToModrinth({
        token, projectId, version,
        name: `${repoName} v${version}`,
        changelogMd,
        gameVersions: repoConfig.game_versions || [],
        loaders: repoConfig.loaders || ['paper'],
        jarPath, dependencies,
        versionType: 'release', featured: false, status: 'listed',
      });
      log(`Modrinth release complete: ${result.id} (${result.version_number})`);
    } catch (err) {
      allErrors.push(err.message);
    }
  }

  if (allErrors.length) die(allErrors.join('; '));
}

main().catch(err => die(err.message));
