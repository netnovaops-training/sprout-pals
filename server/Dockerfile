# Sprout Pals friends server. Same approach as the household's other Rust services:
# the toolchain lives in the build stage and is thrown away; the image is one binary on Debian slim.
ARG RUST_VERSION=1.98.1

# @sha256 pins the exact image, so a changed or hijacked tag can't slip into the build. The digest
# belongs to rust 1.98.1: raise RUST_VERSION and the digest together (Dependabot's docker updates do).
FROM rust:${RUST_VERSION}-slim-trixie@sha256:4cd829461bd5c4d511c32e269da9cb8929223b666519d8004e35fc8d1d771ab7 AS builder
WORKDIR /build
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release --locked

FROM debian:trixie-slim@sha256:62bd423aeee912639ca8ee681c2b762cb3e25137e0a92b20be455898a9f034ad
RUN useradd --system --uid 10001 --home-dir /data sprout && mkdir -p /data && chown sprout /data
COPY --from=builder /build/target/release/sprout-server /usr/local/bin/sprout-server
USER sprout
ENV SPROUT_DB=/data/sprout.db PORT=8020 BIND=0.0.0.0
EXPOSE 8020
# the database lives in a volume mounted here, so a new image never loses friends or hearts
VOLUME ["/data"]
CMD ["sprout-server"]
