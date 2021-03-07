# Diary Server
Backend component to process requests made by the dear-diary command line. Definitely a work in progress.

## Docker Setup
```shell
git clone <repo>
cd diary-server-ktor
cp .env.example .env.dev
# Add your s3 access+secret keys
vim .env.dev
./gradlew clean installDist
docker-compose build
docker-compose up -d
```