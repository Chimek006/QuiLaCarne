# WebSocket Contract

The Android app reads `WEBSOCKET_URL` from `local.properties`.
Release builds require a secure `wss://` URL. Debug builds may use local hosts configured in the debug network security config.

Every WebSocket request should include:

```http
Authorization: Bearer <JWT>
```

Messages should be JSON objects. The event name can be sent as `type`, `event`, or `name`.
If `payload` is missing, the app treats the root object as the payload.

## Events

### MENU_CHANGED

Triggers `syncMenu()`.

```json
{
  "type": "MENU_CHANGED"
}
```

### TABLE_STATUS_CHANGED

Triggers operational sync.

```json
{
  "type": "TABLE_STATUS_CHANGED",
  "payload": {
    "tableNumber": 4
  }
}
```

### ORDER_STATUS_CHANGED

Triggers operational sync.

```json
{
  "type": "ORDER_STATUS_CHANGED",
  "payload": {
    "orderToken": "..."
  }
}
```

### WAITER_ASSIGNED

Triggers operational sync and a local notification only when the assigned waiter can be matched to the current user.
Use `waiterUsername` when possible. `waiterToken` is also supported.

```json
{
  "type": "WAITER_ASSIGNED",
  "payload": {
    "waiterUsername": "waiter_1",
    "tableNumber": 4
  }
}
```

### ORDER_ITEM_STATUS_CHANGED

Triggers operational sync. If the status is ready, the app shows a local notification on the `dish_ready` channel.

Ready status tokens are matched case-insensitively:
`READY`, `READY_FOR_PICKUP`, `READY_TO_SERVE`, `PREPARED`, `DONE`, `GOTOWE`.

Supported alternative fields:

- `statusToken`, `status`, `orderItemStatus`
- `dishName`, `productName`, `itemName`, `name`
- `tableNumber`, `table`, `tableNo`
- `orderToken`, `order`, `orderId`
- `orderItemToken`, `itemToken`, `token`

```json
{
  "type": "ORDER_ITEM_STATUS_CHANGED",
  "payload": {
    "orderItemToken": "...",
    "orderToken": "...",
    "dishName": "Pizza Margherita",
    "tableNumber": 4,
    "statusToken": "READY_FOR_PICKUP"
  }
}
```

### DISH_READY

Triggers operational sync and a local notification without requiring a status token.

```json
{
  "type": "DISH_READY",
  "payload": {
    "dishName": "Pizza Margherita",
    "tableNumber": 4,
    "orderToken": "..."
  }
}
```

Unknown events still trigger operational sync so the offline/online data flow stays consistent while the backend contract evolves.
