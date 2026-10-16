<template>
    <div>
        <PageHead title="知识文章" description="维护心理知识文章：支持草稿、发布、下线与删除">
            <template #buttons>
                <el-button @click="handleEdit({})" type="primary">
                    <el-icon><Plus /></el-icon>
                    <span>新增文章</span>
                </el-button>
            </template>
        </PageHead>
        <el-card class="mh-card--flush">
            <TableSearch :formItem="formItem" @search="handleSearch" />
            <el-table
                v-loading="loading"
                :data="tableData"
                style="width: 100%"
                empty-text="暂无文章，点右上角「新增文章」开始"
            >
                <el-table-column prop="title" label="文章标题" min-width="220" fixed="left" show-overflow-tooltip />
                <el-table-column label="分类" width="140">
                    <template #default="scope">
                        <span>{{ categoryMap[scope.row.categoryId] || '—' }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="authorName" label="作者" width="120" />
                <el-table-column prop="readCount" label="阅读量" width="100" />
                <el-table-column label="状态" width="110">
                    <template #default="scope">
                        <el-tag v-if="scope.row.status === 1" type="success">已发布</el-tag>
                        <el-tag v-else-if="scope.row.status === 2" type="warning">已下线</el-tag>
                        <el-tag v-else type="info">草稿</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="updatedAt" label="更新时间" width="180" />
                <el-table-column label="操作" width="220" fixed="right">
                    <template #default="scope">
                        <div class="table-actions">
                            <el-button @click="handleEdit(scope.row)" text type="primary">编辑</el-button>
                            <el-button
                                v-if="scope.row.status === 0 || scope.row.status === 2"
                                @click="handlePublish(scope.row)"
                                text
                                type="success"
                            >发布</el-button>
                            <el-button
                                v-if="scope.row.status === 1"
                                @click="handleUnpublish(scope.row)"
                                text
                                type="warning"
                            >下线</el-button>
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
        <ArticleDialog v-model="dialogVisible" :article="currentArticle" :categories="categories" @success="handleSuccess" />
    </div>
</template>
<script setup>
import { onMounted, ref, reactive } from 'vue'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'
import { categoryTree, articlePage, getArticleDetail, changeArticleStatus, deleteArticle } from '@/api/admin'
import ArticleDialog from '@/components/ArticleDialog.vue'
import { ElMessageBox, ElMessage } from 'element-plus'

// 必须用 reactive：分类选项是 onMounted 里异步取回来再赋给 formItem[1].options 的，
// 普通数组不是响应式的，TableSearch 的 computed 不会重算 → 下拉框里只剩它自带的「全部」
const formItem = reactive([
    { comp: 'input', prop: 'title', label: '文章标题', placeholder: '请输入文章标题' },
    { comp: 'select', prop: 'categoryId', label: '分类', placeholder: '请选择分类' },
    { comp: 'select', prop: 'status', label: '状态', placeholder: '请选择状态', options: [
        { label: '草稿', value: '0' },
        { label: '已发布', value: '1' },
        { label: '已下线', value: '2' }
    ] }
])

// 分页参数（后端兼容 currentPage/size 与 pageNum/pageSize）
const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

const loading = ref(false)
// 保存当前筛选条件：翻页时复用，否则翻到第 2 页筛选就丢了（旧实现的 bug）
const searchForm = reactive({})

const fetchList = async () => {
    loading.value = true
    try {
        const params = {
            currentPage: pagination.currentPage,
            size: pagination.size,
            ...searchForm
        }
        const { records, total } = await articlePage(params)
        tableData.value = records || []
        pagination.total = total || 0
    } catch (error) {
        tableData.value = []
        pagination.total = 0
    } finally {
        loading.value = false
    }
}

const handleSearch = async (formData) => {
    // TableSearch 传出的是筛选条件副本；翻页调用时不传参数，沿用上一次的条件
    if (formData) {
        Object.assign(searchForm, formData)
    }
    pagination.currentPage = 1
    await fetchList()
}

const handleChange = (page) => {
    pagination.currentPage = page
    fetchList()
}

// 分类映射
const categoryMap = reactive({})
// 分类列表
const categories = ref([])

// 列表数据
const tableData = ref([])

// 新增和编辑
const dialogVisible = ref(false)
const currentArticle = ref(null)
const handleSuccess = () => {
    dialogVisible.value = false
    fetchList()
}
const handleEdit = (row) => {
    if (!row.id) {
        // 新增
        currentArticle.value = null
        dialogVisible.value = true
    } else {
        // 编辑
        getArticleDetail(row.id).then(res => {
            currentArticle.value = res
            dialogVisible.value = true
        })
    }
}

// 发布
const handlePublish = (row) => {
    ElMessageBox.confirm(
        `确认发布文章${row.title}吗？`,
        '确认',
        {
            confirmButtonText: '确认发布',
            cancelButtonText: '取消',
            type: 'info'
        }
    ).then(() => {
        changeArticleStatus(row.id, { status: 1 }).then(() => {
            ElMessage.success('发布成功')
            fetchList()
        })
    })
}

const handleUnpublish = (row) => {
    ElMessageBox.confirm(
        `确认下线文章${row.title}吗？`,
        '确认',
        {
            confirmButtonText: '确认下线',
            cancelButtonText: '取消',
            type: 'warning'
        }
    ).then(() => {
        changeArticleStatus(row.id, { status: 2 }).then(() => {
            ElMessage.success('下线成功')
            fetchList()
        })
    })
}

const handleDelete = (row) => {
    ElMessageBox.confirm(
        `确认删除文章${row.title}吗？`,
        '确认',
        {
            confirmButtonText: '确认删除',
            cancelButtonText: '取消',
            type: 'danger'
        }
    ).then(() => {
        deleteArticle(row.id).then(() => {
            ElMessage.success('删除成功')
            fetchList()
        })
    })
}

onMounted(async () => {
    const data = await categoryTree()

    categories.value = data.map(item => {
        categoryMap[item.id] = item.categoryName
        return {
            label: item.categoryName,
            value: item.id
        }
    })
    formItem[1].options = categories.value

    fetchList()
})
</script>
