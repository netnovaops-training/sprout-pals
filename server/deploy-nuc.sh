#!/usr/bin/env bash
# Deploy (or update) the Sprout Pals friends server on the NUC. Run ON the NUC from this folder:
#     bash deploy-nuc.sh          build the image and (re)start the container
#     bash deploy-nuc.sh undo     stop and remove the container (data volume is kept)
#
# Listens only on the NUC's loopback, 127.0.0.1:8040 - Caddy (host network) reaches it there;
# nothing new is opened on the LAN or the internet. Data: Docker volume sprout_pals_data.
set -euo pipefail
NAME=sprout-pals
IMAGE=sprout-pals/server:latest

if [ "${1:-}" = "undo" ]; then
    docker rm -f "$NAME" 2>/dev/null || true
    echo "Stopped and removed $NAME (volume sprout_pals_data kept)."
    exit 0
fi

docker build -t "$IMAGE" .
docker rm -f "$NAME" 2>/dev/null || true
docker run -d --name "$NAME" --restart unless-stopped \
    -p 127.0.0.1:8040:8020 \
    -v sprout_pals_data:/data \
    --label ai-stack.role="Sprout Pals friends server (garden codes, hearts)" \
    "$IMAGE"

for i in $(seq 1 20); do
    if curl -fsS http://127.0.0.1:8040/api/health >/dev/null 2>&1; then
        echo "sprout-pals is up on 127.0.0.1:8040"
        exit 0
    fi
    sleep 1
done
echo "sprout-pals did not answer on 127.0.0.1:8040 - check: docker logs $NAME" >&2
exit 1
