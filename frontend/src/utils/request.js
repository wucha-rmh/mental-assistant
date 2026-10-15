import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建axios实例
const service = axios.create({
  baseURL: '/api', // 请求的前缀
  timeout: 15000, // 请求的超时时间
})

/** 后端统一响应体的成功码（字符串 "200"，不是数字） */
const SUCCESS_CODE = '200'

/**
 * 需要重新登录的业务码：
 * 401 未登录、A0230 token 无效/过期/黑名单、A0231 token 被禁用、A0300/A0301 访问权限异常
 */
const UNAUTHORIZED_CODES = ['401', 'A0230', 'A0231', 'A0300', 'A0301']

/** 登录页路径，已在该页时不重复跳转 */
const LOGIN_PATH = '/auth/login'

/** 构造带业务码的 Error，调用方可用 err.code 区分失败原因 */
function buildError(msg, code) {
  const error = new Error(msg)
  error.code = code
  return error
}

/** 清除本地登录态并跳转登录页 */
function toLogin(msg) {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  ElMessage.error(msg || '登录已过期，请重新登录')
  if (window.location.pathname !== LOGIN_PATH) {
    window.location.href = LOGIN_PATH
  }
}

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 在发送请求之前做些什么
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['token'] = token
    }
    return config
  },
  (error) => {
    // 对请求错误做些什么
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    const { data } = response
    // 非统一响应体（文件流、SSE 等）原样返回
    if (!data || typeof data !== 'object' || data.code === undefined) {
      return data
    }
    // 成功：只把业务数据交给调用方，业务代码不再剥壳
    if (String(data.code) === SUCCESS_CODE) {
      return data.data
    }
    const code = String(data.code)
    // 后端统一响应体的提示字段是 msg（不是 message）
    const msg = data.msg || '操作失败'
    if (UNAUTHORIZED_CODES.includes(code)) {
      toLogin(msg)
    } else {
      ElMessage.error(msg)
    }
    // 统一以 reject 结束：调用方拿不到 data.data 时能走 catch，而不是把整个响应体当数据用
    return Promise.reject(buildError(msg, code))
  },
  (error) => {
    // 鉴权失败时后端用 HTTP 401/403 返回统一响应体（见 ResponseUtil）
    const { response } = error
    if (response && (response.status === 401 || response.status === 403)) {
      const msg = (response.data && response.data.msg) || '登录已过期，请重新登录'
      toLogin(msg)
      return Promise.reject(buildError(msg, String(response.status)))
    }
    let msg = '网络异常，请稍后重试'
    if (error.code === 'ECONNABORTED') {
      msg = '请求超时，请稍后重试'
    } else if (response && response.data && response.data.msg) {
      msg = response.data.msg
    } else if (response) {
      msg = `请求失败（HTTP ${response.status}）`
    }
    ElMessage.error(msg)
    return Promise.reject(buildError(msg, response ? String(response.status) : error.code))
  }
)

export default service
