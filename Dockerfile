# syntax=docker/dockerfile:1.2

FROM gradle:7.5.1-jdk17-focal AS build
COPY --chown=gradle:gradle . /work
WORKDIR /work
RUN --mount=type=cache,sharing=locked,target=/root/.gradle gradle :clean :build --no-daemon -x test --parallel

FROM openjdk:17
COPY --from=build /work/build/libs/kurnik-0.0.1.jar app.jar

ENTRYPOINT java -jar /app.jar
