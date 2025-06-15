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

## Mocking openai api
Since OpenAI API is expensive, the clients have mock implementations to return dummy responses. To enable mocking, set `spring.profiles.active=dev` in `src/main/resources/application-secrets.properties`. Set it to `prod` to disable mocking