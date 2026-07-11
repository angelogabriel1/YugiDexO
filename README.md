# Yugidex

Ecossistema para escanear, organizar e compartilhar colecoes de Yu-Gi-Oh!:

- `android/`: app Android nativo com Kotlin, Compose, CameraX, ML Kit e Room.
- `server/`: API Node/Express e o portal web.
- `neon/`: schema PostgreSQL usado no Neon.
- Cloudflare R2: armazenamento opcional das artes das cartas.
- Railway: hospedagem do backend e do portal.

O app tambem permite criar decks personalizados, combinar cartas que ja estao no
inventario com cartas marcadas como faltantes e sincronizar esses decks na conta.
Os decks tambem aparecem no perfil publico compartilhado, com os dois estados
visualmente diferenciados, em uma aba propria "Meus Decks" no portal web e no app.
Quando uma carta faltante possui link afiliado cadastrado, o perfil publico exibe
um botao de compra diretamente dentro do deck; no Android, decks sincronizados
tambem preservam esse link para consulta na edicao do deck.

O inventario mostra um valor aproximado em reais, calculado pela menor cotacao
disponivel de cada carta multiplicada pela quantidade. Quando alguma carta ainda
nao possui cotacao, web e app exibem a cobertura usada no total. No Android, o
perfil publico pode ser compartilhado pela folha nativa do sistema.

## Desenvolvimento local

1. Copie `server/.env.example` para `server/.env` e preencha `DATABASE_URL` e `NEON_AUTH_URL`.
2. Execute `npm install` na raiz.
3. Execute `npm --workspace server run db:setup` uma vez.
4. Inicie com `npm run dev`.
5. Abra `http://localhost:3000/colecao/<username>`.

No Android, configure `API_BASE_URL` em `android/gradle.properties`. Use o IP local
do computador para um aparelho fisico e `http://10.0.2.2:3000/` apenas no emulador.

## Migracao legada do Supabase

As variaveis `SUPABASE_URL`, `SUPABASE_ANON_KEY` e
`SUPABASE_SERVICE_ROLE_KEY` sao opcionais e servem somente para a migracao:

```text
npm --workspace server run legacy:status
npm --workspace server run legacy:migrate
```

Senhas nao sao copiadas. O inventario fica associado ao e-mail na tabela de
transicao e e restaurado automaticamente quando o dono cria sua conta no Neon Auth.

## Cloudflare R2

Crie um bucket, uma chave S3 de leitura/escrita e preencha:

```text
R2_ENDPOINT=https://SEU_ACCOUNT_ID.r2.cloudflarestorage.com
R2_BUCKET=yugidex-cards
R2_ACCESS_KEY_ID=...
R2_SECRET_ACCESS_KEY=...
R2_PUBLIC_URL=https://cartas.seudominio.com
```

Para um teste pequeno, envie dez objetos:

```text
npm --workspace server run r2:sync -- --limit=10
```

Depois, execute sem `--limit` para espelhar todas as artes. O processo e
retomavel: objetos que ja existem no bucket sao ignorados. Sao enviados os arquivos
grandes em `cards/` e as miniaturas em `cards-small/`.

Em producao, conecte um dominio proprio ao bucket. O `r2.dev` e adequado apenas
para desenvolvimento e pode sofrer limitacao de trafego.

## Railway

O projeto inclui `Dockerfile` e `railway.toml`. No servico do Railway, configure:

```text
DATABASE_URL
NEON_AUTH_URL
PUBLIC_ORIGIN=https://SEU-APP.up.railway.app
R2_PUBLIC_URL=https://cartas.seudominio.com
ADMIN_EMAILS=voce@example.com
BRL_USD_RATE=5.50
AFFILIATE_CARD_LINKS_JSON={"46986414":"https://meli.la/2Z62nSs"}
AFFILIATE_CARD_URL_TEMPLATE=https://sua-loja.example/busca?q={encodedName}&ref=SEU_CODIGO
AFFILIATE_LINK_LABEL=Ver oferta da carta
```

As chaves de escrita do R2 nao sao necessarias no servidor em execucao; use-as
somente ao rodar `r2:sync`. O Railway fornece `PORT` automaticamente e verifica
`/api/health` antes de ativar uma nova versao.

O painel administrativo fica em `/admin` e usa o login normal do app. Para liberar
o acesso, configure pelo menos uma das variaveis `ADMIN_EMAILS`,
`ADMIN_USER_IDS` ou `ADMIN_USERNAMES` com valores separados por virgula.

Os links de afiliado sao opcionais. Para Mercado Livre, use preferencialmente o
painel `/admin` para cadastrar cada carta com o link curto gerado no Portal do
Afiliado. `AFFILIATE_CARD_LINKS_JSON` continua disponivel como fallback por
ambiente, por `cardId` ou nome da carta, por exemplo
`{"46986414":"https://meli.la/2Z62nSs"}`. Quando nao houver link especifico no
banco ou no JSON, o app pode cair no `AFFILIATE_CARD_URL_TEMPLATE`, se
configurado. Placeholders aceitos no template: `{cardId}`, `{id}`, `{card_id}`,
`{name}`, `{encodedName}` e `{query}`. Voce tambem pode ajustar o aviso exibido
com `AFFILIATE_DISCLOSURE`.

Depois de gerar o dominio do Railway, adicione essa URL como origem confiavel nas
configuracoes do Neon Auth. Em seguida, gere o APK de producao usando a mesma URL
HTTPS como `API_BASE_URL`.

## Rotas principais

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/cards` (Bearer token)
- `POST /api/cards/sync` (Bearer token)
- `GET /api/decks` (Bearer token)
- `POST /api/decks` (Bearer token)
- `PUT /api/decks/:id` (Bearer token)
- `DELETE /api/decks/:id` (Bearer token)
- `POST /api/decks/sync` (Bearer token)
- `GET /api/admin/me` (Bearer token de admin)
- `GET /api/admin/affiliate-links` (Bearer token de admin)
- `POST /api/admin/affiliate-links` (Bearer token de admin)
- `PUT /api/admin/affiliate-links/:id` (Bearer token de admin)
- `DELETE /api/admin/affiliate-links/:id` (Bearer token de admin)
- `GET /api/collections/:username`
- `GET /api/card-details?id=<id>&name=<name>`
- `GET /api/colecao-stream/:username`
- `GET /colecao/:username`

## Verificacoes

```text
npm test
npm --workspace server run db:status
npm --workspace server run auth:smoke
```

Antes de uma migracao em producao, gere um backup logico com
`npm --workspace server run db:backup`. Depois de adicionar o campo de valor a um
banco existente, `npm --workspace server run db:backfill-values` preenche as
cotacoes que estiverem faltando. Para substituir cotacoes antigas depois de uma
mudanca na regra de precos, execute `npm --workspace server run db:backfill-values -- --refresh`.

O `auth:smoke` cria uma conta descartavel, testa autenticacao, sincronizacao,
download do inventario, portal publico e logout, e remove a conta ao final.
