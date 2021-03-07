FROM openjdk:8-jre-alpine
RUN mkdir /app
COPY ./build/install/diary-server-ktor/ /app/
WORKDIR /app/bin
CMD ["./diary-server-ktor"]