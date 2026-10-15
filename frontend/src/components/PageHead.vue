<template>
    <div class="page-head">
        <div class="title-box">
            <h2 class="page-title">{{ props.title }}</h2>
            <p v-if="props.description" class="page-desc">{{ props.description }}</p>
        </div>
        <div class="action-btn">
            <slot name="buttons"></slot>
        </div>
    </div>
</template>
<script setup>
const props = defineProps({
    title: {
        type: String,
        default: '页面标题'
    },
    // 可选副标题，用于说明这块是干什么的（后台页面标题重复的问题由此统一）
    description: {
        type: String,
        default: ''
    }
})
</script>
<style lang="scss" scoped>
.page-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--mh-space-4);
    margin-bottom: var(--mh-space-5);

    .title-box {
        min-width: 0;
    }

    .page-title {
        font-size: var(--mh-font-6);
        font-weight: 600;
        color: var(--mh-text-1);
    }

    .page-desc {
        margin-top: 4px;
        font-size: var(--mh-font-2);
        color: var(--mh-text-3);
    }

    .action-btn {
        display: flex;
        align-items: center;
        gap: var(--mh-space-2);
        flex-shrink: 0;
    }
}
</style>
