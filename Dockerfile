FROM eclipse-temurin:21-jre-alpine

WORKDIR /application

COPY build/libs/*.jar vibe-data.jar

ENTRYPOINT ["java","-jar","vibe-data.jar"]
