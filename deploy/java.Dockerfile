FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY config config
COPY common common
COPY server server
COPY edge edge
COPY simulator/robot simulator/robot
RUN mvn -B -ntp verify

FROM eclipse-temurin:17-jre AS server
WORKDIR /app
COPY --from=build /workspace/server/target/server-0.1.0.jar app.jar
ENV SERVER_BIND=0.0.0.0
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre AS edge
WORKDIR /app
COPY --from=build /workspace/edge/target/edge-0.1.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre AS robot
WORKDIR /app
COPY --from=build /workspace/simulator/robot/target/robot-simulator-0.1.0.jar app.jar
ENV MODBUS_BIND=0.0.0.0
ENTRYPOINT ["java", "-jar", "app.jar"]
