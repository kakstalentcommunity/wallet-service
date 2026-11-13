FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S wallet && adduser -S wallet -G wallet
WORKDIR /app
COPY --from=build /workspace/target/wallet-0.0.1-SNAPSHOT.jar /app/wallet.jar
USER wallet:wallet
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 CMD wget -q -O - http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/wallet.jar"]
