import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    /**
     * 监听所有网卡，让同一局域网的手机/其他电脑能通过
     * http://<本机内网IP>:5173 访问。
     *
     * Vite 默认只绑 127.0.0.1，外网设备连进来是拒连（不是 502，
     * 别被本机代理软件转出来的假 502 骗了）。
     *
     * 下面 /api 的代理目标是 **服务端**发生的：外网设备的浏览器请求
     * http://<本机IP>:5173/api/v1/...，由跑在这台机器上的 Vite
     * 转给它自己的 localhost:8080，所以代理地址不需要跟着改。
     */
    host: true,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
