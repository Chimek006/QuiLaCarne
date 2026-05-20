# Operational REST Sync

The public QuiLaCarne API documentation does not expose a WebSocket, STOMP, or SSE channel. The Android app therefore keeps operational screens fresh with authenticated HTTPS REST sync only.

The coordinator uses the documented sync endpoints:

- `GET /api/sync/bootstrap`
- `GET /api/sync/orders`
- `GET /api/sync/order-items`
- `GET /api/sync/dishes`
- `GET /api/sync/ingredients`
- `GET /api/sync/tables`
- `GET /api/sync/reservations`
- `GET /api/sync/users`
- `GET /api/sync/reports`
- `GET /api/sync/bans`
- `GET /api/sync/dictionaries`
- `GET /api/sync/roles`

`OperationalSyncCoordinator` triggers sync after login or connection recovery, after entering operational screens, and by polling every 30 seconds only while a relevant screen is active. State-changing REST calls still refresh through `SyncRepository`, which serializes operational sync through its existing mutex.
