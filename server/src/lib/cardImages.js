import { config } from '../config.js';

const YGO_IMAGES = 'https://images.ygoprodeck.com/images';

export function cardImageUrl(cardId, fallback = null) {
  return config.R2_PUBLIC_URL
    ? `${config.R2_PUBLIC_URL}/cards/${cardId}.jpg`
    : (fallback || `${YGO_IMAGES}/cards/${cardId}.jpg`);
}

export function cardThumbnailUrl(cardId, fallback = null) {
  return config.R2_PUBLIC_URL
    ? `${config.R2_PUBLIC_URL}/cards-small/${cardId}.jpg`
    : (fallback || `${YGO_IMAGES}/cards_small/${cardId}.jpg`);
}

export function withHostedImages(card) {
  if (!config.R2_PUBLIC_URL || !card?.id) return card;
  const images = card.card_images?.length ? card.card_images : [{ id: card.id }];
  return {
    ...card,
    card_images: images.map(image => ({
      ...image,
      image_url: cardImageUrl(card.id, image.image_url),
      image_url_small: cardThumbnailUrl(card.id, image.image_url_small)
    }))
  };
}
