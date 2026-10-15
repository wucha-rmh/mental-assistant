<template>
    <div>
        <PageHead title="情绪日志" description="查看用户的情绪日记与 AI 情绪分析结果" />
        <el-card class="mh-card--flush">
            <TableSearch :formItem="formItem" @search="handleSearch" />
            <el-table
                v-loading="loading"
                :data="tableData"
                style="width: 100%"
                empty-text="暂无情绪日记记录"
            >
                <el-table-column label="用户" min-width="180">
                    <template #default="scope">
                        <div class="user-cell">
                            <el-avatar :size="28">{{ avatarText(scope.row.nickname) }}</el-avatar>
                            <span class="user-name">{{ scope.row.nickname || scope.row.username || '未知用户' }}</span>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column prop="userId" label="用户ID" width="100" />
                <el-table-column prop="diaryDate" label="记录日期" width="130" />
                <el-table-column label="情绪评分" width="220">
                    <template #default="scope">
                        <el-rate :model-value="scope.row.moodScore" :max="10" disabled />
                    </template>
                </el-table-column>
                <el-table-column prop="diaryContent" label="今日感想" min-width="260" show-overflow-tooltip />
                <el-table-column label="操作" width="140" fixed="right">
                    <template #default="scope">
                        <el-button @click="viewSessionDetail(scope.row)" text type="primary">详情</el-button>
                        <el-button @click="handleDelete(scope.row)" text type="danger">删除</el-button>
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
            v-model="detailDialogVisible"
            title="情绪日志详情"
            width="760px"
            top="6vh"
            :close-on-click-modal="false"
            :body-style="{ maxHeight: '68vh', overflowY: 'auto' }"
        >
            <div class="detail-content" v-if="currentDetail">
                <div class="detail-section">
                    <h4>用户信息</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="用户名">{{ currentDetail.username }}</el-descriptions-item>
                        <el-descriptions-item label="昵称">{{ currentDetail.nickname }}</el-descriptions-item>
                        <el-descriptions-item label="用户ID">{{ currentDetail.userId }}</el-descriptions-item>
                        <el-descriptions-item label="记录日期">{{ currentDetail.diaryDate }}</el-descriptions-item>
                    </el-descriptions>
                </div>
                <div class="detail-section">
                    <h4>情绪状态</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="情绪评分">
                            <el-rate :model-value="currentDetail.moodScore" :max="10" disabled />
                        </el-descriptions-item>
                    </el-descriptions>
                </div>
                <div class="detail-section">
                    <h4>今日感想</h4>
                    <el-descriptions :column="1" border>
                        <el-descriptions-item label="日记内容">{{ currentDetail.diaryContent || '无'
                            }}</el-descriptions-item>
                    </el-descriptions>
                </div>
                <div class="detail-section">
                    <h4>AI情绪分析结果</h4>
                    <div class="ai-analysis-result">
                        <el-descriptions :column="2" border>
                            <el-descriptions-item label="主要情绪">
                                <el-tag :type="getAiEmotionTagType(aiData.primaryEmotion)">{{
                                    aiData.primaryEmotion}}</el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="情绪强度">
                                <el-progress :percentage="aiData.emotionScore"
                                    :color="getEmotionScoreColor(aiData.emotionScore)" :stroke-width="8" />
                            </el-descriptions-item>
                            <el-descriptions-item label="风险等级">
                                <el-tag :type="getRiskLevelTagType(aiData.riskLevel)">
                                    {{ getRiskLevelText(aiData.riskLevel) }}
                                </el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="情绪性质">
                                <el-tag :type="aiData.isNegative ? 'danger' : 'success'">{{ aiData.isNegative ? '负面情绪' :
                                    '正面情绪'}}</el-tag>
                            </el-descriptions-item>
                        </el-descriptions>
                        <div class="ai-suggestion-section">
                            <h5>专业建议</h5>
                            <div class="suggestion-content">{{ aiData.suggestion || '无' }}</div>
                        </div>
                        <div class="ai-risk-section">
                            <h5>风险描述</h5>
                            <div class="risk-content">{{ aiData.riskDescription || '无' }}</div>
                        </div>
                        <div class="ai-improvements-section">
                            <h5>改善建议</h5>
                            <ul class="improvement-list">
                                <li v-for="item in aiData.improvementSuggestions" :key="item">{{ item }}</li>
                            </ul>
                        </div>
                    </div>

                </div>
                <div class="detail-section">
                    <h4>时间信息</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="创建时间">{{ currentDetail.createdAt }}</el-descriptions-item>
                        <el-descriptions-item label="更新时间">{{ currentDetail.updatedAt }}</el-descriptions-item>
                    </el-descriptions>
                </div>
            </div>
            <template #footer>
                <el-button @click="detailDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'
import { getEmotionalPage, deleteEmotional } from '@/api/admin'
import { ElMessageBox } from 'element-plus'

