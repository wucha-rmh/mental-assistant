<template>
    <div class="articleDetail-container">
        <div class="header-section">
            <div class="header-content">
                <div class="header-icon">
                    <el-image :src="iconUrl" class="header-icon-image" />
                </div>
                <div class="header-text">
                    <h1>知识文章</h1>
                    <p>有依据、可自助阅读的心理科普</p>
                </div>
            </div>
        </div>
        <div class="content">
            <div class="article-card">
                <div class="sub-title">
                    <el-tag size="large" class="category-tag">{{ articleDetail.categoryName }}</el-tag>
                    <div class="flex-box">
                        <el-icon><List /></el-icon>
                        <span>{{ dayjs(articleDetail.updatedAt).format('YYYY-MM-DD') }}</span>
                    </div>
                </div>
                <h1 class="article-title">{{ articleDetail.title }}</h1>
                <div class="summary-content" v-if="articleDetail.summary">
                    <p>{{ articleDetail.summary }}</p>
                </div>
                <div class="article-meta">
                    <div class="item flex-box">
                        <el-icon><Avatar /></el-icon>
                        <span>{{ articleDetail.authorName }}</span>
                    </div>
                    <div class="item flex-box">
                        <el-icon><Platform /></el-icon>
                        <span>{{ articleDetail.readCount }} 次阅读</span>
                    </div>
                </div>
            </div>
            <div class="article-card">
                <div class="card-title">正文内容</div>
                <div class="content-wrapper" v-html="formatContent(articleDetail.content)"></div>
                <div class="tags-content" v-if="articleDetail.tagArray && articleDetail.tagArray.length">
                    <h4 class="tags-title">相关标签</h4>
                    <div class="tags-list">
                        <el-tag v-for="tag in articleDetail.tagArray" :key="tag" type="info" effect="light" class="tag-item">{{ tag }}</el-tag>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { getKnowledgeDetail } from '@/api/frontend'
import { dayjs } from 'element-plus'
import { Avatar } from '@element-plus/icons-vue'

const iconUrl = new URL('@/assets/images/book.png', import.meta.url).href

const props = defineProps({
    id: String
})

const articleDetail = ref({})

const formatContent = (content) => {
  if (!content) return ''

  // 基本的HTML清理和格式化
  let formatted = content
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')

  return formatted
}

onMounted(() => {
    getKnowledgeDetail(props.id).then(res => {
        articleDetail.value = res
    })
})
</script>

<style lang="scss" scoped>
.articleDetail-container {
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
            max-width: 980px;
            margin: 0 auto;
            padding: var(--mh-space-8) var(--mh-space-5);
            display: flex;
            align-items: center;
            gap: var(--mh-space-4);
        }

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
        max-width: 980px;
        margin: 0 auto;
        padding: var(--mh-space-6) var(--mh-space-5) var(--mh-space-8);
    }

    .article-card {
        margin-bottom: var(--mh-space-5);
        background: var(--mh-surface);
        border: 1px solid var(--mh-border);
        border-radius: var(--mh-radius-lg);
        padding: var(--mh-space-6);
        box-shadow: var(--mh-shadow-sm);

        .card-title {
            margin-bottom: var(--mh-space-4);
            font-size: var(--mh-font-5);
            font-weight: 600;
            color: var(--mh-text-1);
        }

        .sub-title {
            display: flex;
            align-items: center;
            gap: var(--mh-space-4);
        }

        .article-title {
            margin: var(--mh-space-5) 0 var(--mh-space-4);
            font-size: var(--mh-font-7);
            font-weight: 700;
            line-height: 1.45;
            color: var(--mh-text-1);
        }

        /* 摘要用主题暖色，替换掉旧的荧光绿 */
        .summary-content {
            padding: var(--mh-space-3) var(--mh-space-4);
            background: var(--mh-warm-light-9);
            border-left: 3px solid var(--mh-warm);
            border-radius: 0 var(--mh-radius-sm) var(--mh-radius-sm) 0;

            p {
                font-size: var(--mh-font-3);
                line-height: 1.9;
                color: var(--mh-text-2);
            }
        }

        .article-meta {
            display: flex;
            align-items: center;
            gap: var(--mh-space-5);
            margin-top: var(--mh-space-4);
        }

        /* v-html 内容：字号与行高按阅读体验重排（旧版 h2=15px 比正文还小） */
        .content-wrapper {
            font-size: var(--mh-font-4);
            line-height: 1.9;
            color: var(--mh-text-1);
            word-break: break-word;

            :deep(p) {
                margin-bottom: var(--mh-space-4);
            }

            :deep(h1),
            :deep(h2),
            :deep(h3),
            :deep(h4),
            :deep(h5),
            :deep(h6) {
                margin: var(--mh-space-5) 0 var(--mh-space-3);
                color: var(--mh-text-1);
                font-weight: 600;
            }

            :deep(h2) {
                font-size: var(--mh-font-6);
                padding-bottom: 6px;
                border-bottom: 1px solid var(--mh-border);
            }

            :deep(h3) {
                font-size: var(--mh-font-5);
            }

            :deep(ul),
            :deep(ol) {
                padding-left: var(--mh-space-5);
                margin-bottom: var(--mh-space-3);
            }

            :deep(li) {
                margin-bottom: 6px;
            }

            :deep(img) {
                max-width: 100%;
                border-radius: var(--mh-radius);
            }

            :deep(blockquote) {
                margin: var(--mh-space-4) 0;
                padding: var(--mh-space-3) var(--mh-space-4);
                background: var(--mh-surface-soft);
                border-left: 3px solid var(--mh-primary-light-5);
                border-radius: 0 var(--mh-radius-sm) var(--mh-radius-sm) 0;
                color: var(--mh-text-2);
            }
        }

        .tags-content {
            margin-top: var(--mh-space-5);
            padding-top: var(--mh-space-4);
            border-top: 1px solid var(--mh-border);

            .tags-title {
                margin-bottom: var(--mh-space-3);
                font-size: var(--mh-font-3);
                font-weight: 600;
                color: var(--mh-text-2);
            }

            .tags-list {
                display: flex;
                flex-wrap: wrap;
                gap: var(--mh-space-2);
            }
        }
    }
}
</style>
