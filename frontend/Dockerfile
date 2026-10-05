# --- build stage ------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /workspace

COPY package*.json ./
RUN npm ci

COPY . .
# Same-origin API: nginx proxies /api, /oauth2 and /ws to the backend service.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# --- runtime stage ----------------------------------------------------------
FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD ["sh", "-c", "wget -qO- http://localhost/ >/dev/null || exit 1"]

CMD ["nginx", "-g", "daemon off;"]
