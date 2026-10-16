<template>
    <div>
        <PageHead title="留言管理" description="用户给管理员的留言；回复后用户可在「个人中心 → 我的留言」看到" />
        <el-card class="mh-card--flush">
            <div class="filter-bar">
                <el-select
                    v-model="filters.status"
                    class="filter-select"
                    placeholder="全部状态"
                    clearable
                    @change="handleSearch"
                >
                    <el-option label="待处理" :value="0" />
                    <el-option label="已回复" :value="1" />
                </el-select>
                <el-select
                    v-model="filters.feedbackType"
                    class="filter-select filter-select--wide"
                    placeholder="全部类型"
                    clearable
                    @change="handleSearch"
                >
                    <el-option
                        v-for="type in FEEDBACK_TYPES"
                        :key="type.value"
                        :label="type.label"
                        :value="type.value"
                    />
                </el-select>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button @click="resetFilters">重置</el-button>
            </div>

            <el-table
                v-loading="loading"
                :data="tableData"
                style="width: 100%"
                empty-text="暂无留言"
            >
                <el-table-column prop="id" label="ID" width="70" />
                <el-table-column label="留言人" min-width="170">
                    <template #default="scope">
                        <div class="user-meta">
                            <div class="user-name">
                                {{ scope.row.nickname || scope.row.guestName || scope.row.username || '匿名' }}
                            </div>
                            <div class="user-account">
                                <template v-if="scope.row.userId">@{{ scope.row.username }}</template>
                                <el-tag v-else size="small" type="info" effect="plain">未登录留言</el-tag>
                            </div>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column label="类型" width="150">
                    <template #default="scope">
                        <el-tag size="small" effect="plain">{{ scope.row.feedbackTypeName }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="留言内容" min-width="260" show-overflow-tooltip>
                    <template #default="scope">{{ scope.row.content }}</template>
                </el-table-column>
                <el-table-column label="联系方式" width="150">
                    <template #default="scope">{{ scope.row.contact || '-' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                    <template #default="scope">
                        <el-tag size="small" effect="plain" :type="scope.row.status === 1 ? 'success' : 'warning'">
                            {{ scope.row.statusName }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="留言时间" width="170" />
                <el-table-column label="操作" width="150" fixed="right">
                    <template #default="scope">
                        <div class="table-actions">
                            <el-button type="primary" text @click="openReply(scope.row)">
                                {{ scope.row.status === 1 ? '修改回复' : '回复' }}
                            </el-button>
                            <el-button type="danger" text @click="handleDelete(scope.row)">删除</el-button>
                        </div>
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

        <!-- 回复弹窗 -->
        <el-dialog v-model="replyDialogVisible" title="回复留言" width="620px" top="8vh" :close-on-click-modal="false">
            <div v-if="currentRow" class="original-box">
                <div class="original-head">
                    <span class="original-from">
                        {{ currentRow.nickname || currentRow.guestName || currentRow.username || '匿名' }}
                    </span>
                    <el-tag size="small" effect="plain">{{ currentRow.feedbackTypeName }}</el-tag>
                    <span class="original-time">{{ currentRow.createdAt }}</span>
                </div>
                <div class="original-content">{{ currentRow.content }}</div>
                <div v-if="currentRow.contact" class="original-contact">联系方式：{{ currentRow.contact }}</div>
            </div>

            <el-form ref="replyFormRef" :model="replyForm" :rules="replyRules" label-width="80px">
                <el-form-item label="回复内容" prop="reply">
                    <el-input
                        v-model="replyForm.reply"
                        type="textarea"
                        :rows="4"
                        maxlength="1000"
                        show-word-limit
                        placeholder="回复后用户可在「个人中心 → 我的留言」看到"
                    />
                </el-form-item>
            </el-form>

            <template #footer>
                <el-button @click="replyDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="replyLoading" @click="submitReply(replyFormRef)">
                    提交回复
                </el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import { getFeedbackPage, replyFeedback, deleteFeedback } from '@/api/admin'

const FEEDBACK_TYPES = [
    { value: 1, label: '忘记密码/无法登录' },
    { value: 2, label: '账号问题' },
    { value: 3, label: '功能建议' },
    { value: 4, label: '其它' }
]

const tableData = ref([])
const loading = ref(false)

// 筛选条件：状态与类型都由后端过滤（留言量大时也不会有漏）
const filters = reactive({
    status: null,
    feedbackType: null
})

const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

const loadFeedbacks = () => {
    loading.value = true
    getFeedbackPage({
        currentPage: pagination.currentPage,
        size: pagination.size,
        status: filters.status ?? undefined,
        feedbackType: filters.feedbackType ?? undefined
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
    loadFeedbacks()
}

const handleChange = (page) => {
    pagination.currentPage = page
    loadFeedbacks()
}

const resetFilters = () => {
    filters.status = null
    filters.feedbackType = null
    handleSearch()
}

/* ---------------- 回复 ---------------- */
const replyDialogVisible = ref(false)
const replyFormRef = ref(null)
const replyLoading = ref(false)
const currentRow = ref(null)

const replyForm = reactive({
    reply: ''
})

const replyRules = reactive({
    reply: [{ required: true, message: '请输入回复内容', trigger: 'blur' }]
})

const openReply = (row) => {
    currentRow.value = row
    replyForm.reply = row.adminReply || ''
    replyDialogVisible.value = true
}

const submitReply = (formEl) => {
    if (!formEl || !currentRow.value) return
    formEl.validate((valid) => {
        if (!valid) return
        replyLoading.value = true
        replyFeedback(currentRow.value.id, { reply: replyForm.reply }).then((res) => {
            const index = tableData.value.findIndex((item) => item.id === currentRow.value.id)
            if (index > -1 && res) {
                tableData.value[index] = res
            }
            ElMessage.success('回复成功')
            replyDialogVisible.value = false
        }).catch(() => {
            // 失败提示已由 request.js 统一处理
        }).finally(() => {
            replyLoading.value = false
        })
    })
}

/* ---------------- 删除 ---------------- */
const handleDelete = (row) => {
    const label = row.nickname || row.guestName || row.username || '匿名'
    ElMessageBox.confirm(`确定删除「${label}」的这条留言吗？删除后不可恢复。`, '删除留言', {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消'
    }).then(() => {
        return deleteFeedback(row.id)
    }).then(() => {
        ElMessage.success('删除成功')
        // 删除后当前页可能空了，回退一页
        if (tableData.value.length === 1 && pagination.currentPage > 1) {
            pagination.currentPage -= 1
        }
        loadFeedbacks()
    }).catch(() => {
        // 取消确认，或请求失败（失败已由请求拦截器提示）
    })
}

onMounted(() => {
    loadFeedbacks()
})
</script>

<style lang="scss" scoped>
.filter-bar {
    display: flex;
    flex-wrap: wrap;
    gap: var(--mh-space-3);
    margin-bottom: var(--mh-space-4);

    .filter-select {
        width: 140px;
    }

    .filter-select--wide {
        width: 200px;
    }
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

.original-box {
    margin-bottom: var(--mh-space-4);
    padding: var(--mh-space-3) var(--mh-space-4);
    background: var(--mh-bg);
    border-radius: var(--mh-radius-sm);
}

.original-head {
    display: flex;
    align-items: center;
    gap: var(--mh-space-2);
}

.original-from {
    font-weight: 500;
    color: var(--mh-text-1);
}

.original-time {
    margin-left: auto;
    font-size: var(--mh-font-1);
    color: var(--mh-text-3);
}

.original-content {
    margin-top: var(--mh-space-2);
    color: var(--mh-text-2);
    white-space: pre-wrap;
    word-break: break-word;
}

.original-contact {
    margin-top: var(--mh-space-2);
    font-size: var(--mh-font-1);
    color: var(--mh-text-3);
}
</style>
