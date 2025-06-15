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

| Package           | Description                                                                                                                                |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `controller`      | Handles incoming HTTP requests and maps them to service layer methods.                                                                     |
| `service`         | Contains core business logic. Interacts with repositories, processes data, and performs orchestration.                                     |
| `repository`      | Interfaces with the database using Spring Data.                                                                                            |
| `client`          | Contains code to call external services or APIs (e.g. OpenAI) |
| `config`          | Configuration classes for beans, properties, and third-party integrations.           |
| `exception`       | Defines custom exception classes and global error handling using `@RestControllerAdvice` and `@ExceptionHandler`.                          |
| `util`            | Utility classes or static helper methods for shared logic such as file handling or string manipulation.                                    |
| `models.entity`   | Represents domain objects that are mapped to the database.                                                                                 |
| `models.dto`      | Data Transfer Objects used to external structure API requests and responses. Helps decouple internal models from external representations. |
| `models.internal` | Objects used for intermediate representations of data. Not persisted to the database.                                                      |

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