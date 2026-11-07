/**
 * Normalization utilities for Trade Finance data types.
 */

export function normalizeDate(str) {
  if (!str) return null;
  const clean = String(str).trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(clean)) return clean;
  // YYMMDD SWIFT date
  if (/^\d{6}$/.test(clean)) {
    const yy = clean.substring(0, 2);
    const mm = clean.substring(2, 4);
    const dd = clean.substring(4, 6);
    return `20${yy}-${mm}-${dd}`;
  }
  const parsed = new Date(clean);
  if (!isNaN(parsed.getTime())) {
    return parsed.toISOString().split('T')[0];
  }
  return clean;
}

export function normalizeNumber(str) {
  if (typeof str === 'number') return str;
  const cleaned = String(str || '').replace(/[^0-9.]/g, '');
  const num = parseFloat(cleaned);
  return isNaN(num) ? str : num;
}

export function normalizeCurrency(str) {
  const match = String(str || '').toUpperCase().match(/\b(USD|EUR|GBP|JPY|SGD|CHF|AED|CNY)\b/);
  return match ? match[1] : String(str).trim().toUpperCase();
}

export function normalizePartialShipment(str) {
  const lower = String(str || '').toLowerCase();
  if (lower.includes('allowed') || lower.includes('permitted') || lower.includes('yes') || lower.includes('true')) {
    return 'ALLOWED';
  }
  if (lower.includes('not') || lower.includes('prohibited') || lower.includes('no') || lower.includes('false')) {
    return 'NOT_ALLOWED';
  }
  return 'ALLOWED';
}

export function normalizeByType(dataType, targetField, rawValue) {
  if (rawValue === null || rawValue === undefined) return null;
  if (dataType === 'date') return normalizeDate(rawValue);
  if (dataType === 'number') return normalizeNumber(rawValue);
  if (dataType === 'currency') return normalizeCurrency(rawValue);
  if (dataType === 'enum' && targetField === 'partial_shipment') return normalizePartialShipment(rawValue);
  return typeof rawValue === 'string' ? rawValue.trim() : rawValue;
}
