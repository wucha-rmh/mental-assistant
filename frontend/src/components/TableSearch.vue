<template>
    <el-form ref="ruleFormRef" :model="formData" class="table-search" @submit.prevent>
        <el-row :gutter="16">
            <el-col v-for="item in formItems" :key="item.prop" v-bind="item.col">
                <el-form-item :label="item.label" :prop="item.prop">
                    <component
                        :is="isComp(item.comp)"
                        v-model="formData[item.prop]"
                        :placeholder="item.placeholder || `请选择${item.label || ''}`"
                        clearable
                        @keyup.enter="handleSearch"
                    >
                        <template v-if="item.comp === 'select'">
                            <el-option label="全部" value="" />
                            <el-option
                                v-for="opt in item.options"
                                :key="opt.value"
                                :label="opt.label"
                                :value="opt.value"/>
                        </template>
                    </component>
                </el-form-item>
            </el-col>
            <el-col :span="24" class="search-actions">
                <el-button type="primary" @click="handleSearch">
                    <el-icon><Search /></el-icon>
                    <span>查询</span>
                </el-button>
                <el-button @click="handleReset(ruleFormRef)">重置</el-button>
            </el-col>
        </el-row>
    </el-form>
</template>
<script setup>
import { ref, reactive, computed, watch } from 'vue'

const props = defineProps({
    formItem: {
        type: Array,
        default: () => []
    }
})
const emit = defineEmits(['search'])

// 不在 props 上直接挂 col（旧实现在 computed 里改 props 对象，属于反模式）
const formItems = computed(() =>
    (props.formItem || []).map((item) => ({
        ...item,
        col: item.col || { xs: 24, sm: 12, md: 8, lg: 6, xl: 6 }
    }))
)

const ruleFormRef = ref()
const formData = reactive({})

// 依据配置补全表单字段，重置/绑定才有 key
watch(
    () => props.formItem,
    (items) => {
        ;(items || []).forEach((item) => {
            if (item.prop && !(item.prop in formData)) {
                formData[item.prop] = ''
            }
        })
    },
    { immediate: true }
)

const isComp = (comp) => {
    return {
        input: 'elInput',
        select: 'elSelect'
    }[comp]
}

// 传出副本，避免父组件拿到后又被这里的 v-model 改掉
const handleSearch = () => {
    emit('search', { ...formData })
}

const handleReset = (formEl) => {
    if (!formEl) return
    formEl.resetFields()
    emit('search', { ...formData })
}
</script>
<style lang="scss" scoped>
.table-search {
    padding: var(--mh-space-4) var(--mh-space-5) 0;
    border-bottom: 1px solid var(--mh-border);

    .search-actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--mh-space-2);
        margin-bottom: var(--mh-space-4);
    }
}
</style>
