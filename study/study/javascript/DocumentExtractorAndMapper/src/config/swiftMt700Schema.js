/**
 * SWIFT MT700 Letter of Credit Issuance Canonical Target Schema
 * Represents the target contract required by downstream banking APIs (e.g., post_issuance_call).
 */

export const SWIFT_MT700_TARGET_SCHEMA = {
  loc_number: {
    swiftTag: '20',
    description: 'Documentary Credit Number - The unique identifier of the Letter of Credit issued by the bank',
    dataType: 'string',
    required: true,
    aliases: [
      'documentary_credit_number',
      'doc_credit_no',
      'lc_number',
      'loc_no',
      'credit_reference_number',
      'our_ref'
    ]
  },
  issue_date: {
    swiftTag: '31C',
    description: 'Date of Issue - The calendar date on which the Letter of Credit was formally issued (YYYY-MM-DD)',
    dataType: 'date',
    required: true,
    aliases: [
      'date_of_issue',
      'issuance_date',
      'date_issued',
      'credit_opening_date'
    ]
  },
  expiry_date: {
    swiftTag: '31D',
    description: 'Date of Expiry - The final validity date by which presentation must take place (YYYY-MM-DD)',
    dataType: 'date',
    required: true,
    aliases: [
      'date_of_expiry',
      'expiration_date',
      'valid_until',
      'expiry'
    ]
  },
  expiry_place: {
    swiftTag: '31D',
    description: 'Place of Expiry - The city, country, or specific bank counter where the credit expires',
    dataType: 'string',
    required: true,
    aliases: [
      'place_of_expiry',
      'expiry_location',
      'expiring_at',
      'counter_of_expiry'
    ]
  },
  applicant_name: {
    swiftTag: '50',
    description: 'Applicant Name - The buyer or party on whose behalf the Letter of Credit is issued',
    dataType: 'string',
    required: true,
    aliases: [
      'applicant',
      'buyer_name',
      'importer',
      'ordering_customer',
      'accountee'
    ]
  },
  applicant_address: {
    swiftTag: '50',
    description: 'Applicant Address - Complete registered address of the applicant',
    dataType: 'string',
    required: false,
    aliases: [
      'applicant_addr',
      'buyer_address',
      'applicant_location'
    ]
  },
  beneficiary_name: {
    swiftTag: '59',
    description: 'Beneficiary Name - The seller or party to whom payment is to be made upon complying presentation',
    dataType: 'string',
    required: true,
    aliases: [
      'beneficiary',
      'seller_name',
      'exporter',
      'favouring_party',
      'in_favour_of'
    ]
  },
  currency: {
    swiftTag: '32B',
    description: 'Currency Code - 3-letter ISO-4217 currency code (e.g., USD, EUR, GBP, JPY)',
    dataType: 'currency',
    required: true,
    aliases: [
      'ccy',
      'currency_code',
      'denominated_currency'
    ]
  },
  amount: {
    swiftTag: '32B',
    description: 'Credit Amount - The total maximum monetary amount authorized under this Letter of Credit',
    dataType: 'number',
    required: true,
    aliases: [
      'credit_amount',
      'lc_amount',
      'sum_of',
      'total_value',
      'value_amount'
    ]
  },
  partial_shipment: {
    swiftTag: '43P',
    description: 'Partial Shipments - Specifies whether partial shipments of goods are permitted (ALLOWED or NOT_ALLOWED)',
    dataType: 'enum',
    allowedValues: ['ALLOWED', 'NOT_ALLOWED'],
    required: true,
    aliases: [
      'partial_shipments',
      'part_shipment',
      'partial_drawings'
    ]
  },
  latest_shipment_date: {
    swiftTag: '44C',
    description: 'Latest Date of Shipment - The latest permissible date of loading goods on board / dispatch (YYYY-MM-DD)',
    dataType: 'date',
    required: false,
    aliases: [
      'latest_date_of_shipment',
      'shipment_deadline',
      'must_ship_by',
      'last_shipment_date'
    ]
  },
  tolerance_percentage: {
    swiftTag: '39A',
    description: 'Percentage Credit Amount Tolerance - Allowable positive or negative percentage variance (0 to 15%)',
    dataType: 'number',
    required: false,
    aliases: [
      'tolerance',
      'amount_tolerance',
      'plus_minus_tolerance',
      'variance_percentage'
    ]
  }
};

