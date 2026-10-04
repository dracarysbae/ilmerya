FROM eclipse-temurin:21-jdk AS build
WORKDIR /source
COPY gradlew ./gradlew
COPY gradle/wrapper/ ./gradle/wrapper/
COPY shared/src/commonMain/kotlin/ ./shared/src/commonMain/kotlin/
COPY server/ ./server/
RUN chmod +x gradlew && ./gradlew -p server test installDist --no-daemon --max-workers=2

FROM eclipse-temurin:21-jre
WORKDIR /opt/ilmerya
COPY --from=build /source/server/build/install/ilmerya-league/ ./
ENV BIND_ADDRESS=0.0.0.0 PORT=8080 REQUIRE_PERSISTENT_DB=true JAVA_OPTS="-Xms32m -Xmx256m"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/opt/ilmerya/bin/ilmerya-league"]
