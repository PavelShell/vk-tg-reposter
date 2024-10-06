FROM eclipse-temurin:21-jre
RUN mkdir /opt/app
COPY build/libs/vk-tg-reposter.jar /opt/app
ADD --chmod=555 https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux /bin/yt-dlp
CMD ["java", "-jar", "/opt/app/vk-tg-reposter.jar"]