/**
 * Validates mapped data against data types and conditional trade finance business invariants.
 * @param {Object} mappedData Key-value map of target fields
 * @returns {{ isValid: boolean, errors: string[], warnings: string[] }}
 */
export function validateSwiftMt700Invariants(mappedData) {
  const errors = [];
  const warnings = [];

  // 1. Required Field Checks
  for (const [fieldName, fieldDef] of Object.entries(SWIFT_MT700_TARGET_SCHEMA)) {
    const fieldEntry = mappedData[fieldName];
    const val = fieldEntry ? fieldEntry.value : null;

    if (fieldDef.required && (val === null || val === undefined || val === '')) {
      errors.push(`Missing required field: '${fieldName}' (SWIFT Tag ${fieldDef.swiftTag})`);
      continue;
    }

    if (val !== null && val !== undefined && val !== '') {
      // 2. Data Type Validations
      if (fieldDef.dataType === 'number') {
        const num = Number(val);
        if (isNaN(num)) {
          errors.push(`Field '${fieldName}' must be a valid number, got '${val}'`);
        } else if (fieldName === 'amount' && num <= 0) {
          errors.push(`Credit amount must be strictly greater than 0, got ${num}`);
        } else if (fieldName === 'tolerance_percentage' && (num < 0 || num > 15)) {
          errors.push(`Tolerance percentage must be between 0 and 15, got ${num}`);
        }
      } else if (fieldDef.dataType === 'currency') {
        const cleanCcy = String(val).trim().toUpperCase();
        if (!/^[A-Z]{3}$/.test(cleanCcy)) {
          errors.push(`Currency '${val}' is not a valid 3-letter ISO-4217 code`);
        }
      } else if (fieldDef.dataType === 'date') {
        const dateRegex = /^\d{4}-\d{2}-\d{2}$/;
        if (!dateRegex.test(String(val))) {
          errors.push(`Field '${fieldName}' must follow ISO date format YYYY-MM-DD, got '${val}'`);
        }
      } else if (fieldDef.dataType === 'enum') {
        if (!fieldDef.allowedValues.includes(String(val).toUpperCase())) {
          errors.push(`Field '${fieldName}' must be one of [${fieldDef.allowedValues.join(', ')}], got '${val}'`);
        }
      }
    }
  }

  // 3. Conditional Business Invariants
  const issueDateStr = mappedData.issue_date?.value;
  const expiryDateStr = mappedData.expiry_date?.value;
  const latestShipmentDateStr = mappedData.latest_shipment_date?.value;

  if (issueDateStr && expiryDateStr) {
    const issueDate = new Date(issueDateStr);
    const expiryDate = new Date(expiryDateStr);
    if (!isNaN(issueDate) && !isNaN(expiryDate) && expiryDate < issueDate) {
      errors.push(`Conditional Invariant Failed: Expiry date (${expiryDateStr}) cannot precede Issue date (${issueDateStr})`);
    }
  }

  if (latestShipmentDateStr && expiryDateStr) {
    const latestShipmentDate = new Date(latestShipmentDateStr);
    const expiryDate = new Date(expiryDateStr);
    if (!isNaN(latestShipmentDate) && !isNaN(expiryDate) && latestShipmentDate > expiryDate) {
      errors.push(`Conditional Invariant Failed: Latest shipment date (${latestShipmentDateStr}) cannot be after Expiry date (${expiryDateStr})`);
    }
  }

  return {
    isValid: errors.length === 0,
    errors,
    warnings
  };
}
