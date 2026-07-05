import { config } from '../config.js';

export async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    signal: AbortSignal.timeout(config.REQUEST_TIMEOUT_MS),
    headers: { Accept: 'application/json', 'User-Agent': 'Yugidex/1.0', ...options.headers }
  });
  if (!response.ok) throw new Error(`Upstream HTTP ${response.status}`);
  return response.json();
}

export async function fetchHtml(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    signal: AbortSignal.timeout(config.REQUEST_TIMEOUT_MS),
    headers: {
      Accept: 'text/html,application/xhtml+xml',
      'Accept-Language': 'pt-BR,pt;q=0.9',
      'User-Agent': 'Mozilla/5.0 (compatible; Yugidex/1.0; +https://github.com/yugidex)',
      ...options.headers
    }
  });
  if (!response.ok) throw new Error(`Upstream HTTP ${response.status}`);
  return response.text();
}
