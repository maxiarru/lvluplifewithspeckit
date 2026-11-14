FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Only use the directory emitted by scripts/prepare-vercel-trial.ps1 as context.
COPY . .
RUN chmod +x gradlew \
    && ./gradlew :domain:test :server:test :server:installDist --no-daemon --console=plain

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN groupadd --gid 10001 lvlup \
    && useradd --uid 10001 --gid lvlup --no-create-home --shell /usr/sbin/nologin lvlup
WORKDIR /opt/lvlup
COPY --from=build --chown=10001:10001 /workspace/server/build/install/server/lib/ ./lib/
USER 10001:10001
ENV PORT=8081 \
    LVLUP_ENV=production \
    LVLUP_BIND_HOST=0.0.0.0
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-cp", "lib/*", "lvluplife.api.ServerKt"]
