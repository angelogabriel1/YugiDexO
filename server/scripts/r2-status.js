import { ListObjectsV2Command, S3Client } from '@aws-sdk/client-s3';
import { config } from '../src/config.js';

const required = ['R2_ENDPOINT', 'R2_BUCKET', 'R2_ACCESS_KEY_ID', 'R2_SECRET_ACCESS_KEY', 'R2_PUBLIC_URL'];
const missing = required.filter(key => !config[key]);
if (missing.length) throw new Error(`Configure no .env: ${missing.join(', ')}`);

const r2 = new S3Client({
  region: 'auto',
  endpoint: config.R2_ENDPOINT,
  credentials: {
    accessKeyId: config.R2_ACCESS_KEY_ID,
    secretAccessKey: config.R2_SECRET_ACCESS_KEY
  }
});

let objects = 0;
let firstKey;
let continuationToken;
do {
  const page = await r2.send(new ListObjectsV2Command({
    Bucket: config.R2_BUCKET,
    ContinuationToken: continuationToken
  }));
  objects += page.KeyCount ?? page.Contents?.length ?? 0;
  firstKey ??= page.Contents?.[0]?.Key;
  continuationToken = page.IsTruncated ? page.NextContinuationToken : undefined;
} while (continuationToken);

if (!firstKey) throw new Error('O bucket esta vazio');
const publicResponse = await fetch(`${config.R2_PUBLIC_URL}/${firstKey}`, {
  method: 'HEAD',
  signal: AbortSignal.timeout(15_000)
});

console.log('R2 status', {
  objects,
  publicAccess: publicResponse.ok,
  publicStatus: publicResponse.status,
  contentType: publicResponse.headers.get('content-type')
});
if (!publicResponse.ok) process.exitCode = 1;
