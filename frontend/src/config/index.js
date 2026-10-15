/**
 * 文件访问前缀。
 *
 * 留空 = 同源访问：
 *   - 开发环境：Vite 把 /files 代理到后端 1236（见 vite.config.js）
 *   - 生产环境：由后端或反向代理提供 /files/**
 *
 * 上传的文件全部落在项目内的 backend/uploads，数据库里存的是相对路径
 * （形如 /files/bussiness/article/xxx.png），不再依赖任何外部文件服务器/图床。
 */
export const fileBaseUrl = ''
