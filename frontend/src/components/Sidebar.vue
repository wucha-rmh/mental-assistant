<template>
    <el-aside :width="isCollapse ? 'var(--mh-aside-collapsed)' : 'var(--mh-aside-width)'" class="sidebar">
        <div class="brand" :class="{ 'brand--collapsed': isCollapse }">
            <el-image class="brand-logo" :src="iconUrl" alt="心理健康AI助手" fit="cover" />
            <div v-show="!isCollapse" class="info-card">
                <h1 class="brand-title">心理健康AI助手</h1>
                <p class="brand-subtitle">管理后台</p>
            </div>
        </div>
        <!--
            菜单高亮的两个关键点（旧实现写死 default-active="2" 导致**从来没有高亮**）：
            1. index 必须是完整路径，2. default-active 跟当前 route.path 走。
        -->
        <el-menu
            :collapse="isCollapse"
            :collapse-transition="false"
            :default-active="route.path"
            router
            class="menu-style"
        >
            <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
                <el-icon><component :is="item.icon" /></el-icon>
                <template #title>{{ item.title }}</template>
            </el-menu-item>
        </el-menu>
    </el-aside>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'

const route = useRoute()
const router = useRouter()

const iconUrl = new URL('@/assets/images/机器人.png', import.meta.url).href

const isCollapse = computed(() => useAdminStore().isCollapse)

// 菜单直接从路由表生成，避免「路由改了菜单忘了改」；index 用完整路径配合 el-menu 的 router 模式
const menuItems = computed(() => {
    const backRoute = router.options.routes.find((item) => item.path === '/back')
    if (!backRoute || !backRoute.children) {
        return []
    }
    return backRoute.children.map((child) => ({
        path: `${backRoute.path}/${child.path}`,
        title: child.meta?.title || child.path,
        icon: child.meta?.icon || 'Menu'
    }))
})
</script>
<style lang="scss" scoped>
.sidebar {
    background: var(--mh-surface);
    border-right: 1px solid var(--mh-border);
    transition: width 0.2s ease;
    overflow-x: hidden;

    .brand {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: var(--mh-space-3);
        padding: var(--mh-space-4) var(--mh-space-3);
        border-bottom: 1px solid var(--mh-border);

        /* 折叠态：容器只有 68px，logo 必须缩小，否则被裁切（旧版 50px + padding 会溢出） */
        &--collapsed {
            padding: var(--mh-space-3) var(--mh-space-1);
            gap: 0;

            .brand-logo {
                width: 34px;
                height: 34px;
            }
        }

        .brand-logo {
            width: 42px;
            height: 42px;
            border-radius: var(--mh-radius-sm);
            flex-shrink: 0;
        }

        .info-card {
            min-width: 0;

            .brand-title {
                font-size: var(--mh-font-4);
                font-weight: 600;
                color: var(--mh-text-1);
                margin-bottom: 2px;
                white-space: nowrap;
            }

            .brand-subtitle {
                font-size: var(--mh-font-1);
                color: var(--mh-text-3);
                white-space: nowrap;
            }
        }
    }

    .menu-style {
        height: calc(100% - 76px);
        padding-top: var(--mh-space-2);
        overflow-y: auto;
    }
}
</style>
