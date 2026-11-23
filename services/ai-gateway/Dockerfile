FROM node:22-alpine

ENV NODE_ENV=production
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

# Copy production gateway files only (tests excluded via .dockerignore and explicit list).
COPY access.mjs billing.mjs billing.sql gateway-http.mjs jobs.mjs jobs.sql lifecycle.mjs \
     provider.mjs quota.sql server.mjs shared-quota.mjs story.mjs story.sql usage.mjs \
     usage.sql web-search.mjs worker.mjs ./

USER node
EXPOSE 8080

# فحص الجاهزية وليس مجرد الحياة: يفشل إذا تعذّر الوصول إلى قاعدة الحصص.
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/readyz >/dev/null || exit 1

CMD ["node", "server.mjs"]
