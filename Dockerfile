# -------- build stage --------
FROM maven:3.9.4-eclipse-temurin-21 AS build
LABEL authors="saurabhsahu"
ENTRYPOINT ["top", "-b"]
WORKDIR /build

# copy only maven descriptors first to leverage Docker layer cache
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN mvn -B -f pom.xml -q -e dependency:go-offline

# copy sources and build fat jar
COPY src ./src
RUN mvn -B -f pom.xml -q clean package -DskipTests

# -------- runtime stage --------
FROM eclipse-temurin:21-jre-jammy
ARG APP_JAR=/build/target/*.jar
WORKDIR /app

# copy the fat jar from build stage
COPY --from=build /build/target/*.jar app.jar

# runtime settings (tune if needed)
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

# expose HTTP port
EXPOSE 8080

# default entrypoint
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
