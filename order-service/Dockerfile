FROM maven:3.9-eclipse-temurin-21 AS build
ENV MAVEN_OPTS="-Xmx256m -XX:ActiveProcessorCount=2"
WORKDIR /build
COPY pom.xml ./
COPY customer-service/pom.xml customer-service/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY infrastructure/database-migrations/pom.xml infrastructure/database-migrations/pom.xml
COPY order-service/src order-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -pl order-service -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget && addgroup -S app && adduser -S app -G app
ARG OTEL_AGENT_VERSION=2.31.1
RUN wget -q -O /otel.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar
WORKDIR /app
COPY --from=build /build/order-service/target/order-service-1.0.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/otel.jar", "-jar", "/app/app.jar"]
