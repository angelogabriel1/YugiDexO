import { config } from '../config.js';

function list(value) {
  return String(value || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean);
}

function normalize(value) {
  return String(value || '').trim().toLowerCase();
}

export function isAdminUser(user, source = config) {
  const emails = list(source.ADMIN_EMAILS).map(normalize);
  const ids = list(source.ADMIN_USER_IDS);
  const usernames = list(source.ADMIN_USERNAMES).map(normalize);
  if (!emails.length && !ids.length && !usernames.length) return false;

  return ids.includes(user.id)
    || emails.includes(normalize(user.email))
    || usernames.includes(normalize(user.name));
}

export function requireAdmin(req, res, next) {
  if (!isAdminUser(req.auth?.user)) {
    return res.status(403).json({
      error: 'Acesso restrito ao administrador. Configure ADMIN_EMAILS, ADMIN_USER_IDS ou ADMIN_USERNAMES no servidor.'
    });
  }
  next();
}
