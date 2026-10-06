# 빌드 단계: 정적 파일(HTML, JS, CSS)을 만든다.
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# 구글 로그인 버튼을 켜려면 빌드할 때 클라이언트 ID를 넣는다. 비어 있으면 버튼이 숨는다.
ARG VITE_GOOGLE_CLIENT_ID=""
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
RUN npm run build

# 실행 단계: Nginx가 정적 파일을 주고 /api는 백엔드로 넘긴다 (ADR 0012).
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
