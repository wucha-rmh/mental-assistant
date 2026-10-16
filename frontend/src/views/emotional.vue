<template>
    <div>
        <PageHead title="情绪日志" description="查看用户的情绪日记" />
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
                        <el-rate class="mood-rate" :model-value="scope.row.moodScore" :max="10" disabled />
                    </template>
                </el-table-column>
                <el-table-column prop="diaryContent" label="今日感想" min-width="260" show-overflow-tooltip />
                <el-table-column label="操作" width="140" fixed="right">
                    <template #default="scope">
                        <div class="table-actions">
                            <el-button @click="viewSessionDetail(scope.row)" text type="primary">详情</el-button>
                            <el-button @click="handleDelete(scope.row)" text type="danger">删除</el-button>
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
const viewSessionDetail = (row) => {
    currentDetail.value = row
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
/* 表格里的用户单元格 */
.user-cell {
    display: flex;
    align-items: center;
    gap: var(--mh-space-2);

    .user-name {
        color: var(--mh-text-1);
    }
}

/* 情绪评分：10 颗星按默认尺寸（18px 图标 + 6px 间距）要 240px，会溢出 220px 的列宽 */
.mood-rate {
    --el-rate-icon-size: 15px;
    --el-rate-icon-margin: 3px;
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
</style>
