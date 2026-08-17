# SSU Web API v1 foundation

The dev3.36 bridge is deliberately **read-only**. It is designed for server status pages and as the security/threading foundation for later remote administration.

## Configuration
The API is disabled by default.

```toml
enableWebApi = true
webApiBindAddress = "127.0.0.1"
webApiPort = 8765
webApiToken = "replace-with-a-long-random-secret"
webApiAllowedOrigin = ""
```

For production, prefer the `SSU_WEB_API_TOKEN` environment variable over committing a token to a config file. If the environment variable is present and non-blank it overrides `webApiToken`.

A token of at least 16 characters is mandatory whenever the API is enabled. Keep the default loopback bind when a reverse proxy runs on the same host. If you deliberately bind to a non-loopback interface, protect the port with a firewall/reverse proxy and TLS at the proxy.

## Authentication
Every GET request requires:

```text
Authorization: Bearer <token>
```

## Endpoints
- `GET /api/v1/health` — small liveness response.
- `GET /api/v1/status` — versioned status snapshot with online player list and NPC runtime counts.
- `GET /api/v1/players` — online count plus UUID/name/dimension rows.
- `GET /api/v1/capabilities` — advertises which API feature families exist. dev3.36 reports remote actions as disabled.

## Threading model
Minecraft data is read only on the Minecraft server thread. SSU creates an immutable snapshot approximately once per second. HTTP worker threads serve that immutable snapshot and never traverse live entity/player/world collections.

## Planned next layer
A future write API can build on this bridge with explicit scopes/permissions, an audited server-thread action queue, rate limiting and narrow action types. Raw remote command execution is intentionally not part of dev3.36.
