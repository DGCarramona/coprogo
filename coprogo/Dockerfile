# syntax=docker/dockerfile:1

# Keep the digest when updating the tag: both builds must use the same toolchain.
FROM ghcr.io/graalvm/native-image-community:25-ol9@sha256:7d17320ffc98d5aaffdf8c1aae929e659dc440f7a7722eb5ad2cade1ac834af7 AS build

WORKDIR /workspace

COPY --chmod=755 gradlew ./gradlew
COPY gradle/wrapper/ ./gradle/wrapper/
COPY coprogo/settings.gradle.kts coprogo/build.gradle.kts coprogo/gradle.properties ./coprogo/
COPY coprogo/build-logic/build.gradle.kts ./coprogo/build-logic/
COPY coprogo/build-logic/src/main/ ./coprogo/build-logic/src/main/
COPY coprogo/pitest-support/build.gradle.kts ./coprogo/pitest-support/

# Resolve the wrapper and compile build logic independently of application code.
# No database, credentials, tests, or jOOQ regeneration are needed to package.
RUN --mount=type=cache,id=coprogo-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew -p coprogo --no-daemon --no-watch-fs --max-workers=2 \
    -Dorg.gradle.jvmargs=-Xmx1g -Pkotlin.compiler.execution.strategy=in-process \
    --build-cache help

COPY coprogo/src/main/ ./coprogo/src/main/
COPY coprogo/src/generated/jooq/ ./coprogo/src/generated/jooq/

RUN --mount=type=cache,id=coprogo-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew -p coprogo --no-daemon --no-watch-fs --max-workers=2 \
    -Dorg.gradle.jvmargs=-Xmx1g -Pkotlin.compiler.execution.strategy=in-process \
    --build-cache classes generateResourcesConfigFile

# Kotlin compilation and native analysis must not retain their heaps together.
# Keep the Gradle coordinator small while the native compiler uses its 6 GiB heap.
# Compatibility codegen avoids requiring the build host's CPU instruction set.
ENV NATIVE_IMAGE_OPTIONS="-J-Xmx6g --parallelism=2 -march=compatibility"

RUN --mount=type=cache,id=coprogo-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew -p coprogo --no-daemon --no-watch-fs --max-workers=2 \
    -Dorg.gradle.jvmargs=-Xmx512m -Pkotlin.compiler.execution.strategy=in-process \
    --build-cache nativeCompile

# glibc, C++ runtime, CA certificates and timezone data, without a shell or JDK.
FROM gcr.io/distroless/cc-debian13:nonroot@sha256:83b9b50586253821cf63ffcec87d3d94265a093ad30da232039af962dd8f4edf AS runtime

WORKDIR /app
COPY --from=build --chown=65532:65532 /workspace/coprogo/build/native/nativeCompile/ ./

ENV MICRONAUT_ENVIRONMENTS=runtime \
    MICRONAUT_SERVER_HOST=0.0.0.0 \
    MICRONAUT_SERVER_PORT=10000

USER 65532:65532
EXPOSE 10000

# Direct execution preserves SIGTERM delivery. Leave headroom for native memory
# and Netty in a 512 MiB container; Docker/Render arguments can override CMD.
ENTRYPOINT ["/app/coprogo"]
CMD ["-Xmx256m", "-XX:MaxDirectMemorySize=64m"]
