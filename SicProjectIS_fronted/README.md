# Vue 3 + TypeScript + Vite

本项目是 SicProject 的业务前端，启动、环境配置和完整流程说明见 [Cloud README](../SicProjectIS_cloud/README.md)。核心代码注释采用 [统一规范](../SicProjectIS_cloud/docs/code-commenting-guide.md)，具体范围及豁免文件见 [覆盖清单](../SicProjectIS_cloud/docs/code-commenting-coverage.md)。

开发时浏览器访问 `http://127.0.0.1:5173`，请求 `/api` 由 Vite 代理至 Gateway，默认地址为 `http://127.0.0.1:8080`。Cloud 单实例和多实例启动示例均默认使用 8080，无需配置前端代理。仅在后端自行修改 `GATEWAY_PORT`，例如改为 18080 时，在本前端目录的 `.env.development.local` 中设置：

```dotenv
SIC_GATEWAY_PROXY_TARGET=http://127.0.0.1:18080
```

然后重新运行 `npm run dev`。也可在当前 PowerShell 会话中设置 `$env:SIC_GATEWAY_PROXY_TARGET = 'http://127.0.0.1:18080'`，进程环境变量优先于本地文件。参考 [.env.example](.env.example)；`.env.development.local` 不提交 Git。切换回 8080 后端时同步修改或移除此本地设置。请求封装和 SSE 保持同源 `/api`，无需改为浏览器直接访问跨域 Gateway。

This template should help get you started developing with Vue 3 and TypeScript in Vite. The template uses Vue 3 `<script setup>` SFCs, check out the [script setup docs](https://v3.vuejs.org/api/sfc-script-setup.html#sfc-script-setup) to learn more.

Learn more about the recommended Project Setup and IDE Support in the [Vue Docs TypeScript Guide](https://vuejs.org/guide/typescript/overview.html#project-setup).
