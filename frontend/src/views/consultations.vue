<template>
    <div>
        <PageHead title="咨询记录" description="全部用户的 AI 咨询会话，可按会话查看完整对话" />
        <el-card class="mh-card--flush">
            <el-table
                v-loading="loading"
                :data="tableData"
                style="width: 100%"
                empty-text="暂无咨询会话"
            >
                <el-table-column label="用户" width="160">
                    <template #default="scope">
                        <div class="user-cell">
                            <el-avatar :size="28">{{ avatarText(scope.row.userNickname) }}</el-avatar>
                            <span>{{ scope.row.userNickname || '未知用户' }}</span>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column label="会话内容" min-width="320">
                    <template #default="scope">
                        <div class="session-title">{{ scope.row.sessionTitle }}</div>
                        <div class="session-preview">{{ scope.row.lastMessageContent }}</div>
                    </template>
                </el-table-column>
                <el-table-column prop="messageCount" label="消息数" width="100" />
                <el-table-column prop="lastMessageTime" label="最后消息时间" width="190" />
                <el-table-column label="操作" width="100" fixed="right">
                    <template #default="scope">
                        <div class="table-actions">
                            <el-button type="primary" text @click="viewSessionDetail(scope.row)">详情</el-button>
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
        <el-dialog
            v-model="showDetailDialog"
            title="咨询会话详情"
            width="760px"
            top="6vh"
            :close-on-click-modal="false"
        >
            <div class="session-detail">
                <div class="detail-header">
                    <div class="detail-row">
                        <div class="detail-label">用户：</div>
                        <div class="detail-value">{{ sessionDetail.userNickname }}</div>
                    </div>
                    <div class="detail-row">
                        <div class="detail-label">开始时间：</div>
                        <div class="detail-value">{{ sessionDetail.startedAt }}</div>
                    </div>
                    <div class="detail-row">
                        <div class="detail-label">消息数：</div>
                        <div class="detail-value">{{ sessionDetail.messageCount }}</div>
                    </div>
                </div>
                <div class="messages-container">
                    <div class="messages-header">
                        <h4>对话记录</h4>
                    </div>
                    <div class="messages-list" v-loading="loadingMessages">
                        <div v-for="message in sessionMessages" :key="message.id" class="message-item" :class="message.senderType === 1 ? 'user-message' : 'ai-message'">
                            <div class="message-header">
                               <span class="sender">{{ message.senderType === 1 ? '用户' : 'AI助手' }}</span>
                               <span class="time">{{ message.createdAt }}</span>
                            </div>
                            <div class="message-content">{{ message.content }}</div>
                        </div>
                    </div>
                </div>
            </div>
            <template #footer>
                <el-button @click="showDetailDialog = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>
<script setup>
import { onMounted, ref, reactive } from 'vue'
import PageHead from '@/components/PageHead.vue'
import { getConsultationPage, getSessionDetail } from '@/api/admin'

const tableData = ref([])
const loading = ref(false)

const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

const avatarText = (name) => (name ? String(name).slice(0, 1) : '用')

// 会话详情
const sessionDetail = ref({})
const sessionMessages = ref([])
const loadingMessages = ref(false)

const viewSessionDetail = (row) => {
    loadingMessages.value = true
    showDetailDialog.value = true
    getSessionDetail(row.id).then(res => {
        sessionMessages.value = res || []
        sessionDetail.value = row
    }).catch(() => {
        sessionMessages.value = []
    }).finally(() => {
        loadingMessages.value = false
    })
    
}

const handleChange = (page) => {
    pagination.currentPage = page
    handleSearch()
}

const handleSearch = () => {
    // 分页参数只传 currentPage / size（后端同时兼容 pageNum / pageSize）
    loading.value = true
    getConsultationPage({
        currentPage: pagination.currentPage,
        size: pagination.size
    }).then(res => {
        tableData.value = (res && res.records) || []
        pagination.total = (res && res.total) || 0
    }).catch(() => {
        tableData.value = []
        pagination.total = 0
    }).finally(() => {
        loading.value = false
    })
}

// 详情
const showDetailDialog = ref(false)

onMounted(() => {
    handleSearch()
})
</script>

<style lang="scss" scoped>
.user-cell {
    display: flex;
    align-items: center;
    gap: var(--mh-space-2);
}

.session-title {
    font-weight: 500;
    color: var(--mh-text-1);
    margin-bottom: 4px;
}

.session-preview {
    font-size: var(--mh-font-2);
    color: var(--mh-text-3);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.session-detail {
    .detail-header {
        margin-bottom: var(--mh-space-5);
        padding: var(--mh-space-4);
        background: var(--mh-surface-soft);
        border-radius: var(--mh-radius);
        border: 1px solid var(--mh-border);
    }

    .detail-row {
        display: flex;
        align-items: center;
        margin-bottom: var(--mh-space-2);

        &:last-child {
            margin-bottom: 0;
        }

        .detail-label {
            font-weight: 500;
            color: var(--mh-text-3);
            min-width: 80px;
            margin-right: var(--mh-space-2);
        }

        .detail-value {
            color: var(--mh-text-1);
        }
    }
}

.messages-container {
    margin-top: var(--mh-space-5);

    .messages-header {
        margin-bottom: var(--mh-space-3);

        h4 {
            margin: 0;
            color: var(--mh-text-1);
            font-size: var(--mh-font-4);
            font-weight: 600;
        }
    }

    .messages-list {
        max-height: 46vh;
        overflow-y: auto;
        border: 1px solid var(--mh-border);
        border-radius: var(--mh-radius);
        padding: var(--mh-space-4);
        background: var(--mh-surface-soft);

        .message-item {
            margin-bottom: var(--mh-space-3);
            padding: var(--mh-space-3) var(--mh-space-4);
            border-radius: var(--mh-radius);
            background: var(--mh-surface);
            border: 1px solid var(--mh-border);

            &:last-child {
                margin-bottom: 0;
            }

            &.user-message {
                background: var(--mh-primary-light-9);
                border-color: var(--mh-primary-light-7);
            }

            &.ai-message {
                background: var(--mh-mint-light-9);
                border-color: var(--mh-mint-light-7);
            }
        }
    }
}

/* ⚠️ 模板里 .message-content 与 .message-header 是兄弟节点，
   旧实现把它嵌在 .message-header 里 → white-space:pre-wrap 从来不生效，多行消息被折平 */
.message-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;

    .sender {
        font-weight: 600;
        color: var(--mh-text-2);
    }

    .time {
        font-size: var(--mh-font-1);
        color: var(--mh-text-3);
    }
}

.message-content {
    color: var(--mh-text-1);
    line-height: var(--mh-line);
    white-space: pre-wrap;
    word-break: break-word;
    font-size: var(--mh-font-3);
}
</style>