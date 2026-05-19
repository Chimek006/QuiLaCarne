# WebSocket realtime sync

The current API documentation does not publish a WebSocket endpoint, topic list, or payload schema.
The Android client therefore treats WebSocket support as an optional generic realtime trigger.

## Configuration

Set the endpoint in `local.properties`:

```properties
WEBSOCKET_URL=wss://api.quilacarne.com.pl/<backend-websocket-path>
```

Leave `WEBSOCKET_URL` empty when the backend does not expose a WebSocket endpoint.

## Authorization

The app sends the current access token as:

```http
Authorization: Bearer <access-token>
```

## Runtime behavior

- When `WEBSOCKET_URL` is empty, WebSocket is disabled and the app uses fallback polling.
- When the socket connects, the app runs `syncOperationalData()`.
- Every text or binary message is treated as a trigger to run `syncOperationalData()`.
- The client does not parse concrete event types yet because the API docs do not define them.
- If the connection closes or fails, the client reconnects after a short delay.
- While WebSocket is not connected, `MainActivity` runs one central fallback poll instead of every screen polling independently.

## Diagnostics

Relevant log tags:

- `REALTIME_WS` - WebSocket connection, messages, reconnects, sync triggers.
- `CENTRAL_SYNC` - fallback polling when WebSocket is disabled or disconnected.
- `SYNC_FLOW` - serialized operational sync start/end/failure.
- `TABLE_STATUS_SYNC` - table status received from API, previous local status, final saved status.
