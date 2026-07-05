import { admin } from '../src/supabase.js';

async function exactCount(table) {
  const { count, error } = await admin.from(table).select('*', { count: 'exact', head: true });
  if (error) throw error;
  return count ?? 0;
}

const { data, error } = await admin.auth.admin.listUsers({ page: 1, perPage: 1 });
if (error) throw error;

console.log('Supabase legado', {
  authUsers: data?.total ?? data?.users?.length ?? 0,
  profiles: await exactCount('profiles'),
  cards: await exactCount('cards')
});
