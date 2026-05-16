FROM node:22-alpine AS build
ARG VITE_API_URL
ARG VITE_COMMIT=dev
ENV VITE_API_URL=$VITE_API_URL
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN node scripts/copy-ffmpeg.mjs && npm run build

FROM nginx:alpine
ARG VITE_API_URL=https://api.heirlooms.digital
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/conf.d/nginx.conf.template
RUN VITE_API_URL="${VITE_API_URL}" envsubst '${VITE_API_URL}' \
    < /etc/nginx/conf.d/nginx.conf.template \
    > /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
