import test from 'node:test';
import assert from 'node:assert/strict';
import { buildAffiliateLink } from '../src/lib/affiliate.js';

test('gera link afiliado por template de carta', () => {
  const link = buildAffiliateLink(
    { id: 46986414, name: 'Dark Magician' },
    { template: 'https://loja.example/busca?q={encodedName}&ref=yugidex&id={cardId}' }
  );

  assert.equal(link.url, 'https://loja.example/busca?q=Dark%20Magician&ref=yugidex&id=46986414');
  assert.equal(link.label, 'Ver oferta da carta');
  assert.equal(link.provider, null);
  assert.match(link.disclosure, /Link de afiliado/);
});

test('usa link afiliado especifico da carta antes do template', () => {
  const link = buildAffiliateLink(
    { id: 46986414, name: 'Dark Magician' },
    {
      links: JSON.stringify({ 46986414: 'https://meli.la/2Z62nSs' }),
      template: 'https://loja.example/busca?q={encodedName}'
    }
  );

  assert.equal(link.url, 'https://meli.la/2Z62nSs');
});

test('preserva provedor configurado no retorno do afiliado', () => {
  const link = buildAffiliateLink(
    { id: 46986414, name: 'Dark Magician' },
    { links: { 46986414: 'https://meli.la/2Z62nSs' }, provider: 'Mercado Livre' }
  );

  assert.equal(link.provider, 'Mercado Livre');
});

test('aceita mapa de afiliados por nome normalizado da carta', () => {
  const link = buildAffiliateLink(
    { id: 123, name: 'Dark Magician' },
    { links: { 'dark magician': 'https://meli.la/2Z62nSs' } }
  );

  assert.equal(link.url, 'https://meli.la/2Z62nSs');
});

test('omite afiliado quando template esta ausente ou invalido', () => {
  assert.equal(buildAffiliateLink({ id: 1, name: 'Blue-Eyes White Dragon' }), null);
  assert.equal(buildAffiliateLink({ id: 1, name: 'Blue-Eyes White Dragon' }, { template: 'nao-e-url/{cardId}' }), null);
});
