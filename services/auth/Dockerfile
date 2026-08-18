# Multi-stage: build the fat jar with Maven, then run it on a slim JRE image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so they cache across source-only changes.
COPY pom.xml .
COPY proto/pom.xml proto/pom.xml
COPY events/pom.xml events/pom.xml
COPY services/auth/pom.xml services/auth/pom.xml
COPY services/card-catalog/pom.xml services/card-catalog/pom.xml
COPY services/event/pom.xml services/event/pom.xml
COPY services/inventory/pom.xml services/inventory/pom.xml
COPY services/buylist/pom.xml services/buylist/pom.xml
COPY services/overlap/pom.xml services/overlap/pom.xml
COPY services/notification/pom.xml services/notification/pom.xml
COPY gateway/pom.xml gateway/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    apt-get update && apt-get install -y --no-install-recommends maven && \
    mvn -B -pl services/auth -am dependency:go-offline -DskipTests || true

# Build.
COPY proto/src proto/src
COPY services/auth/src services/auth/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/auth -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/auth/target/auth-exec.jar /app/auth.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/auth.jar"]
