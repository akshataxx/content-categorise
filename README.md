## 🚀 To get started 

### Add your OpenAI API key
Copy to `.env` and add your key there:

```dotenv
OPENAI_API_KEY=<YOUR_API_KEY>
```

### Setup Database
#### Install Postgres
```shell
brew install postgresql
brew services restart postgresql@14

brew install --cask pgadmin4
open -a pgAdmin\ 4 #(To open pgAdmin)
```
This will:
- Install the Postgres server,
- Run it in the background,
- Install pgAdmin (GUI for Postgres).

#### Create Database
```shell
psql -U postgres 
# Once the shell is open, run the following command:
CREATE DATABASE contentdb;
```
This will create a new database called "contentdb" with the default user "postgres".
You can choose to use a different user or database name if desired, just have update the `application.properties` accordingly.

### Install other dependencies
```declarative
brew install yt-dlp
brew install ffmpeg
```

## 🧱 Project Structure Overview

This project follows a layered architecture using standard Java/Spring concepts:

| Package                | Description                                                                                                                                                             |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `domain`               | Core business logic and rules                                                                                                                                           |
| `domain.model`         | Pure domain models — represent business concepts (e.g. Transcript, User) with only the fields your app actually needs to work with. No annotations, no framework stuff. |
| `domain.service`       | Domain service interfaces defining core business operations.                                                                                                            |
| `domain.repository`    | Repository interfaces for abstracting data access.                                                                                                                      |
| `data`                 | Data persistence and external service interactions.                                                                                                                     |
| `data.entity`          | Database entities with MongoDB annotations (e.g. UserEntity, CategoryAliasEntity).                                                                                      |
| `data.repository`      | MongoDB repository implementations using Spring Data.                                                                                                                   |
| `data.client`          | Clients for external services (e.g. OpenAI client).                                                                                                                     |
| `application`          | Coordinates between the domain logic and data access. Implements business use cases and maps data between layers.                                                       |
| `application.dto`      | Internal Data Transfer Objects used for converting between domain, entity, and API DTOs.                                                                                
 `application.mapper`   | Maps between domain models, entities, and DTOs.                                                                                                                         |
| `application.internal` |                                                                                                                                                                         |
| `ui`                   | Presentation Layer                                                                                                                                                      |
| `api`                  | HTTP communication layer                                                                                                                                                |
| `api.controller`       | REST controllers that handle incoming HTTP requests.                                                                                                                    |
| `util`                 | Utility classes or static helper methods for shared logic such as file handling or string manipulation.                                                                 
| `exception`            | Defines custom exception classes and global error handling using `@RestControllerAdvice` and `@ExceptionHandler`.                                                       
| `config`               | Configuration classes for beans, properties, and third-party integrations.                                                                                              


## 📘 API Docs (Springdoc OpenAPI)

This project uses [Springdoc OpenAPI](https://springdoc.org/) to auto-generate API docs.

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

## 🧩 Development Mode (Mocking OpenAI API)

## 📜 Logging

This repo is configured for cost-efficient file logging with rolling rotation.

- Default file: `logs/content-app.log`
- Rotation: size + daily, compressed archives
- Defaults can be overridden via environment variables:

```bash
# examples
LOG_LEVEL_ROOT=DEBUG \
LOG_FILE_NAME=logs/content-app.log \
LOG_FILE_PATTERN=logs/content-app-%d{yyyy-MM-dd}.%i.log.gz \
LOG_MAX_FILE_SIZE=10MB \
LOG_MAX_HISTORY=7 \
LOG_TOTAL_SIZE_CAP=1GB \
./mvnw spring-boot:run
```

In Docker (compose), logs are persisted to the host at `./logs` via a volume mount. You can tail the logs with:

```bash
# host
tail -f logs/content-app.log
```

Tip: At runtime, you can change log levels dynamically using Spring Boot Actuator's `/actuator/loggers` endpoint (expose it if desired).

Extensibility: If you later want to ship logs to a platform (e.g., Logstash/ELK, Datadog), add a custom `logback-spring.xml` with an additional appender and optionally the `logstash-logback-encoder` dependency. The current property-based setup continues to work unchanged.


To avoid real OpenAI API calls (and cost), the app includes a mock implementation used in development mode.

Select the profile via environment variables (or Makefile targets):

```properties
spring.profiles.active=dev   # ➜ enables mock client (returns dummy responses)
spring.profiles.active=prod  # ➜ uses actual OpenAI API
```