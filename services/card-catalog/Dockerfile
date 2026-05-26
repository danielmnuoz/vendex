# Multi-stage: build the fat jar with Maven, then run it on a slim JRE image.
# Mirrors services/auth/Dockerfile.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY pom.xml .
COPY proto/pom.xml proto/pom.xml
COPY services/auth/pom.xml services/auth/pom.xml
COPY services/card-catalog/pom.xml services/card-catalog/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    apt-get update && apt-get install -y --no-install-recommends maven && \
    mvn -B -pl services/card-catalog -am dependency:go-offline -DskipTests || true

COPY proto/src proto/src
COPY services/auth/src services/auth/src
COPY services/card-catalog/src services/card-catalog/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/card-catalog -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/card-catalog/target/card-catalog-exec.jar /app/card-catalog.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/card-catalog.jar"]
