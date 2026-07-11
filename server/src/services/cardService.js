import * as cheerio from 'cheerio';
import { config } from '../config.js';
import { fetchHtml, fetchJson } from '../lib/http.js';
import { withHostedImages } from '../lib/cardImages.js';
import { fallbackPrices } from '../lib/cardPrices.js';

const YGO = 'https://db.ygoprodeck.com/api/v7';

function money(text) {
  if (!text) return null;
  const match = text.match(/R\$\s*([\d.]+(?:,\d{2})?)/i);
  return match ? Number(match[1].replace(/\./g, '').replace(',', '.')) : null;
}

function structuredProducts($) {
  const items = [];
  $('script[type="application/ld+json"]').each((_, script) => {
    try {
      const value = JSON.parse($(script).text());
      const nodes = Array.isArray(value) ? value : [value];
      for (const node of nodes) {
        if (node?.['@type'] !== 'Product') continue;
        const offers = Array.isArray(node.offers) ? node.offers : [node.offers].filter(Boolean);
        for (const offer of offers) {
          const price = Number(offer?.price ?? offer?.lowPrice);
          if (Number.isFinite(price)) items.push({
            edition: node.sku || node.name || 'Edicao nacional',
            price,
            url: offer.url || node.url || null
          });
        }
      }
    } catch { /* ignora JSON-LD invalido */ }
  });
  return items;
}

export async function scrapeMyPCards(name) {
  const searchUrl = new URL('/busca', config.MYPCARDS_BASE_URL);
  searchUrl.searchParams.set('q', name);
  const html = await fetchHtml(searchUrl);
  const $ = cheerio.load(html);
  const editions = structuredProducts($);

  $('[data-price], .product-card, .produto, .search-result').each((_, element) => {
    const node = $(element);
    const price = Number(node.attr('data-price')) || money(node.text());
    if (!price) return;
    editions.push({
      edition: node.find('[data-edition], .edition, .product-name, h2, h3').first().text().trim() || 'Edicao nacional',
      price,
      url: node.find('a').first().attr('href') || null
    });
  });

  const unique = [...new Map(editions.map(item => [`${item.edition}-${item.price}`, item])).values()]
    .filter(item => item.price > 0)
    .sort((a, b) => a.price - b.price);
  if (!unique.length) throw new Error('Sem ofertas nacionais');
  return { source: 'mypcards', currency: 'BRL', min: unique[0].price, max: unique.at(-1).price, editions: unique };
}

export async function getCardDetails({ id, name }) {
  const query = new URLSearchParams(id ? { id: String(id) } : { name });
  const [english, portuguese] = await Promise.allSettled([
    fetchJson(`${YGO}/cardinfo.php?${query}`),
    fetchJson(`${YGO}/cardinfo.php?language=pt&${query}`)
  ]);
  if (english.status === 'rejected') throw Object.assign(new Error('Carta nao encontrada'), { status: 404 });
  const card = english.value.data?.[0];
  if (!card) throw Object.assign(new Error('Carta nao encontrada'), { status: 404 });
  const translated = portuguese.status === 'fulfilled' ? portuguese.value.data?.[0] : null;
  let prices;
  try { prices = await scrapeMyPCards(translated?.name || card.name); }
  catch { prices = fallbackPrices(card, config.BRL_USD_RATE); }
  return withHostedImages({
    ...card,
    localized: translated ? { name: translated.name, description: translated.desc } : null,
    prices
  });
}
