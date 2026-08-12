# Production Nginx routing

This directory owns the reverse proxy configuration for two API containers.

## Routing policy

| Request | Upstream policy | Reason |
| --- | --- | --- |
| REST API and other HTTP | `least_conn` | Distribute independent requests across both API instances |
| Match notification SSE | `least_conn`, buffering disabled | Keep the long-lived event stream responsive |
| `/ws/game/{gameRoomId}` | Consistent hash by `gameRoomId` | Keep both game participants in one in-memory session registry |
| `/ws/custom-games/rooms/{roomId}` | Consistent hash by `roomId` | Keep custom-room participants in one in-memory session registry |
| `/actuator`, `/actuator/**` | `404`, not proxied | Keep internal metrics and management endpoints off the public entry point |

Query parameters are not part of the affinity key. For example, every request to
`/ws/game/100` is routed to the same API instance even when authentication query
parameters differ.

## Required service names

The Docker network must resolve these names:

- `league-of-star-api-1:8080`
- `league-of-star-api-2:8080`

Both API containers and Nginx must join the same Docker network. The production
Compose issue will mount or build this configuration and expose only Nginx to the
host.

`worker_connections` is set to 16,384 because each proxied long-lived connection
uses one client connection and one upstream connection. The container open-file
limit must remain at least 16,384 in the production Compose configuration.

## Failure behavior

Nginx marks an API instance unavailable when a connection fails. A subsequent
WebSocket reconnect is then routed to the remaining instance. The failed
handshake itself is not guaranteed to move to another instance, and an
established WebSocket cannot move because its session is held in application
memory. The client must reconnect after an instance failure.

## Validation

Build and validate the configuration without starting the application:

```bash
docker build -t league-of-star-nginx:local infra/prod/nginx
docker run --rm --entrypoint nginx league-of-star-nginx:local -t
sh infra/prod/nginx/test/verify-routing.sh
```

On Windows PowerShell, run
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/prod/nginx/test/verify-routing.ps1`.

TLS certificates and the public domain are intentionally not configured here.
They must be added after the deployment domain and certificate termination point
are decided.
