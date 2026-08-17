import assert from 'node:assert/strict';
import test from 'node:test';
import {
  DeliveryStatusContractError,
  parseDeliveryStatusResponse,
  presentationFor,
  safeTrackingUrl,
  shouldAutoRefresh,
} from './delivery-status.ts';

const ORDER_ID = '11111111-2222-4333-8444-555555555555';
const JOB_ID = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';

test('parses and sanitizes the public delivery response', () => {
  const parsed = parseDeliveryStatusResponse({
    orderId: ORDER_ID,
    deliveryJobId: JOB_ID,
    providerId: 'borzo',
    providerDeliveryId: 'must-not-leak',
    status: 'IN_TRANSIT',
    trackingUrl: 'https://tracking.example/order/1',
    observedAt: '2026-07-29T00:00:00Z',
    rawPayload: { secret: true },
    history: [{
      oldStatus: 'PICKED_UP',
      newStatus: 'IN_TRANSIT',
      trackingUrl: 'https://tracking.example/order/1',
      observedAt: '2026-07-29T00:00:00Z',
      recordedAt: '2026-07-29T00:00:01Z',
      providerDeliveryId: 'must-not-leak',
    }],
  });
  assert.equal(parsed.status, 'IN_TRANSIT');
  assert.equal(parsed.history.length, 1);
  assert.equal('providerDeliveryId' in parsed, false);
  assert.equal('rawPayload' in parsed, false);
});

test('allows an owned order before its first delivery projection', () => {
  const parsed = parseDeliveryStatusResponse({
    orderId: ORDER_ID,
    deliveryJobId: null,
    providerId: null,
    status: null,
    trackingUrl: null,
    observedAt: null,
    history: [],
  });
  assert.equal(parsed.status, null);
  assert.equal(shouldAutoRefresh(parsed.status), true);
});

test('rejects unsupported statuses and invalid identifiers', () => {
  assert.throws(
    () => parseDeliveryStatusResponse({ orderId: 'bad', status: null, history: [] }),
    DeliveryStatusContractError,
  );
  assert.throws(
    () => parseDeliveryStatusResponse({ orderId: ORDER_ID, status: 'UNKNOWN', history: [] }),
    DeliveryStatusContractError,
  );
});

test('accepts only HTTPS tracking links', () => {
  assert.equal(safeTrackingUrl('https://tracking.example/1'), 'https://tracking.example/1');
  assert.equal(safeTrackingUrl('http://tracking.example/1'), null);
  assert.equal(safeTrackingUrl('javascript:alert(1)'), null);
});

test('classifies terminal and attention states', () => {
  assert.equal(presentationFor('DELIVERED').terminal, true);
  assert.equal(presentationFor('FAILED').attention, true);
  assert.equal(shouldAutoRefresh('RETURNED'), false);
  assert.equal(shouldAutoRefresh('IN_TRANSIT'), true);
});
