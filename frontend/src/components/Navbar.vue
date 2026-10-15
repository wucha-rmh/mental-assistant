<template>
    <div class="navbar">
        <div class="left-box">
            <el-button class="collapse-btn" text @click="handleCollapse">
                <el-icon :size="18"><component :is="isCollapse ? 'Expand' : 'Fold'" /></el-icon>
            </el-button>
            <span class="page-title">{{ route.meta.title || '管理后台' }}</span>
        </div>
        <div class="right-box">
            <el-dropdown @command="handleCommand">
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
        </div>
        <AvatarUploader ref="avatarUploader" />
    </div>
</template>
<script setup>
import { ref, computed } from 'vue'
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

const isCollapse = computed(() => useAdminStore().isCollapse)

const handleCommand = (command) => {
    if (command === 'avatar') {
        // 打开文件选择框，上传成功后 store 会自己更新
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
            logout().then(() => {
                localStorage.removeItem('token')
                userStore.clear()
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
/* 顶栏本身的白底/底边/高度由 BackendLayout 的 .top-header 统一控制，
   这里只负责内容排布，不要再叠一层阴影（旧版就是两层阴影 + 两条线） */
.navbar {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 var(--mh-space-5);

    .left-box,
    .right-box {
        display: flex;
        align-items: center;
        gap: var(--mh-space-3);
    }

    .collapse-btn {
        color: var(--mh-text-2);

        &:hover {
            color: var(--mh-primary);
            background: var(--mh-primary-light-9);
        }
    }

    .page-title {
        font-size: var(--mh-font-5);
        font-weight: 600;
        color: var(--mh-text-1);
    }

    .user-box {
        display: flex;
        align-items: center;
        gap: var(--mh-space-2);
        padding: 4px 10px 4px 4px;
        border-radius: var(--mh-radius-pill);
        cursor: pointer;
        outline: none;
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
</style>
