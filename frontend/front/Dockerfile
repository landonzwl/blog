# ===== 第一阶段：打包前端 =====
# 构建上下文是仓库根目录，这样排班页面可以引用 NP/src。
# glibc 镜像。Alpine 上 npm 经常漏装 Rollup / lightningcss 的 musl 原生包，构建会在 npm run build 退出。
FROM node:20-bookworm-slim AS build
WORKDIR /src
COPY frontend/front/package.json frontend/front/package-lock.json ./frontend/front/
WORKDIR /src/frontend/front
RUN npm ci
WORKDIR /src
COPY frontend/front ./frontend/front
COPY NP/src ./NP/src
WORKDIR /src/frontend/front
RUN NODE_OPTIONS=--max-old-space-size=768 npm run build

# ===== 第二阶段：Nginx 托管 + 反代 /api =====
FROM nginx:stable-alpine
COPY frontend/front/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/frontend/front/dist /usr/share/nginx/html
EXPOSE 80
