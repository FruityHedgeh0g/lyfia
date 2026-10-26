# Lyfia

Lyfia is the software behind the site and member space of the association **Une Rose Un Espoir**: public pages, a member space ("Mon espace") and an administration space ("Administration"), across its Secteurs.

"Lyfia" names the software only. People using the site see the association, never Lyfia. The domain vocabulary lives in [`CONTEXT.md`](CONTEXT.md) and the decisions in [`docs/adr/`](docs/adr/).

## Stack

- [Quarkus](https://quarkus.io/) (Java 21) for the REST API
- [Quinoa](https://docs.quarkiverse.io/quarkus-quinoa/dev/) serving the React frontend in `src/main/webui`
- Keycloak for authentication (OIDC) and Role mirroring (ADR 0002)
- RabbitMQ for user events published by Keycloak

## Running in dev mode

```shell
./mvnw quarkus:dev
```

The dev profile stores data in a local H2 file under `data/`. The Dev UI is at <http://localhost:8080/q/dev/>.

## Changing the schema

Production runs on PostgreSQL with a schema owned by Flyway, and Hibernate only validates the entities against it (ADR 0005). Dev and tests run on H2, where Hibernate updates the schema itself, so they never notice a missing migration.

When an entity changes, add `src/main/resources/db/migration/V<n>__<what>.sql` in PostgreSQL syntax. Never edit a migration that has already shipped.

## Tests

```shell
./mvnw verify
```

## Packaging

```shell
./mvnw package
```

This produces `target/quarkus-app/`, runnable with `java -jar target/quarkus-app/quarkus-run.jar`.

## The image

CI builds a native image for `amd64` and `arm64` and pushes both under the same tags to `quay.io/fruityhedgehog/lyfia`, so `docker pull` picks the one matching the host:

- `latest` and `sha-<short>` from `main`
- `X.Y.Z` from a git tag `vX.Y.Z`

Before pushing, CI starts each image against PostgreSQL 17 and RabbitMQ and checks that it answers. To build it locally for your own architecture (Docker only, no JDK needed):

```shell
docker build -t lyfia .
```

The image holds no configuration secret. Provide these as environment variables:

| Variable | What |
|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL` | `jdbc:postgresql://<host>:5432/<db>` |
| `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD` | PostgreSQL account |
| `QUARKUS_OIDC_AUTH_SERVER_URL` | `https://<keycloak>/realms/<realm>` |
| `QUARKUS_OIDC_CLIENT_ID`, `QUARKUS_OIDC_CREDENTIALS_SECRET` | Keycloak client for logging in |
| `QUARKUS_KEYCLOAK_ADMIN_CLIENT_SERVER_URL`, `QUARKUS_KEYCLOAK_ADMIN_CLIENT_REALM` | Keycloak admin API, for Role mirroring |
| `QUARKUS_KEYCLOAK_ADMIN_CLIENT_CLIENT_ID`, `QUARKUS_KEYCLOAK_ADMIN_CLIENT_CLIENT_SECRET` | Keycloak client with realm-management rights |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_VIRTUAL_HOST` | RabbitMQ carrying Keycloak's user events |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | RabbitMQ account |
| `LYFIA_SUPER_ADMIN` | Keycloak username of the Super admin, made so at every startup (ADR 0006). Optional |

The app listens on port 8080, and `/q/health` reports whether it can reach its database. It stops at startup if RabbitMQ cannot be reached.
