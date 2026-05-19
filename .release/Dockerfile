FROM node:22-bookworm-slim

RUN apt-get update && apt-get install -y \
    wget \
    gnupg \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN npx playwright install --with-deps chromium

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY src/ ./src/

ENTRYPOINT ["node", "src/spigot-release.js"]
