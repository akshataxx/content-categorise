## To get started 

### Add your openai api key
In `src/main/resources/application-secrets.properties` add your key under `openai.api.key`.

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

## 📦 Project Structure Overview

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

## Mocking openai api
Since OpenAI API is expensive, the clients have mock implementations to return dummy responses. To enable mocking, set `spring.profiles.active=dev` in `src/main/resources/application-secrets.properties`. Set it to `prod` to disable mocking
