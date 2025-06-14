## To get started 
In `src/main/resources/application-secrets.properties` add your open api key under `openai.api.key`.

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