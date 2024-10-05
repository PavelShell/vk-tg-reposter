FROM eclipse-temurin:21 as jre-build

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:21 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN mkdir /opt/app
COPY build/libs/vk-tg-reposter.jar /opt/app
ADD --chmod=555 https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux /bin/yt-dlp
CMD ["java", "-jar", "/opt/app/vk-tg-reposter.jar"]
