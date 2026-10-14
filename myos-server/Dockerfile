FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app
# 容器内没有宿主机的 ~/.m2/settings.xml，不带镜像会直连 Maven Central，
# 国内网络下慢且易超时。docker-settings.xml 只有公共镜像、无凭据。
COPY docker-settings.xml .
RUN mvn -q -s docker-settings.xml -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -s docker-settings.xml -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/myos-server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
