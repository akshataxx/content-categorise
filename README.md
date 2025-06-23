## 🚀 To get started 

### Add your openai api key
Add your key in `application-secrets.properties`:

```properties
openai.api.key=<YOUR_API_KEY>
```

### Install MongoDB
```declarative
brew tap mongodb/brew
brew install mongodb-community
brew services start mongodb-community
```
This will:
- Install the MongoDB server,
- Run it in the background,
- Make it available on localhost:27017.

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

To avoid real OpenAI API calls (and cost), the app includes a mock implementation used in development mode.

Set the profile in `application-secrets.properties`:

```properties
spring.profiles.active=dev   # ➜ enables mock client (returns dummy responses)
spring.profiles.active=prod  # ➜ uses actual OpenAI API
```