<template>
    <div>
        <PageHead title="用户管理" description="平台注册用户一览；禁用后该账号立即无法登录，已登录的会话同时失效" />
        <el-card class="mh-card--flush">
            <div class="filter-bar">
                <el-input
                    v-model="filters.keyword"
                    class="filter-keyword"
                    placeholder="用户名 / 昵称 / 邮箱"
                    clearable
                    @keyup.enter="handleSearch"
                    @clear="handleSearch"
                />
                <el-select
                    v-model="filters.status"
                    class="filter-select"
                    placeholder="全部状态"
                    clearable
                    @change="handleSearch"
                >
                    <el-option label="正常" :value="1" />
                    <el-option label="禁用" :value="0" />
                </el-select>
                <el-select
                    v-model="filters.userType"
                    class="filter-select"
                    placeholder="全部类型"
                    clearable
                    @change="handleSearch"
                >
                    <el-option label="普通用户" :value="1" />
                    <el-option label="管理员" :value="2" />
                </el-select>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button @click="resetFilters">重置</el-button>
            </div>

            <el-table
                v-loading="loading"
                :data="tableData"
                style="width: 100%"
                empty-text="暂无用户"
            >
                <el-table-column prop="id" label="ID" width="70" />
                <el-table-column label="用户" min-width="200">
                    <template #default="scope">
                        <div class="user-cell">
                            <el-avatar :size="30" :src="scope.row.avatar || ''">
                                {{ avatarText(scope.row.displayName) }}
                            </el-avatar>
                            <div class="user-meta">
                                <div class="user-name">{{ scope.row.nickname || scope.row.username }}</div>
                                <div class="user-account">@{{ scope.row.username }}</div>
                            </div>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
                <el-table-column label="手机号" width="130">
                    <template #default="scope">{{ scope.row.phone || '-' }}</template>
                </el-table-column>
                <el-table-column label="类型" width="110">
                    <template #default="scope">
                        <el-tag :type="scope.row.userType === 2 ? 'warning' : 'info'" effect="plain">
                            {{ scope.row.userTypeDisplayName }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                    <template #default="scope">
                        <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" effect="plain">
                            {{ scope.row.statusDisplayName }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="注册时间" width="170" />
                <el-table-column label="操作" width="110" fixed="right">
                    <template #default="scope">
                        <el-button
                            v-if="scope.row.status === 1"
                            type="danger"
                            text
                            :disabled="scope.row.id === currentUserId"
                            @click="changeStatus(scope.row, 0)"
                        >
                            禁用
                        </el-button>
                        <el-button v-else type="success" text @click="changeStatus(scope.row, 1)">
                            启用
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>

            <div class="pagination-info">
                <el-pagination
                    v-model:current-page="pagination.currentPage"
                    :page-size="pagination.size"
                    :total="pagination.total"
                    layout="total, prev, pager, next, jumper"
                    @current-change="handleChange"
                />
            </div>
        </el-card>
    </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import { getUserPage, updateUserStatus } from '@/api/admin'

const tableData = ref([])
const loading = ref(false)

// 当前登录管理员：用于禁用「禁用自己」（后端也会再挡一次）
const currentUserId = ref(null)
try {
    currentUserId.value = JSON.parse(localStorage.getItem('userInfo') || '{}').id ?? null
} catch (e) {
    currentUserId.value = null
}

const filters = reactive({
    keyword: '',
    status: null,
    userType: null
})

const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

const avatarText = (name) => (name ? String(name).slice(0, 1) : '用')

const loadUsers = () => {
    loading.value = true
    // 分页参数只传 currentPage / size；status 为 0 也要发出去（?? 而不是 ||）
    getUserPage({
        currentPage: pagination.currentPage,
        size: pagination.size,
        keyword: filters.keyword || undefined,
        status: filters.status ?? undefined,
        userType: filters.userType ?? undefined
    }).then((res) => {
        tableData.value = (res && res.records) || []
        pagination.total = (res && res.total) || 0
    }).catch(() => {
        tableData.value = []
        pagination.total = 0
    }).finally(() => {
        loading.value = false
    })
}

const handleSearch = () => {
    pagination.currentPage = 1
    loadUsers()
}

const handleChange = (page) => {
    pagination.currentPage = page
    loadUsers()
}

const resetFilters = () => {
    filters.keyword = ''
    filters.status = null
    filters.userType = null
    handleSearch()
}

const changeStatus = (row, status) => {
    const label = row.nickname || row.username
    const action = status === 0 ? '禁用' : '启用'
    const tip = status === 0
        ? `确定禁用「${label}」吗？该账号将立即无法登录，已登录的会话同时失效。`
        : `确定启用「${label}」吗？启用后该账号可以正常登录。`

    ElMessageBox.confirm(tip, `${action}用户`, {
        type: 'warning',
        confirmButtonText: `确定${action}`,
        cancelButtonText: '取消'
    }).then(() => {
        return updateUserStatus(row.id, { status })
    }).then((res) => {
        // 后端返回更新后的用户，就地替换这一行，避免整页重新请求
        const index = tableData.value.findIndex((item) => item.id === row.id)
        if (index > -1 && res) {
            tableData.value[index] = res
        }
        ElMessage.success(`${action}成功`)
    }).catch(() => {
        // 取消确认，或请求失败（失败已由请求拦截器提示）
    })
}

onMounted(() => {
    loadUsers()
})
</script>

<style lang="scss" scoped>
.filter-bar {
    display: flex;
    flex-wrap: wrap;
    gap: var(--mh-space-3);
    margin-bottom: var(--mh-space-4);

    .filter-keyword {
        width: 240px;
    }

    .filter-select {
        width: 140px;
    }
}

.user-cell {
    display: flex;
    align-items: center;
    gap: var(--mh-space-3);
}

.user-meta {
    min-width: 0;

    .user-name {
        color: var(--mh-text-1);
        font-weight: 500;
    }

    .user-account {
        font-size: var(--mh-font-1);
        color: var(--mh-text-3);
    }
}
</style>
