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
/**
 * 下载一个工具压缩包到项目的 .tools 目录，由本脚本的工具准备流程调用。
 * @param {string} url 完整下载地址，保留 HTTPS 证书校验。
 * @param {string} name 保存到 .tools 下的文件名，例如 maven.zip。
 * @returns {Promise<void>} 响应体全部写入本地文件后兑现，不负责解压或校验哈希。
 * @throws {Error} 下载超时、HTTP 状态异常或文件写入失败时，Promise 拒绝。
 * @example
 * await download(mavenUrl, 'maven.zip');
 */
async function download(url, name) {
  const response = await fetch(url, { signal: AbortSignal.timeout(240000) });
  if (!response.ok) throw new Error(`Download ${response.status}: ${url}`);
  await pipeline(Readable.fromWeb(response.body), createWriteStream(path.join(dir, name)));
  console.log(`Downloaded ${name}`);
}
/**
 * 流式计算已下载文件的哈希，与官方校验值比较。
 * @param {string} name .tools 目录中的待校验文件名。
 * @param {'sha256'|'sha512'} algorithm 哈希算法，与官方公布的校验文件一致。
 * @param {string} expected 官方十六进制校验值，比较时忽略字母大小写。
 * @returns {Promise<void>} 文件读取完成且哈希匹配时兑现。
 * @throws {Error} 文件读取失败或校验值不匹配时，Promise 拒绝。
 * @example
 * await verify('maven.zip', 'sha512', expectedChecksum);
 */
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
