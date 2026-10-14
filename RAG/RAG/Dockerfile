# 构建
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# 运行（适配 2C2G 小内存服务器）
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/rag-ticket-1.0.0.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx640m -XX:MaxMetaspaceSize=160m -XX:+UseG1GC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
