FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 로컬 build/libs 폴더의 Spring Boot 실행 JAR를 컨테이너로 복사
COPY build/libs/filly-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]
