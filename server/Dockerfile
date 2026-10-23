# Sprout Pals friends server. Same approach as the household's other Rust services:
# the toolchain lives in the build stage and is thrown away; the image is one binary on Debian slim.
ARG RUST_VERSION=1.95

FROM rust:${RUST_VERSION}-slim-bookworm AS builder
WORKDIR /build
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release --locked

FROM debian:bookworm-slim
RUN useradd --system --uid 10001 --home-dir /data sprout && mkdir -p /data && chown sprout /data
COPY --from=builder /build/target/release/sprout-server /usr/local/bin/sprout-server
USER sprout
ENV SPROUT_DB=/data/sprout.db PORT=8020 BIND=0.0.0.0
EXPOSE 8020
# the database lives in a volume mounted here, so a new image never loses friends or hearts
VOLUME ["/data"]
CMD ["sprout-server"]
