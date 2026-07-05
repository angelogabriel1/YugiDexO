import { pool } from '../db.js';

export async function requireAuth(req, res, next) {
  const match = req.get('authorization')?.match(/^Bearer\s+(.+)$/i);
  if (!match) return res.status(401).json({ error: 'Token ausente' });

  try {
    const result = await pool.query(`
      select
        u.id, u.name, u.email, u."emailVerified" as email_verified,
        s.id as session_id, s."expiresAt" as expires_at
      from neon_auth.session s
      join neon_auth."user" u on u.id = s."userId"
      where s.token = $1
        and s."expiresAt" > now()
        and coalesce(u.banned, false) = false
      limit 1
    `, [match[1]]);
    const authenticated = result.rows[0];
    if (!authenticated) return res.status(401).json({ error: 'Token invalido ou expirado' });
    req.auth = {
      token: match[1],
      user: {
        id: authenticated.id,
        name: authenticated.name,
        email: authenticated.email,
        emailVerified: authenticated.email_verified
      },
      session: { id: authenticated.session_id, expiresAt: authenticated.expires_at }
    };
    next();
  } catch (error) {
    next(error);
  }
}
