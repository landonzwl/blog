FROM maven:3.9.6-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package && cp target/*.jar /app/app.jar

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar
EXPOSE 8081
ENV JAVA_OPTS="-Xms64m -Xmx256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
