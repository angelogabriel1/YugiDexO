FROM node:22-alpine

ENV NODE_ENV=production
WORKDIR /app

COPY package.json package-lock.json ./
COPY server/package.json ./server/package.json
RUN npm ci --omit=dev --workspace server

COPY --chown=node:node server ./server
COPY --chown=node:node neon ./neon

USER node
CMD ["npm", "--workspace", "server", "start"]
