const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('node:crypto');
process.env.GCLOUD_PROJECT = 'fashion2hand';
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
process.env.SHIPMENT_WEBHOOK_TOKEN = crypto.randomUUID();
const handler = require('../index').updateShipmentFromCarrier;
const { getFirestore } = require('firebase-admin/firestore');
const { getApp, deleteApp } = require('firebase-admin/app');
const db = getFirestore();
after(() => deleteApp(getApp()));

async function carrierServer() {
  const server = http.createServer(async (req, res) => {
    let text = ''; for await (const chunk of req) text += chunk;
    req.body = text ? JSON.parse(text) : {};
    req.get = name => req.headers[name.toLowerCase()];
    res.status = code => { res.statusCode = code; return res; };
    res.send = body => res.end(body);
    res.json = body => { res.setHeader('content-type', 'application/json'); res.end(JSON.stringify(body)); };
    await handler(req, res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  async function send(body, token = process.env.SHIPMENT_WEBHOOK_TOKEN) {
    return fetch(url, { method: 'POST', headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: JSON.stringify(body) });
  }
  return { send, close: () => new Promise(resolve => server.close(resolve)) };
}

test('carrier webhook authenticates, deduplicates and never reopens a terminal shipment', async () => {
  const { send, close } = await carrierServer();
  const orderId = `carrier-${crypto.randomUUID()}`, trackingCode = `TRACK-${orderId}`;
  await db.doc(`orders/${orderId}`).set({ buyerId: `buyer-${orderId}`, sellerId: `seller-${orderId}`, orderStatus: 'SHIPPING', shippingStatus: 'IN_TRANSIT', orderType: 'FREE' });
  await db.doc(`shipments/${orderId}`).set({ orderId, trackingCode, status: 'IN_TRANSIT', events: [{ status: 'IN_TRANSIT' }] });
  const event = { eventId: crypto.randomUUID(), orderId, trackingCode, status: 'DELIVERED', description: 'Verified carrier callback fixture' };
  try {
    assert.equal((await send(event, 'invalid')).status, 401);
    assert.equal((await send({ ...event, eventId: undefined })).status, 400);
    assert.equal((await send(event)).status, 200);
    assert.equal((await send(event)).status, 200);
    const delivered = await db.doc(`orders/${orderId}`).get();
    assert.equal(delivered.get('orderStatus'), 'DELIVERED'); assert.ok(delivered.get('inspectionDeadline'));
    assert.equal((await db.doc(`shipments/${orderId}`).get()).get('events').length, 2);
    assert.equal((await send({ ...event, eventId: crypto.randomUUID(), status: 'IN_TRANSIT' })).status, 404);
    assert.equal((await send({ ...event, eventId: crypto.randomUUID(), status: 'RETURNED' })).status, 404);
    assert.equal((await send({ ...event, orderId: 'different-order' })).status, 404);
    assert.equal((await db.doc(`orders/${orderId}`).get()).get('orderStatus'), 'DELIVERED');
  } finally { await close(); }
});

test('carrier return cancels only the request accepted for this order and retries preserve a new request', async () => {
  const { send, close } = await carrierServer();
  try {
    for (const matchingOrder of [true, false]) {
      const orderId = `return-${crypto.randomUUID()}`, trackingCode = `TRACK-${orderId}`;
      const postId = `post-${orderId}`, requestId = `request-${orderId}`;
      const requestRef = db.doc(`freeRequests/${requestId}`);
      await db.doc(`posts/${postId}`).set({ status: 'RESERVED', reservedForOrderId: orderId });
      await requestRef.set({ status: 'ACCEPTED', orderId: matchingOrder ? orderId : 'newer-order' });
      await db.doc(`orders/${orderId}`).set({ postId, requestId, buyerId: `buyer-${orderId}`, sellerId: `seller-${orderId}`, orderStatus: 'SHIPPING', shippingStatus: 'IN_TRANSIT', orderType: 'FREE' });
      await db.doc(`shipments/${orderId}`).set({ orderId, trackingCode, status: 'IN_TRANSIT', events: [] });
      const event = { eventId: crypto.randomUUID(), orderId, trackingCode, status: 'RETURNED' };
      assert.equal((await send(event)).status, 200);
      assert.equal((await db.doc(`orders/${orderId}`).get()).get('orderStatus'), 'CANCELLED');
      assert.equal((await db.doc(`posts/${postId}`).get()).get('status'), 'ACTIVE');
      assert.equal((await requestRef.get()).get('status'), matchingOrder ? 'CANCELLED' : 'ACCEPTED');
      await requestRef.set({ status: 'PENDING' });
      for (const eventId of [event.eventId, crypto.randomUUID()]) assert.equal((await send({ ...event, eventId })).status, 200);
      assert.equal((await requestRef.get()).get('status'), 'PENDING');
    }
  } finally { await close(); }
});
