/**
 * 登录用户信息的读取工具。
 *
 * 登录成功后 `views/login.vue` 会把后端返回的 userInfo 原样存进 localStorage，
 * 字段见后端 `UserLoginResponseDTO.UserDetailResponseDTO`：
 * username / nickname / avatar / userType / userTypeDisplayName。
 *
 * 这里集中做「安全解析 + 兜底」，避免各组件重复写 localStorage 解析、
 * 也避免回退逻辑各写一套（历史上 Navbar 直接把用户名写死成 admin）。
 */

import { fileBaseUrl } from '@/config/index.js'

/** 没有头像时使用的本地默认头像（frontend/public/img/default-avatar.svg） */
export const DEFAULT_AVATAR = '/img/default-avatar.svg'

/** 取不到任何名字时的兜底显示 */
export const DEFAULT_NAME = '未登录'

/**
 * 读取当前登录用户信息。
 * 任何异常（没有、不是 JSON、不是对象）都返回空对象，绝不抛错。
 * @returns {object}
 */
export function getUserInfo() {
  try {
    const raw = localStorage.getItem('userInfo')
    if (!raw) return {}
    const info = JSON.parse(raw)
    return info && typeof info === 'object' ? info : {}
  } catch (e) {
    return {}
  }
}

/**
 * 显示名：昵称优先，其次用户名，都没有才回退。
 * @param {object} [info] 不传则现读 localStorage
 * @returns {string}
 */
export function displayName(info = getUserInfo()) {
  const nickname = (info?.nickname || '').trim()
  const username = (info?.username || '').trim()
  return nickname || username || DEFAULT_NAME
}

/**
 * 头像地址：库里存的是 `/files/...` 相对路径，前面拼 `fileBaseUrl`（现在是空串，同源）；
 * 没有头像则返回本地默认图。
 * @param {object} [info] 不传则现读 localStorage
 * @returns {string}
 */
export function avatarUrl(info = getUserInfo()) {
  const avatar = (info?.avatar || '').trim()
  return avatar ? fileBaseUrl + avatar : DEFAULT_AVATAR
}
