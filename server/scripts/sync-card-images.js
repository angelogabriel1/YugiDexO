import { ListObjectsV2Command, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { config } from '../src/config.js';

const required = ['R2_ENDPOINT', 'R2_BUCKET', 'R2_ACCESS_KEY_ID', 'R2_SECRET_ACCESS_KEY'];
const missing = required.filter(key => !config[key]);
if (missing.length) throw new Error(`Configure no .env: ${missing.join(', ')}`);

const limitArgument = process.argv.find(argument => argument.startsWith('--limit='));
const limit = limitArgument ? Number(limitArgument.split('=')[1]) : Infinity;
if (!(limit > 0)) throw new Error('--limit precisa ser um numero positivo');
const concurrencyArgument = process.argv.find(argument => argument.startsWith('--concurrency='));
const concurrency = concurrencyArgument ? Number(concurrencyArgument.split('=')[1]) : 5;
if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 25) {
  throw new Error('--concurrency precisa ser um inteiro entre 1 e 25');
}

const r2 = new S3Client({
  region: 'auto',
  endpoint: config.R2_ENDPOINT,
  credentials: {
    accessKeyId: config.R2_ACCESS_KEY_ID,
    secretAccessKey: config.R2_SECRET_ACCESS_KEY
  }
});

async function existingKeys() {
  const keys = new Set();
  let continuationToken;
  do {
    const page = await r2.send(new ListObjectsV2Command({
      Bucket: config.R2_BUCKET,
      ContinuationToken: continuationToken
    }));
    for (const object of page.Contents ?? []) if (object.Key) keys.add(object.Key);
    continuationToken = page.IsTruncated ? page.NextContinuationToken : undefined;
  } while (continuationToken);
  return keys;
}

async function download(url) {
  const response = await fetch(url, {
    headers: { 'User-Agent': 'Yugidex-R2-Mirror/1.0' },
    signal: AbortSignal.timeout(30_000)
  });
  if (!response.ok) throw new Error(`Download ${response.status}: ${url}`);
  return new Uint8Array(await response.arrayBuffer());
}

async function upload(key, url) {
  const body = await download(url);
  await r2.send(new PutObjectCommand({
    Bucket: config.R2_BUCKET,
    Key: key,
    Body: body,
    ContentType: 'image/jpeg',
    CacheControl: 'public, max-age=31536000, immutable'
  }));
}

const catalogResponse = await fetch('https://db.ygoprodeck.com/api/v7/cardinfo.php?misc=yes', {
  headers: { 'User-Agent': 'Yugidex-R2-Mirror/1.0' },
  signal: AbortSignal.timeout(60_000)
});
if (!catalogResponse.ok) throw new Error(`Falha ao obter catalogo: HTTP ${catalogResponse.status}`);
const catalog = await catalogResponse.json();
const jobs = [];
for (const card of catalog.data ?? []) {
  for (const image of card.card_images ?? []) {
    const imageId = image.id ?? card.id;
    if (image.image_url) jobs.push({ key: `cards/${imageId}.jpg`, url: image.image_url });
    if (image.image_url_small) jobs.push({ key: `cards-small/${imageId}.jpg`, url: image.image_url_small });
  }
}

const existing = await existingKeys();
const uniqueJobs = [...new Map(jobs.map(job => [job.key, job])).values()];
const pending = uniqueJobs.filter(job => !existing.has(job.key)).slice(0, limit);
let completed = 0;
let failed = 0;
let cursor = 0;

async function worker() {
  while (cursor < pending.length) {
    const job = pending[cursor++];
    try {
      await upload(job.key, job.url);
      completed += 1;
    } catch (error) {
      failed += 1;
      console.error('Falha', { key: job.key, message: error.message });
    }
    if ((completed + failed) % 100 === 0 || completed + failed === pending.length) {
      console.log('Progresso R2', { processed: completed + failed, total: pending.length, completed, failed });
    }
  }
}

console.log('Sincronizacao R2 iniciada', {
  catalogObjects: uniqueJobs.length,
  duplicateCatalogKeys: jobs.length - uniqueJobs.length,
  existing: existing.size,
  pending: pending.length
});
await Promise.all(Array.from({ length: Math.min(concurrency, pending.length || 1) }, worker));
if (failed) process.exitCode = 1;
