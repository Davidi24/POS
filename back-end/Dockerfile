# The POS backend as a container, used by ../docker-compose.yml (see ../start.sh).
# Build: the Maven wrapper packages the jar (tests skipped); the Maven cache is kept between builds.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
 && ./mvnw -B -ntp package -Dmaven.test.skip=true \
 && cp target/pos-*.jar /app.jar

# Run: just a Java runtime and the jar, as a normal user. Uploaded photos live in /app/uploads (a volume).
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app pos \
 && mkdir -p /app/uploads \
 && chown -R pos /app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER pos
EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
