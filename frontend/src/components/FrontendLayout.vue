<template>
    <div class="frontend-layout">
        <header class="site-header">
            <div class="header-inner">
                <router-link to="/" class="brand-section">
                    <el-image class="brand-logo" :src="iconUrl" alt="心理健康AI助手" fit="cover" />
                    <div class="brand-text">
                        <h1 class="brand-name">心理健康AI助手</h1>
                        <p class="brand-slogan">陪你走过每一个情绪起伏</p>
                    </div>
                </router-link>
                <nav class="nav-section">
                    <router-link to="/" class="nav-link">首页</router-link>
                    <router-link to="/knowledge" class="nav-link">知识库</router-link>
                    <router-link to="/consultation" class="nav-link" v-if="isLoggedIn">AI 咨询</router-link>
                    <router-link to="/emotion-diary" class="nav-link" v-if="isLoggedIn">情绪日记</router-link>
                    <el-dropdown v-if="isLoggedIn" class="user-dropdown" @command="handleCommand">
                        <div class="user-box">
                            <el-avatar :size="30" :src="userStore.avatar" />
                            <span class="user-name">{{ userStore.name }}</span>
                            <el-icon class="arrow"><ArrowDown /></el-icon>
                        </div>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <el-dropdown-item command="avatar">更换头像</el-dropdown-item>
                                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                    <template v-else>
                        <router-link to="/auth/login" class="nav-link">登录</router-link>
                        <router-link to="/auth/register">
                            <el-button type="primary" round>免费注册</el-button>
                        </router-link>
                    </template>
                </nav>
            </div>
        </header>
        <AvatarUploader ref="avatarUploader" />
        <main class="main-content">
            <router-view></router-view>
        </main>
        <footer class="footer-container">
            <div class="footer-inner">
                <p>心理健康AI助手 · 让你的情绪被看见、被接住</p>
                <p class="copyright">&copy; 2026 心理健康AI助手. All rights reserved.</p>
            </div>
        </footer>
    </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { logout } from '@/api/admin'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import AvatarUploader from '@/components/AvatarUploader.vue'

const router = useRouter()
// 当前登录用户（登录时由 login.vue 写进 localStorage.userInfo）
const userStore = useUserStore()
const avatarUploader = ref(null)

const iconUrl = new URL('@/assets/images/机器人.png', import.meta.url).href

const isLoggedIn = ref(false)

// 登出
const handleLogout = () => {
    logout().then(() => {
        // 清除缓存
        localStorage.removeItem('token')
        userStore.clear()
        // 跳转到登录页
        router.push('/auth/login')
    })
}

// 头像菜单
const handleCommand = (command) => {
    if (command === 'avatar') {
        if (avatarUploader.value) {
            avatarUploader.value.pick()
        }
        return
    }
    if (command === 'logout') {
        ElMessageBox.confirm('确定退出登录吗？', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        }).then(() => {
            handleLogout()
        })
    }
}

onMounted(() => {
   isLoggedIn.value = localStorage.getItem('token') !== null
})
</script>
<style scoped lang="scss">
/* 整页纵向布局，保证 footer 永远在底部（旧实现没有 flex 容器，footer 的 margin-top:auto 是无效的） */
.frontend-layout {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
    background: var(--mh-bg);
}

.site-header {
    position: sticky;
    top: 0;
    z-index: 100;
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(8px);
    border-bottom: 1px solid var(--mh-border);
}

.header-inner {
    max-width: var(--mh-content-max);
    height: 68px;
    margin: 0 auto;
    padding: 0 var(--mh-space-5);
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--mh-space-6);

    .brand-section {
        display: flex;
        align-items: center;
        gap: var(--mh-space-3);
        min-width: 0;

        .brand-logo {
            width: 42px;
            height: 42px;
            border-radius: var(--mh-radius-sm);
            flex-shrink: 0;
        }

        .brand-text {
            min-width: 0;
        }

        .brand-name {
            font-size: var(--mh-font-4);
            font-weight: 600;
            color: var(--mh-text-1);
            line-height: 1.2;
        }

        .brand-slogan {
            font-size: var(--mh-font-1);
            color: var(--mh-text-3);
            line-height: 1.4;
        }
    }

    .nav-section {
        display: flex;
        align-items: center;
        gap: var(--mh-space-5);

        .nav-link {
            position: relative;
            color: var(--mh-text-2);
            font-size: var(--mh-font-3);
            font-weight: 500;
            padding: 6px 2px;
            transition: color 0.18s ease;

            &:hover {
                color: var(--mh-primary);
            }

            /* 当前所在页高亮（旧实现没有选中态） */
            &.router-link-exact-active,
            &.router-link-active {
                color: var(--mh-primary);
                font-weight: 600;
            }

            &.router-link-exact-active::after,
            &.router-link-active::after {
                content: '';
                position: absolute;
                left: 0;
                right: 0;
                bottom: -2px;
                height: 2px;
                border-radius: 2px;
                background: var(--mh-primary);
            }
        }

        .user-dropdown {
            cursor: pointer;
            outline: none;

            .user-box {
                display: flex;
                align-items: center;
                gap: var(--mh-space-2);
                padding: 4px 10px 4px 4px;
                border-radius: var(--mh-radius-pill);
                transition: background 0.18s ease;

                &:hover {
                    background: var(--mh-primary-light-9);
                }

                .user-name {
                    font-size: var(--mh-font-2);
                    font-weight: 500;
                    color: var(--mh-text-2);
                }

                .arrow {
                    color: var(--mh-text-3);
                }
            }
        }
    }
}

.main-content {
    flex: 1;
    min-height: 0;
}

.footer-container {
    margin-top: var(--mh-space-10);
    background: var(--mh-surface);
    border-top: 1px solid var(--mh-border);

    .footer-inner {
        max-width: var(--mh-content-max);
        margin: 0 auto;
        padding: var(--mh-space-6) var(--mh-space-5);
        text-align: center;
        color: var(--mh-text-3);
        font-size: var(--mh-font-2);

        .copyright {
            margin-top: 4px;
            color: var(--mh-text-3);
            opacity: 0.8;
        }
    }
}
</style>
