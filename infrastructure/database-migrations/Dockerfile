FROM maven:3.9-eclipse-temurin-21 AS build
ENV MAVEN_OPTS="-Xmx256m -XX:ActiveProcessorCount=2"
WORKDIR /build
COPY pom.xml ./
COPY customer-service/pom.xml customer-service/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY infrastructure/database-migrations/pom.xml infrastructure/database-migrations/pom.xml
COPY infrastructure/database-migrations/src infrastructure/database-migrations/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -pl infrastructure/database-migrations -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/infrastructure/database-migrations/target/database-migrations-1.0.0-SNAPSHOT.jar migrations.jar
USER app
ENTRYPOINT ["java", "-Xmx128m", "-jar", "/app/migrations.jar"]
