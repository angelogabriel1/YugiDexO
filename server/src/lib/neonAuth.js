import { config } from '../config.js';

export class AuthError extends Error {
  constructor(message, status = 400) {
    super(message);
    this.status = status;
  }
}

export async function neonAuthRequest(path, { body, token } = {}) {
  const response = await fetch(`${config.NEON_AUTH_URL}${path}`, {
    method: body ? 'POST' : 'GET',
    headers: {
      Accept: 'application/json',
      Origin: config.PUBLIC_ORIGIN,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(config.REQUEST_TIMEOUT_MS)
  });

  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = { message: text }; }
  if (!response.ok) {
    throw new AuthError(data?.message || data?.error?.message || data?.error || 'Falha na autenticacao', response.status);
  }
  return {
    data,
    token: response.headers.get('set-auth-token') || data?.token || data?.session?.token || null
  };
}

export async function getNeonSession(token) {
  const { data } = await neonAuthRequest('/get-session', { token });
  return data;
}
