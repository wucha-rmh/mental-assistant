<template>
  <div class="knowledge-container">
    <div class="header-section">
        <div class="header-content">
            <div class="header-icon">
                <el-image :src="iconUrl" class="header-icon-image" />
            </div>
            <div class="header-text">
                <h1>知识库</h1>
                <p>焦虑、睡眠、人际关系…… 挑一篇读读，也许正好对上你现在的状态</p>
            </div>
        </div>
    </div>
    <div class="content">
        <!-- 左侧推荐 -->
        <aside class="recommend-section">
            <div class="section-title">
                <el-icon><Histogram /></el-icon>
                <span>推荐阅读</span>
            </div>
            <div class="recommend-list">
                <p v-if="recommendList.length === 0" class="empty-text">暂无推荐文章</p>
                <div v-for="item in recommendList" :key="item.id" class="recommend-item" @click="goToArticle(item.id)">
                    <h4>{{ item.title }}</h4>
                    <p class="read-count">
                        <el-icon><Histogram /></el-icon>
                        <span>阅读量 {{ item.readCount }}</span>
                    </p>
                </div>
            </div>
        </aside>
        <!-- 右侧列表 -->
        <div class="article-list" v-loading="loading">
            <el-empty v-if="!loading && articleList.length === 0" description="暂无文章" />
            <div v-for="item in articleList" :key="item.id" class="article-item" @click="goToArticle(item.id)">
                <el-image class="cover" fit="cover" :src="getImage(item.coverImage)"></el-image>
                <div class="info">
                    <div class="title-row">
                        <h3>{{ item.title }}</h3>
                        <el-tag plain type="primary">{{ item.categoryName }}</el-tag>
                    </div>
                    <div class="meta">
                        <div class="flex-box">
                            <el-icon><Avatar /></el-icon>
                            <span>{{ item.authorName }}</span>
                        </div>
                        <div class="flex-box">
                            <el-icon><List /></el-icon>
                            <span>{{ dayjs(item.updatedAt).format('YYYY-MM-DD') }}</span>
                        </div>
                        <div class="flex-box">
                            <el-icon><Platform /></el-icon>
                            <span>阅读量 {{ item.readCount }}</span>
                        </div>
                    </div>
                </div>
            </div>
            <div class="pagination-info">
                <el-pagination
                    v-model:current-page="pagination.currentPage"
                    :page-size="pagination.size"
                    :total="pagination.total"
                    layout="total, prev, pager, next"
                    @current-change="handleChange"
                />
            </div>
        </div>
    </div>
  </div>
</template>
<script setup>
    import { dayjs, ElMessage } from 'element-plus'
    import { ref, reactive, onMounted } from 'vue'
    import { getKnowledgeList } from '@/api/frontend'
    import { fileBaseUrl } from '@/config/index.js'
    import { useRouter } from 'vue-router'

    const router = useRouter()

    import iconUrl from '@/assets/images/book.png'
    import { Platform } from '@element-plus/icons-vue'

    // 推荐阅读列表
    const recommendList = ref([])
    const loading = ref(false)

    // 右侧列表数据
    const pagination = reactive({
        currentPage: 1,
        size: 10,
        total: 0
    })

    const articleList = ref([])
    // 获取列表数据
    const getPageList = () => {
        loading.value = true
        const params = {
            sortField: 'publishedAt',
            sortDirection: 'desc',
            currentPage: pagination.currentPage,
            size: pagination.size
        }
        getKnowledgeList(params).then(res => {
            articleList.value = res.records || []
            pagination.total = res.total || 0
        }).catch(() => {
            articleList.value = []
            pagination.total = 0
        }).finally(() => {
            loading.value = false
        })
    }
    // 获取封面图片（本地资源，走同源 /files，不再依赖外部图床）
    const getImage = (url) => {
        return url ? fileBaseUrl + url : '/img/cover-placeholder.svg'
    }

    const handleChange = (page) => {
        pagination.currentPage = page
        getPageList()
    }

    // 跳转到详情
    const goToArticle = (id) => {
        router.push(`/knowledge/article/${id}`)
    }

    onMounted(() => {
        // 获取推荐阅读列表（按阅读量倒序）
        const params = {
            sortField: 'readCount',
            sortDirection: 'desc',
            currentPage: 1,
            size: 5
        }
        getPageList()
        getKnowledgeList(params).then(res => {
            recommendList.value = res.records || []
        }).catch(() => {
            recommendList.value = []
        })
    })
