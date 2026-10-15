<template>
    <div class="frontend-layout">
        <div class="navbar-container">
            <div class="brand-section">
                <el-image style="width: 50px; height: 50px" :src="iconUrl" alt="品牌logo" class="brand-logo" />
                <h1 class="brand-name">心理健康AI助手</h1>
            </div>
            <div class="nav-section">
                <router-link to="/" class="nav-link">首页</router-link>
                <router-link to="/consultation" class="nav-link" v-if="isLoggedIn">AI咨询</router-link>
                <router-link to="/emotion-diary" class="nav-link" v-if="isLoggedIn">情绪日记</router-link>
                <router-link to="/knowledge" class="nav-link">知识库</router-link>
                <el-dropdown v-if="isLoggedIn" class="user-dropdown" @command="handleCommand">
                    <div class="user-box">
                        <el-avatar :size="32" :src="userStore.avatar" />
                        <span class="user-name">{{ userStore.name }}</span>
                        <el-icon><ArrowDown /></el-icon>
                    </div>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <el-dropdown-item command="avatar">更换头像</el-dropdown-item>
                            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
                <template v-else>
                    <router-link to="/auth/login" class="nav-link">登录</router-link>
                    <router-link to="/auth/register" class="nav-link">
                        <el-button type="primary">注册</el-button>
                    </router-link>
                </template>
            </div>
        </div>
        <AvatarUploader ref="avatarUploader" />
        <div class="main-content">
            <router-view></router-view>
        </div>
        <div class="footer-container">
            <div class="footer-bottom">
                <p>&copy; 2026 心理健康AI助手. All rights reserved.</p>
            </div>
        </div>
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
.frontend-layout {
    background-color: #fff;

    .navbar-container {
        max-width: 1200px;
        height: 100%;
        margin: 0 auto;
        padding: 10px;
        display: flex;
        align-items: center;
        justify-content: space-between;

        .brand-section {
            display: flex;
            align-items: center;

            .brand-name {
                margin-left: 10px;
                font-size: 24px;
                font-weight: 600;
                color: #333;
            }
        }

        .nav-section {
            display: flex;
            align-items: center;
            gap: 40px;

            .nav-link {
                color: #4b5563;
                font-size: 16px;
                font-weight: 500;

                &:hover {
                    color: #4A90E2;
                }
            }

            .user-dropdown {
                cursor: pointer;
                outline: none;

                .user-box {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    color: #4b5563;
                    font-size: 16px;
                    font-weight: 500;

                    &:hover {
                        color: #4A90E2;
                    }
                }
            }
        }
    }

    .footer-container {
        background: #1f2937;
        color: white;
        padding: 15px 0;
        margin-top: auto;

        .footer-bottom {
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 10px;
            text-align: center;
        }
    }
}
</style>
