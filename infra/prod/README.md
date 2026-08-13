# Single EC2 production stack

The production stack runs Nginx, MySQL, Redis, and two API instances on one
Docker network. Only Nginx publishes a host port.

## Start

```bash
cd infra/prod
cp .env.example .env
# Fill every empty value and replace the example domains.
./start.sh
```

`start.sh` builds the Nginx and shared API images first, then enforces the
startup phases below:

1. Nginx becomes healthy.
2. MySQL and Redis become healthy.
3. Both API instances become healthy.

The same health dependencies are declared in `compose.yml`, so the API
containers cannot start before Nginx, MySQL, and Redis are healthy.

## Network exposure

| Service | Host port | Docker network port |
| --- | --- | --- |
| Nginx | `${HTTP_PORT:-80}` | `80` |
| API 1 / API 2 | None | `8080` |
| MySQL | None | `3306` |
| Redis | None | `6379` |

MySQL and Redis data are persisted in the `mysql-data` and `redis-data` named
volumes. `docker compose down` keeps these volumes; do not use `down --volumes`
in production unless the data is intentionally being deleted.

## Operations

```bash
docker compose --env-file .env -f compose.yml ps
docker compose --env-file .env -f compose.yml logs -f --tail=200
docker compose --env-file .env -f compose.yml down
```

The initial deployment uses Hibernate `ddl-auto=update` because this repository
does not yet contain a schema migration tool. Introduce Flyway before changing
`SPRING_JPA_HIBERNATE_DDL_AUTO` to `validate`.