const getAiEmotionTagType = (emotion) => {
    const emotionTagMap = {
        '快乐': 'success',
        '平静': 'success',
        '兴奋': 'warning',
        '满足': 'success',
        '愤怒': 'danger',
        '悲伤': 'info',
        '焦虑': 'warning',
        '恐惧': 'danger',
        '沮丧': 'info',
        '压力': 'warning'
    }
    return emotionTagMap[emotion] || 'info'
}

const getEmotionScoreColor = (score) => {
    if (score >= 80) return '#f56c6c'
    if (score >= 60) return '#e6a23c'
    if (score >= 40) return '#909399'
    return '#67c23a'
}

const getRiskLevelTagType = (riskLevel) => {
    const riskTagMap = {
        0: 'success',
        1: 'info',
        2: 'warning',
        3: 'danger'
    }
    return riskTagMap[riskLevel] || 'info'
}

const getRiskLevelText = (riskLevel) => {
    const riskTextMap = {
        0: '正常',
        1: '关注',
        2: '预警',
        3: '危机'
    }
    return riskTextMap[riskLevel] || '未知风险等级'
}

const formItem = [
    { comp: 'input', prop: 'userId', label: '用户ID', placeholder: '请输入用户ID' },
    {
        comp: 'select', prop: 'moodScreRange', label: '情绪评分', placeholder: '请选择评分范围', options: [{
            label: '低分（1-3）',
            value: '1-3'
        }, {
            label: '中分（4-6）',
            value: '4-6'
        }, {
            label: '高分（7-10）',
            value: '7-10'
        }]
    }
]


// 列表
const tableData = ref([])
const loading = ref(false)
// 筛选条件（用户ID / 情绪评分范围）：翻页时复用，否则翻到第 2 页筛选就丢了
const searchForm = reactive({})
// 分页参数（后端只认 currentPage/size，旧实现写成 current → 翻页恒返回第一页数据）
const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

const avatarText = (name) => (name ? String(name).slice(0, 1) : '用')

const fetchList = async () => {
    loading.value = true
    try {
        const params = {
            currentPage: pagination.currentPage,
            size: pagination.size,
            ...searchForm
        }
        const { records, total } = await getEmotionalPage(params)
        tableData.value = records || []
        pagination.total = total || 0
    } catch (error) {
        tableData.value = []
        pagination.total = 0
    } finally {
        loading.value = false
    }
}

const handleChange = (page) => {
    pagination.currentPage = page
    fetchList()
}

const handleSearch = async (formData) => {
    if (formData) {
        Object.assign(searchForm, formData)
    }
    pagination.currentPage = 1
    await fetchList()
}

// 详情
const detailDialogVisible = ref(false)
const currentDetail = ref(null)
const aiData = ref(null)
const viewSessionDetail = (row) => {
    currentDetail.value = row
    if (row.aiEmotionAnalysis) {
        try {
            aiData.value = JSON.parse(row.aiEmotionAnalysis)
        } catch (error) {
            // 后端约定 aiEmotionAnalysis 是合法 JSON 字符串或 null，这里兜底避免脏数据把弹窗打断
            aiData.value = {}
        }
    } else {
        aiData.value = {}
    }
    detailDialogVisible.value = true
}

// 删除
const handleDelete = (row) => {
    ElMessageBox.confirm('确定删除该条记录吗？', '删除确认', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'danger'
    }).then(() => {
        // 确认删除
        deleteEmotional(row.id).then(() => {
            fetchList()
        })
    })
}

onMounted(() => {
    fetchList()
})

</script>
<style lang="scss" scoped>
/* 表格里的用户单元格与生活指标 */
.user-cell {
    display: flex;
    align-items: center;
    gap: var(--mh-space-2);

    .user-name {
        color: var(--mh-text-1);
    }
}

.detail-content {
    .detail-section {
        margin-bottom: var(--mh-space-5);

        h4 {
            margin: 0 0 var(--mh-space-3) 0;
            color: var(--mh-text-1);
            font-size: var(--mh-font-4);
        }
    }
}

.ai-analysis-result {

    .ai-keywords-section,
    .ai-suggestion-section,
    .ai-risk-section,
    .ai-improvements-section {
        margin-top: var(--mh-space-4);
        padding: var(--mh-space-3) var(--mh-space-4);
        background-color: var(--mh-surface-soft);
        border-radius: var(--mh-radius-sm);

        h5 {
            margin: 0 0 var(--mh-space-2) 0;
            color: var(--mh-text-2);
            font-size: var(--mh-font-3);
            font-weight: 600;
        }
    }

    .suggestion-content,
    .risk-content {
        line-height: var(--mh-line);
        color: var(--mh-text-2);
        background-color: var(--mh-surface);
        padding: var(--mh-space-3);
        border-radius: var(--mh-radius-sm);
        border: 1px solid var(--mh-border);
    }

    .improvement-list {
        margin: 0;
        padding-left: var(--mh-space-5);

        li {
            margin-bottom: 4px;
            color: var(--mh-text-2);
            line-height: 1.6;
        }
    }

    .el-progress {
        .el-progress__text {
            font-size: var(--mh-font-1) !important;
        }
    }
}
</style>
