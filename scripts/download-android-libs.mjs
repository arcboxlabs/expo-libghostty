#!/usr/bin/env node
// Fetch the pre-built Android libghostty-vt static libraries pinned in
// vendor-manifest.json into android/vendor/. Runs on postinstall; idempotent
// (skips when the extracted tree already matches the pinned checksum).
//
// The tarball is produced by CI (see .github/workflows) and contains:
//   include/ghostty/**  arm64-v8a/libghostty-vt.a  x86_64/libghostty-vt.a
//
// When vendor-manifest.json has no `libghostty-vt` entry yet (pre-release
// checkouts), this script is a no-op so installs don't fail.

import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const manifest = JSON.parse(await readFile(join(root, 'vendor-manifest.json'), 'utf8'));
const android = manifest['libghostty-vt']?.android;

const vendorDir = join(root, 'android', 'vendor');
const stampFile = join(vendorDir, '.checksum');

if (!android) {
  if (existsSync(vendorDir)) {
    console.log('[expo-libghostty] android vendor present (no manifest pin); skipping');
  } else {
    console.log('[expo-libghostty] no android pin in vendor-manifest.json; skipping');
  }
  process.exit(0);
}

const { url, sha256 } = android;

if (existsSync(vendorDir) && existsSync(stampFile)) {
  const stamp = (await readFile(stampFile, 'utf8')).trim();
  if (stamp === sha256) {
    console.log('[expo-libghostty] android libghostty-vt up to date');
    process.exit(0);
  }
}

console.log(`[expo-libghostty] downloading android libghostty-vt (${url})`);
const response = await fetch(url, { redirect: 'follow' });
if (!response.ok) throw new Error(`download failed: HTTP ${response.status}`);
const tarball = Buffer.from(await response.arrayBuffer());

const actual = createHash('sha256').update(tarball).digest('hex');
if (actual !== sha256) {
  throw new Error(`checksum mismatch: expected ${sha256}, got ${actual}`);
}

await rm(vendorDir, { recursive: true, force: true });
await mkdir(vendorDir, { recursive: true });
const tarPath = join(vendorDir, 'libghostty-vt-android.tar.gz');
await writeFile(tarPath, tarball);
execFileSync('tar', ['-xzf', tarPath, '-C', vendorDir]);
await rm(tarPath);
await writeFile(stampFile, `${sha256}\n`);
console.log('[expo-libghostty] android libghostty-vt ready');
