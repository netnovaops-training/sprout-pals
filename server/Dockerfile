# Sprout Pals friends server. Same approach as the household's other Rust services:
# the toolchain lives in the build stage and is thrown away; the image is one binary on Debian slim.
ARG RUST_VERSION=1.95

# @sha256 pins the exact image, so a changed or hijacked tag can't slip into the build. The digest
# belongs to rust 1.95: raise RUST_VERSION and the digest together (Dependabot's docker updates do).
FROM rust:${RUST_VERSION}-slim-bookworm@sha256:d7482085ff5b415f84dba5647ae71606650bdef00db7aeb69f4b3d170c3e4082 AS builder
WORKDIR /build
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release --locked

FROM debian:bookworm-slim@sha256:7ff6ba31527b9e336162fa061746c366c4b2032f4d2cff2078bea43f78efe3f8
RUN useradd --system --uid 10001 --home-dir /data sprout && mkdir -p /data && chown sprout /data
COPY --from=builder /build/target/release/sprout-server /usr/local/bin/sprout-server
USER sprout
ENV SPROUT_DB=/data/sprout.db PORT=8020 BIND=0.0.0.0
EXPOSE 8020
# the database lives in a volume mounted here, so a new image never loses friends or hearts
VOLUME ["/data"]
CMD ["sprout-server"]
