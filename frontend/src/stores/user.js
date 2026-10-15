import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { getUserInfo, displayName, avatarUrl } from '@/utils/user'

/**
 * 当前登录用户。
 *
 * 唯一数据源仍是 localStorage.userInfo（登录成功后由 views/login.vue 写入），
 * 这里把它包成响应式状态：换头像后 store 一改，导航栏/头部的头像与昵称立刻跟着变，
 * 不需要刷新页面。
 *
 * 约定：任何写入 localStorage 的地方都要同步调用这里的 action（setInfo / setAvatar / clear）。
 */
export const useUserStore = defineStore('user', () => {
    /** 用户信息，字段同后端 UserLoginResponseDTO.UserDetailResponseDTO */
    const info = ref(getUserInfo())

    /** 是否已登录（有用户信息即视为已登录） */
    const isLoggedIn = computed(() => Object.keys(info.value).length > 0)

    /** 显示名：昵称优先，其次用户名，都没有则「未登录」 */
    const name = computed(() => displayName(info.value))

    /** 头像地址：/files/... 前面拼 fileBaseUrl（现在是空串，同源），没有则本地默认图 */
    const avatar = computed(() => avatarUrl(info.value))

    /** 覆盖整个用户信息（登录成功、换头像成功时调用） */
    const setInfo = (next) => {
        const value = next && typeof next === 'object' ? next : {}
        info.value = value
        if (Object.keys(value).length > 0) {
            localStorage.setItem('userInfo', JSON.stringify(value))
        }
    }

    /** 只更新头像路径 */
    const setAvatar = (path) => {
        setInfo({ ...info.value, avatar: path || null })
    }

    /** 退出登录时清空 */
    const clear = () => {
        info.value = {}
        localStorage.removeItem('userInfo')
    }

    return { info, isLoggedIn, name, avatar, setInfo, setAvatar, clear }
})
