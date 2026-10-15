<template>
    <div class="backend-layout">
        <el-container class="main-container">
            <Sidebar />
            <el-container class="right-container">
                <el-header class="top-header">
                    <Navbar />
                </el-header>
                <el-main class="main-content">
                    <router-view class="content-container"></router-view>
                </el-main>
            </el-container>
        </el-container>
    </div>
</template>
<script setup>
import Sidebar from './Sidebar.vue'
import Navbar from './Navbar.vue'
</script>
<style lang="scss" scoped>
.backend-layout {
    height: 100vh;
    background: var(--mh-bg);

    .main-container {
        height: 100%;
    }

    /* 让内容区可以收缩，避免表格把布局撑出横向滚动 */
    .right-container {
        min-width: 0;
    }

    /* 顶栏：只保留一条底边，阴影与边框不要叠两层（旧版 Navbar 自己又加了一层） */
    .top-header {
        height: var(--mh-header-height) !important;
        padding: 0 !important;
        background: var(--mh-surface);
        border-bottom: 1px solid var(--mh-border);
        box-shadow: none;
    }

    /* 内容区用浅底，让白色卡片浮起来 */
    .main-content {
        padding: var(--mh-space-5);
        background: var(--mh-bg);
        min-height: 0;
    }

    .content-container {
        display: block;
    }
}
</style>
