export function inventoryValue(cards) {
  return cards.reduce((total, card) => {
    const rawValue = card.estimated_unit_value ?? card.estimatedUnitValue;
    const value = Number(rawValue);
    const quantity = Number(card.quantity ?? 1);
    return rawValue != null && Number.isFinite(value) && value >= 0 && Number.isFinite(quantity) && quantity > 0
      ? total + value * quantity
      : total;
  }, 0);
}

export function pricedQuantity(cards) {
  return cards.reduce((total, card) => {
    const rawValue = card.estimated_unit_value ?? card.estimatedUnitValue;
    const value = Number(rawValue);
    const quantity = Number(card.quantity ?? 1);
    return rawValue != null && Number.isFinite(value) && value >= 0 && Number.isFinite(quantity) && quantity > 0
      ? total + quantity
      : total;
  }, 0);
}
