#!/usr/bin/env node
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import chokidar from 'chokidar';

const docsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const watchedExtensions = new Set(['.css', '.inc', '.js', '.json', '.mjs', '.py', '.rst', '.ts', '.txt']);
const watchedFilenames = new Set(['docutils.conf']);
const excludedTopLevel = new Set(['.astro', '_build', 'dist', 'node_modules', 'public', 'src']);

let astro;
let syncing = false;
let pendingSync = false;
let restarting = false;
let stopping = false;
let debounce;
const astroBin = path.join(docsRoot, 'node_modules', '.bin', 'astro');
const astroArgs = process.argv.slice(2);

function isWatched(file) {
  const absoluteFile = path.isAbsolute(file) ? file : path.resolve(docsRoot, file);
  const relPath = path.relative(docsRoot, absoluteFile).split(path.sep).join('/');
  if (relPath.startsWith('..') || path.isAbsolute(relPath)) return false;
  if (
    relPath.startsWith('.astro/') ||
    relPath.startsWith('_build/') ||
    relPath.startsWith('dist/') ||
    relPath.startsWith('node_modules/') ||
    relPath.startsWith('public/sphinx/') ||
    relPath.startsWith('src/content/docs/')
  ) {
    return false;
  }
  return watchedExtensions.has(path.extname(relPath)) || watchedFilenames.has(path.basename(relPath));
}

function getWatchEntries() {
  const entries = [];
  for (const dirent of fs.readdirSync(docsRoot, { withFileTypes: true })) {
    if (excludedTopLevel.has(dirent.name)) continue;
    const entry = path.join(docsRoot, dirent.name);
    if (dirent.isDirectory()) {
      entries.push(entry);
    } else if (dirent.isFile() && isWatched(entry)) {
      entries.push(entry);
    }
  }
  return entries;
}

function run(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: docsRoot,
      env: { ...process.env, ASTRO_TELEMETRY_DISABLED: '1' },
      stdio: 'inherit',
    });
    child.on('exit', (code, signal) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`${command} ${args.join(' ')} exited with ${signal || code}`));
      }
    });
  });
}

function runAllowingFailure(command, args) {
  return new Promise((resolve) => {
    const child = spawn(command, args, {
      cwd: docsRoot,
      env: { ...process.env, ASTRO_TELEMETRY_DISABLED: '1' },
      stdio: 'inherit',
    });
    child.on('exit', resolve);
  });
}

async function sync() {
  await run(process.env.PYTHON || 'python', ['scripts/sphinx_to_starlight.py']);
}

function startAstro() {
  const child = spawn(astroBin, ['dev', '--host', '0.0.0.0', ...astroArgs], {
    cwd: docsRoot,
    env: { ...process.env, ASTRO_TELEMETRY_DISABLED: '1' },
    stdio: 'inherit',
  });
  astro = child;
  child.on('exit', (code) => {
    if (astro === child) astro = undefined;
    if (!stopping && !restarting && code !== 0) process.exit(code ?? 1);
  });
}

async function stopAstroProcess() {
  const child = astro;
  if (child && child.exitCode === null) {
    await new Promise((resolve) => {
      child.once('exit', resolve);
      child.kill('SIGTERM');
      setTimeout(() => {
        if (child.exitCode === null) child.kill('SIGKILL');
      }, 5000).unref();
    });
  }
  if (astro === child) astro = undefined;
}

async function restartAstro() {
  restarting = true;
  try {
    await stopAstroProcess();
    await runAllowingFailure(astroBin, ['dev', 'stop']);
    startAstro();
  } finally {
    restarting = false;
  }
}

async function stopAstro() {
  await stopAstroProcess();
  await runAllowingFailure(astroBin, ['dev', 'stop']);
}

async function regenerateContent() {
  if (syncing) {
    pendingSync = true;
    return;
  }
  syncing = true;
  do {
    pendingSync = false;
    console.log('[docs-dev] Regenerating Starlight content');
    try {
      await sync();
      // Generated docs are ignored build artifacts, so Astro's content watcher
      // can retain stale pages when the converter rewrites them.
      await restartAstro();
    } catch (error) {
      console.error(`[docs-dev] ${error.message}`);
    }
  } while (pendingSync && !stopping);
  syncing = false;
}

function schedule(file) {
  if (!isWatched(file)) return;
  clearTimeout(debounce);
  debounce = setTimeout(regenerateContent, 750);
}

async function main() {
  await sync();
  startAstro();

  const watcher = chokidar.watch(getWatchEntries(), {
    ignoreInitial: true,
    awaitWriteFinish: {
      stabilityThreshold: 100,
      pollInterval: 50,
    },
  });
  watcher.on('change', schedule);
  watcher.on('add', schedule);
  watcher.on('unlink', schedule);

  const stop = async () => {
    stopping = true;
    await watcher.close();
    await stopAstro();
    process.exit(0);
  };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
}

main().catch((error) => {
  console.error(`[docs-dev] ${error.message}`);
  process.exit(1);
});
