FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 로컬 build/libs 폴더의 jar 파일을 컨테이너로 복사
COPY build/libs/*.war app.jar

EXPOSE 8080

# 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]