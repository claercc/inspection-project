// HTTPS certificate validation remains enabled. Use Node TLS on Windows to avoid
// Schannel failures when the OS certificate-revocation server cannot be reached.
import { createHash } from 'node:crypto';
import { createWriteStream, createReadStream, mkdirSync, existsSync } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dir = path.join(root, '.tools');
mkdirSync(dir, { recursive: true });
async function download(url, name) {
  const response = await fetch(url, { signal: AbortSignal.timeout(240000) });
  if (!response.ok) throw new Error(`Download ${response.status}: ${url}`);
  await pipeline(Readable.fromWeb(response.body), createWriteStream(path.join(dir, name)));
  console.log(`Downloaded ${name}`);
}
async function verify(name, algorithm, expected) {
  const hash = createHash(algorithm);
  for await (const chunk of createReadStream(path.join(dir, name))) hash.update(chunk);
  if (hash.digest('hex') !== expected.toLowerCase()) throw new Error(`${name} checksum mismatch`);
  console.log(`Verified ${name} ${algorithm}`);
}
const mavenUrl =
  'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip';
const mavenHash = await fetch(mavenUrl + '.sha512');
if (!mavenHash.ok) throw new Error('Maven checksum unavailable');
if (!existsSync(path.join(dir, 'maven.zip'))) await download(mavenUrl, 'maven.zip');
await verify('maven.zip', 'sha512', (await mavenHash.text()).trim().split(/\s+/)[0]);
const jdkUrl = 'https://aka.ms/download-jdk/microsoft-jdk-17.0.20.1-windows-x64.zip';
const jdkHash = await fetch(jdkUrl + '.sha256sum.txt');
if (!jdkHash.ok) throw new Error('JDK checksum unavailable');
const expected = (await jdkHash.text()).trim().split(/\s+/)[0];
if (!/^[a-f0-9]{64}$/i.test(expected)) throw new Error('Invalid JDK checksum format');
if (!existsSync(path.join(dir, 'microsoft-jdk17.zip')))
  await download(jdkUrl, 'microsoft-jdk17.zip');
await verify('microsoft-jdk17.zip', 'sha256', expected);
