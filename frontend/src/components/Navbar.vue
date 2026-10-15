<template>
    <div class="navbar">
        <div class="flex-box">
            <el-button @click="handleCollapse">
                <el-icon><Expand /></el-icon>
            </el-button>
            <p class="page-title">{{ route.meta.title }}</p>
        </div>
        <div class="flex-box">
            <el-dropdown @command="handleCommand">
                <div class="flex-box">
                    <el-avatar :src="userStore.avatar" />
                    <p class="user-name">{{ userStore.name }}</p>
                    <el-icon><ArrowDown /></el-icon>
                </div>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item command="avatar">更换头像</el-dropdown-item>
                        <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        </div>
        <AvatarUploader ref="avatarUploader" />
    </div>
</template>
<script setup>
import { ref } from 'vue'
import { useAdminStore } from '@/stores/admin'
import { useUserStore } from '@/stores/user'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { logout } from '@/api/admin'
import AvatarUploader from '@/components/AvatarUploader.vue'

const router = useRouter()
const route = useRoute()

// 当前登录用户（登录时由 login.vue 写进 localStorage.userInfo，store 包了一层响应式）
const userStore = useUserStore()
const avatarUploader = ref(null)

const handleCommand = (command) => {
    if (command === 'avatar') {
        // 打开文件选择框，上传成功后 store 会自己更新
        if (avatarUploader.value) {
            avatarUploader.value.pick()
        }
        return
    }
    if (command === 'logout') {
        // 处理退出登录逻辑
        ElMessageBox.confirm('确定退出登录吗？', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        }).then(() => {
            // 确认退出登录
            logout().then(() => {
                // 清除缓存
                localStorage.removeItem('token')
                userStore.clear()
                // 跳转到登录页
                router.push('/auth/login')
            })
        })
    }
}

const handleCollapse = () => {
    useAdminStore().toggleCollapse()
}
</script>
<style lang="scss" scoped>
.navbar {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 15px;
    background: white;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    border-bottom: 1px solid #e5e7eb;
    .flex-box {
        display: flex;
        align-items: center;
        justify-content: center;
    }
    .page-title {
        margin-left: 20px;
        font-size: 26px;
        font-weight: bold;
        color: #1f2937;
    }
}
</style>
