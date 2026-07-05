import 'dotenv/config';
import { z } from 'zod';

const schema = z.object({
  PORT: z.coerce.number().int().positive().default(3000),
  HOST: z.string().default('0.0.0.0'),
  PUBLIC_ORIGIN: z.string().url().default('http://localhost:3000'),
  DATABASE_URL: z.string().min(20).refine(value => /^postgres(?:ql)?:\/\//i.test(value), 'DATABASE_URL invalida'),
  NEON_AUTH_URL: z.string().url().transform(value => value.replace(/\/$/, '')),
  R2_PUBLIC_URL: z.string().url().transform(value => value.replace(/\/$/, '')).optional(),
  R2_ENDPOINT: z.string().url().transform(value => value.replace(/\/$/, '')).optional(),
  R2_BUCKET: z.string().min(3).optional(),
  R2_ACCESS_KEY_ID: z.string().min(10).optional(),
  R2_SECRET_ACCESS_KEY: z.string().min(20).optional(),
  SUPABASE_URL: z.preprocess(value => {
    if (typeof value !== 'string') return value;
    const url = value.trim().replace(/^hhttps:\/\//i, 'https://').replace(/\/$/, '');
    return /^[a-z0-9-]+\.supabase\.co$/i.test(url) ? `https://${url}` : url;
  }, z.string().url().optional()),
  SUPABASE_ANON_KEY: z.string().min(20).optional(),
  SUPABASE_SERVICE_ROLE_KEY: z.string().min(20).optional(),
  BRL_USD_RATE: z.coerce.number().positive().default(5.5),
  MYPCARDS_BASE_URL: z.string().url().default('https://mypcards.com'),
  REQUEST_TIMEOUT_MS: z.coerce.number().int().positive().default(8000)
});

export const config = schema.parse(process.env);
