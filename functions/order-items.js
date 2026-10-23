/** Additive multi-item support. Legacy orders continue to use their single postId. */
function orderPostIds(order) {
  const items = order.get('items');
  return [...new Set(Array.isArray(items) && items.length ? items.map(item => item.postId) : [order.get('postId')])].filter(id => typeof id === 'string' && id && !id.includes('/'));
}
async function readOrderPosts(tx, order, db) {
  if (!order.exists) return [];
  const snapshots = await Promise.all(orderPostIds(order).map(id => tx.get(db.doc(`posts/${id}`))));
  return snapshots.map(snapshot => ({ snapshot, orderId: order.id }));
}
function writeOrderPosts(tx, entries, patch) {
  for (const { snapshot, orderId } of entries)
    if (snapshot.exists && snapshot.get('reservedForOrderId') === orderId) tx.update(snapshot.ref, patch);
}
module.exports = { orderPostIds, readOrderPosts, writeOrderPosts };