# API reference

`openapi.yaml` and `openapi.json` describe the REST API under `/api` (OpenAPI 3.1). They are generated, not written: every production build (`./mvnw package`) rewrites them from the JAX-RS resources, merged with `src/main/resources/META-INF/openapi.yaml`, which holds the title, description and security scheme. Commit them with the change that alters the API, so the diff shows the contract moving.

Dev and test builds leave them alone: the dev-only endpoints (`/api/dev/persona`) must not appear in the reference.

To browse them, run `./mvnw quarkus:dev` and open the Swagger UI at <http://localhost:8080/q/swagger-ui/>, or load `openapi.yaml` in any OpenAPI viewer (<https://editor.swagger.io/>, an IDE plugin).
