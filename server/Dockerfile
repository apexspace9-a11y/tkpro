FROM node:24-alpine
WORKDIR /app
COPY package.json server.mjs ./
ENV PORT=8080
EXPOSE 8080
CMD ["node", "server.mjs"]
