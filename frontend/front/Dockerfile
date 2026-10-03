# ===== 第一阶段：在 Node 环境里打包前端 =====
FROM node:20-alpine AS build
WORKDIR /app

# 先只复制依赖清单，利用 Docker 缓存层加速重复构建
COPY package*.json ./
RUN npm ci

# 复制全部源码并执行打包
COPY . .
RUN npm run build

# ===== 第二阶段：把打包结果放进 Nginx =====
FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