</script>
<style lang="scss" scoped>
.knowledge-container {
    background: var(--mh-bg);

    .flex-box {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: var(--mh-font-2);
        color: var(--mh-text-3);
    }

    .header-section {
        background: linear-gradient(135deg, var(--mh-primary-light-9) 0%, var(--mh-mint-light-9) 100%);
        border-bottom: 1px solid var(--mh-border);

        .header-content {
            max-width: var(--mh-content-max);
            margin: 0 auto;
            padding: var(--mh-space-8) var(--mh-space-5);
            display: flex;
            align-items: center;
            gap: var(--mh-space-4);
        }

        /* 图标是白色剪影 PNG，需要有底色的小圆 */
        .header-icon {
            width: 56px;
            height: 56px;
            border-radius: 50%;
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            background: linear-gradient(135deg, var(--mh-primary-light-3) 0%, var(--mh-primary) 100%);
            box-shadow: var(--mh-shadow-primary);

            .header-icon-image {
                width: 26px;
                height: 26px;
            }
        }

        .header-text {
            h1 {
                font-size: var(--mh-font-7);
                font-weight: 700;
                color: var(--mh-text-1);
                margin-bottom: 4px;
            }

            p {
                font-size: var(--mh-font-3);
                color: var(--mh-text-2);
            }
        }
    }

    .content {
        max-width: var(--mh-content-max);
        margin: 0 auto;
        padding: var(--mh-space-6) var(--mh-space-5) var(--mh-space-8);
        display: flex;
        align-items: flex-start;
        gap: var(--mh-space-5);
    }

    .recommend-section {
        width: 280px;
        flex-shrink: 0;
        position: sticky;
        top: calc(var(--mh-header-height) + var(--mh-space-4));
        background: var(--mh-surface);
        border: 1px solid var(--mh-border);
        border-radius: var(--mh-radius-lg);
        box-shadow: var(--mh-shadow-sm);
        padding: var(--mh-space-5);

        .section-title {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: var(--mh-font-4);
            font-weight: 600;
            color: var(--mh-text-1);
            margin-bottom: var(--mh-space-4);
        }

        .empty-text {
            font-size: var(--mh-font-2);
            color: var(--mh-text-3);
            text-align: center;
            padding: var(--mh-space-4) 0;
        }

        .recommend-list {
            display: flex;
            flex-direction: column;
            gap: var(--mh-space-4);

            .recommend-item {
                border-left: 3px solid var(--mh-primary-light-5);
                padding-left: var(--mh-space-3);
                cursor: pointer;
                transition: border-color 0.2s ease;

                &:hover {
                    border-left-color: var(--mh-primary);

                    h4 {
                        color: var(--mh-primary);
                    }
                }

                h4 {
                    font-size: var(--mh-font-3);
                    font-weight: 500;
                    line-height: 1.6;
                    color: var(--mh-text-1);
                    transition: color 0.2s ease;
                }

                .read-count {
                    margin-top: 6px;
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    font-size: var(--mh-font-1);
                    color: var(--mh-text-3);
                }
            }
        }
    }

    .article-list {
        flex: 1;
        min-width: 0;

        .article-item {
            display: flex;
            gap: var(--mh-space-5);
            padding: var(--mh-space-4);
            margin-bottom: var(--mh-space-5);
            background: var(--mh-surface);
            border: 1px solid var(--mh-border);
            border-radius: var(--mh-radius-lg);
            box-shadow: var(--mh-shadow-sm);
            cursor: pointer;
            transition: transform 0.2s ease, box-shadow 0.2s ease;

            &:hover {
                transform: translateY(-2px);
                box-shadow: var(--mh-shadow);
            }

            .cover {
                width: 220px;
                height: 138px;
                border-radius: var(--mh-radius);
                flex-shrink: 0;
                overflow: hidden;
                background: var(--mh-surface-soft);
            }

            .info {
                flex: 1;
                min-width: 0;
                display: flex;
                flex-direction: column;
                justify-content: space-between;

                .title-row {
                    display: flex;
                    align-items: flex-start;
                    gap: var(--mh-space-3);

                    h3 {
                        flex: 1;
                        min-width: 0;
                        font-size: var(--mh-font-5);
                        font-weight: 600;
                        color: var(--mh-text-1);
                        line-height: 1.5;
                    }
                }

                .meta {
                    display: flex;
                    flex-wrap: wrap;
                    align-items: center;
                    gap: var(--mh-space-5);
                }
            }
        }
    }
}
</style>
