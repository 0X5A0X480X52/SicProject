/**
 * 装配 Vue、Pinia、Router 与 Element Plus 并挂载应用；业务请求经公共客户端访问 Gateway。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import { router } from './router'

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
