import { admin } from '../src/supabase.js';
import { pool, transaction } from '../src/db.js';

async function allAuthUsers() {
  const users = [];
  for (let page = 1; ; page += 1) {
    const { data, error } = await admin.auth.admin.listUsers({ page, perPage: 1000 });
    if (error) throw error;
    users.push(...(data?.users ?? []));
    if ((data?.users?.length ?? 0) < 1000) return users;
  }
}

async function allRows(table) {
  const { data, error } = await admin.from(table).select('*');
  if (error) throw error;
  return data ?? [];
}

const [users, profiles, cards] = await Promise.all([
  allAuthUsers(),
  allRows('profiles'),
  allRows('cards')
]);

const profilesById = new Map(profiles.map(profile => [profile.id, profile]));
const cardsByUser = new Map();
for (const card of cards) {
  const list = cardsByUser.get(card.user_id) ?? [];
  list.push({
    card_id: card.card_id,
    name: card.name,
    image_url: card.image_url,
    type: card.type,
    attribute: card.attribute,
    rarity: card.rarity,
    collection_name: card.collection_name,
    quantity: card.quantity,
    saved_at: card.saved_at
  });
  cardsByUser.set(card.user_id, list);
}

let stagedAccounts = 0;
let stagedCards = 0;
await transaction(async client => {
  for (const user of users) {
    const email = user.email?.trim().toLowerCase();
    const profile = profilesById.get(user.id);
    if (!email || !profile?.username) continue;
    const inventory = cardsByUser.get(user.id) ?? [];
    await client.query(`
      insert into legacy_accounts(email, legacy_user_id, username, cards)
      values ($1, $2, $3, $4::jsonb)
      on conflict (email) do update set
        legacy_user_id = excluded.legacy_user_id,
        username = excluded.username,
        cards = excluded.cards
      where legacy_accounts.imported_at is null
    `, [email, user.id, profile.username, JSON.stringify(inventory)]);
    stagedAccounts += 1;
    stagedCards += inventory.length;
  }
});

console.log('Migracao preparada', { stagedAccounts, stagedCards });
await pool.end();
