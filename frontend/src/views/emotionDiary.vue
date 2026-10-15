<template>
    <div class="emotionDiary-container">
        <div class="header-section">
            <div class="header-content">
                <div class="header-icon">
                    <el-image :src="iconUrl" class="header-icon-image" />
                </div>
                <div class="header-text">
                    <h1>情绪日志</h1>
                    <p>把今天的心情写下来，慢慢看见自己的情绪起伏</p>
                </div>
            </div>
        </div>
        <div class="content">
            <!-- 第一步：心情评分 -->
            <div class="diary-card">
                <div class="title">心情评分</div>
                <!-- 该日期已有记录时说明「保存=更新」，避免误以为新增了一条 -->
                <div v-if="existingDiary" class="today-hint">
                    <el-icon><InfoFilled /></el-icon>
                    <span>
                        {{ existingDiary.diaryDate }} 已经记录过一篇了，保存会<strong>更新这一篇</strong>（不会新增）；
                        上次更新于 {{ existingDiary.updatedAt }}
                    </span>
                </div>
                <div v-else class="today-hint is-new">
                    <el-icon><InfoFilled /></el-icon>
                    <span>{{ diaryForm.diaryDate }} 还没有记录，保存后会<strong>新增一篇</strong></span>
                </div>
                <div class="form-group date-group">
                    <div class="form-label">记录日期</div>
                    <el-date-picker
                        v-model="diaryForm.diaryDate"
                        type="date"
                        value-format="YYYY-MM-DD"
                        placeholder="选择日期"
                        :clearable="false"
                        :editable="false"
                        :disabled-date="disableFutureDate"
                        @change="syncExistingDiary"
                    />
                    <span class="date-tip">同一天只保留一篇：想更正以前的记录，直接选那一天即可</span>
                </div>
                <div class="section">
                    <p>这一天的整体情绪状态如何？（1-10 分）</p>
                    <div class="rate">
                        <el-rate
                            v-model="diaryForm.moodScore"
                            :texts="emotionStatus"
                            show-texts
                            :max="10"
                            size="large"
                        />
                    </div>
                </div>
            </div>
            <!-- 第二步：今日感想 -->
            <div class="diary-card">
                <div class="title">今日感想</div>
                <div class="detail-form">
                    <div class="form-group">
                        <el-input
                            v-model="diaryForm.diaryContent"
                            placeholder="写下您今天的想法、感受或发生的有趣事情..."
                            type="textarea"
                            :rows="7"
                            maxLength="2000"
                            show-word-limit
                        ></el-input>
                    </div>
                    <div class="action-buttons">
                        <el-button @click="resetForm">重置</el-button>
                        <el-button type="primary" @click="submit">
                            {{ existingDiary ? '更新这篇记录' : '保存记录' }}
                        </el-button>
                    </div>
                </div>
            </div>
            <!-- 我的记录：只显示当前登录用户自己的日记 -->
            <div class="diary-card">
                <div class="list-header">
                    <div class="title">我的记录</div>
                    <span v-if="listTotal" class="list-count">共 {{ listTotal }} 篇</span>
                </div>
                <div class="diary-list" v-loading="listLoading">
                    <el-empty v-if="!listLoading && diaryList.length === 0" description="还没有记录，写完上面这份就会出现啦" />
                    <div v-for="item in diaryList" :key="item.id" class="diary-item">
                        <div class="item-head">
                            <span class="item-date">{{ item.diaryDate }}</span>
                            <el-rate :model-value="item.moodScore" :max="10" disabled size="small" />
                        </div>
                        <p
                            v-if="item.diaryContent"
                            class="item-content"
                            :class="{ 'is-expanded': expandedIds.includes(item.id) }"
                        >{{ item.diaryContent }}</p>
                        <div class="item-foot">
                            <span v-if="item.updatedAt && item.updatedAt !== item.createdAt">最后更新 {{ item.updatedAt }}</span>
                            <el-button
                                v-if="item.diaryContent && item.diaryContent.length > 80"
                                text
                                type="primary"
                                @click="toggleExpand(item.id)"
                            >{{ expandedIds.includes(item.id) ? '收起' : '展开' }}</el-button>
                            <span class="foot-actions">
                                <el-button text type="primary" @click="startEdit(item)">编辑</el-button>
                                <el-button text type="danger" @click="handleDelete(item)">删除</el-button>
                            </span>
                        </div>
                    </div>
                    <div class="pagination-info" v-if="listTotal > listSize">
                        <el-pagination
                            v-model:current-page="listPage"
                            :page-size="listSize"
                            :total="listTotal"
                            layout="prev, pager, next"
                            @current-change="fetchDiaryList"
                        />
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
<script setup>
    import { dayjs, ElMessage, ElMessageBox } from 'element-plus'
    import { onMounted, reactive, ref } from 'vue'
    import { addEmotionDiary, deleteMyEmotionDiary, getMyEmotionDiaries } from '@/api/frontend'

    // 情绪评分
    const emotionStatus = ['绝望崩溃', '消沉抑郁', '焦虑烦躁', '低落不悦', '平静淡然', '轻松惬意', '愉悦舒心', '欢欣满足', '兴奋欣喜', '极致幸福']

    // 表单只有两项：心情评分 + 今日感想（日期用于定位「哪一天」）
    const diaryForm = reactive({
        diaryDate: dayjs().format('YYYY-MM-DD'),
        moodScore: null,
        diaryContent: ''
    })

    // 当前所选日期已有的记录（null = 该日期还没有记录，保存会新增）
    const existingDiary = ref(null)

    const emptyForm = (date) => ({
        diaryDate: date || dayjs().format('YYYY-MM-DD'),
        moodScore: null,
        diaryContent: ''
    })

    // 把某条记录回填到表单（用于编辑已有日记）
    const fillForm = (item) => {
        Object.assign(diaryForm, {
            diaryDate: item.diaryDate,
            moodScore: item.moodScore,
            diaryContent: item.diaryContent || ''
        })
    }

    // 不允许选未来日期（日记是记录已经发生的心情）
    const disableFutureDate = (date) => date.getTime() > Date.now()

    /**
     * 按「所选日期」查一次后端：
     * 有记录 → 回填表单（等于「在这篇的基础上编辑」）；没有 → 清空表单（等于新增）。
     */
    const syncExistingDiary = () => {
        const date = diaryForm.diaryDate
        return getMyEmotionDiaries({ pageNum: 1, pageSize: 1, diaryDate: date })
            .then(res => {
                const found = (res && res.records && res.records[0]) || null
                existingDiary.value = found
                if (found) {
                    fillForm(found)
                } else {
                    Object.assign(diaryForm, emptyForm(date))
                }
            })
            .catch(() => {
                existingDiary.value = null
            })
    }

    // 重置：该日期有记录就恢复成已保存的内容，否则清空
    const resetForm = () => {
        if (existingDiary.value) {
            fillForm(existingDiary.value)
        } else {
            Object.assign(diaryForm, emptyForm(diaryForm.diaryDate))
        }
    }

    const submit = () => {
        if (!diaryForm.moodScore) {
            ElMessage.error('请选择情绪评分')
            return
        }
        addEmotionDiary(diaryForm).then((saved) => {
            ElMessage.success(existingDiary.value ? '这篇记录已更新' : '记录成功')
            // 后端返回的就是落库后的那条：用它同步「该日期是否已有记录」与表单内容
            if (saved) {
                existingDiary.value = saved
                fillForm(saved)
            }
            // 回到列表第一页，立刻能看到刚才写的这一篇
            listPage.value = 1
            fetchDiaryList()
        })
    }

    // 列表里点「编辑」：把这篇载入表单（可以改任何一天，不只是今天）
    const startEdit = (item) => {
        fillForm(item)
        existingDiary.value = item
        expandedIds.value = expandedIds.value.includes(item.id) ? expandedIds.value : [...expandedIds.value, item.id]
        window.scrollTo({ top: 0, behavior: 'smooth' })
        ElMessage.info(`正在编辑 ${item.diaryDate} 的记录`)
    }

    // 列表里点「删除」
    const handleDelete = (item) => {
        ElMessageBox.confirm(
            `确定删除 ${item.diaryDate} 的这篇日记吗？删除后不可恢复。`,
            '删除确认',
            { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
        ).then(() => {
            deleteMyEmotionDiary(item.id).then(() => {
                ElMessage.success('已删除')
                // 删掉的正是表单当前这一天 → 表单回到「新增」状态
                if (diaryForm.diaryDate === item.diaryDate) {
                    existingDiary.value = null
                    Object.assign(diaryForm, emptyForm(item.diaryDate))
                }
                expandedIds.value = expandedIds.value.filter(id => id !== item.id)
                // 当前页可能被删空，回退一页再刷新
                if (diaryList.value.length === 1 && listPage.value > 1) {
                    listPage.value -= 1
                }
                fetchDiaryList()
            })
        }).catch(() => {
            // 取消删除 / 接口失败（提示已由 request.js 统一处理）
        })
    }

    // ── 我的记录（只查当前登录用户的日记）────────────────────────────
    const diaryList = ref([])
    const listLoading = ref(false)
    const listPage = ref(1)
    const listSize = 5
    const listTotal = ref(0)
    const expandedIds = ref([])

    const fetchDiaryList = () => {
        listLoading.value = true
        getMyEmotionDiaries({ pageNum: listPage.value, pageSize: listSize })
            .then(res => {
                diaryList.value = (res && res.records) || []
                listTotal.value = (res && res.total) || 0
            })
            .catch(() => {
                diaryList.value = []
                listTotal.value = 0
            })
            .finally(() => {
                listLoading.value = false
            })
    }

    const toggleExpand = (id) => {
        expandedIds.value = expandedIds.value.includes(id)
            ? expandedIds.value.filter(item => item !== id)
            : [...expandedIds.value, id]
    }

    onMounted(() => {
        fetchDiaryList()
        // 打开页面先看「今天」有没有记录：有就回填，方便直接编辑今天这篇
        syncExistingDiary()
    })

    const iconUrl = new URL('@/assets/images/like.png', import.meta.url).href
</script>
<style lang="scss" scoped>
.emotionDiary-container {
    background: var(--mh-bg);

    /* 头部与首页 Hero 用同一套柔和渐变 + 深色文字（旧版是黄绿→橙的高饱和渐变） */
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

        /* 图标本身是白色剪影 PNG，必须放在有底色的小圆里才看得见 */
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

    .diary-card {
        margin-bottom: var(--mh-space-5);
        background: var(--mh-surface);
        border: 1px solid var(--mh-border);
        border-radius: var(--mh-radius-lg);
        padding: var(--mh-space-6);
        box-shadow: var(--mh-shadow-sm);

        .title {
            margin-bottom: var(--mh-space-5);
            font-size: var(--mh-font-5);
            font-weight: 600;
            color: var(--mh-text-1);
        }

        /* 我的记录 */
        .list-header {
            display: flex;
            align-items: baseline;
            justify-content: space-between;
            margin-bottom: var(--mh-space-4);

            .title {
                margin-bottom: 0;
            }

            .list-count {
                font-size: var(--mh-font-2);
                color: var(--mh-text-3);
            }
        }

        .diary-list {
            .diary-item {
                padding: var(--mh-space-4);
                margin-bottom: var(--mh-space-3);
                background: var(--mh-surface-soft);
                border: 1px solid var(--mh-border);
                border-radius: var(--mh-radius);

                &:last-child {
                    margin-bottom: 0;
                }

                .item-head {
                    display: flex;
                    align-items: center;
                    flex-wrap: wrap;
                    gap: var(--mh-space-3);

                    .item-date {
                        font-weight: 600;
                        color: var(--mh-text-1);
                    }
                }

                .item-content {
                    margin-top: var(--mh-space-2);
                    font-size: var(--mh-font-3);
                    line-height: 1.8;
                    color: var(--mh-text-2);
                    white-space: pre-wrap;
                    word-break: break-word;
                    display: -webkit-box;
                    -webkit-line-clamp: 3;
                    line-clamp: 3;
                    -webkit-box-orient: vertical;
                    overflow: hidden;

                    &.is-expanded {
                        display: block;
                        overflow: visible;
                    }
                }

                .item-foot {
                    display: flex;
                    align-items: center;
                    flex-wrap: wrap;
                    gap: var(--mh-space-4);
                    margin-top: var(--mh-space-3);
                    font-size: var(--mh-font-2);
                    color: var(--mh-text-3);

                    .foot-actions {
                        display: flex;
                        align-items: center;
                        gap: var(--mh-space-1);
                        margin-left: auto;
                    }
                }
            }
        }

        /* 「今天已经记录过」提示条：明确告知保存＝更新而不是新增 */
        .today-hint {
            display: flex;
            align-items: flex-start;
            gap: var(--mh-space-2);
            padding: var(--mh-space-3) var(--mh-space-4);
            margin-bottom: var(--mh-space-4);
            background: var(--mh-primary-light-9);
            border: 1px solid var(--mh-primary-light-7);
            border-radius: var(--mh-radius);
            font-size: var(--mh-font-2);
            line-height: 1.7;
            color: var(--mh-text-2);

            .el-icon {
                margin-top: 3px;
                color: var(--mh-primary);
                flex-shrink: 0;
            }

            strong {
                color: var(--mh-primary);
            }

            /* 「这一天还没有记录」用薄荷色区分，避免和「已存在」的提示混淆 */
            &.is-new {
                background: var(--mh-mint-light-9);
                border-color: var(--mh-mint-light-7);

                .el-icon,
                strong {
                    color: var(--mh-mint-dark-2);
                }
            }
        }

        .date-group {
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: var(--mh-space-3);
            margin-bottom: var(--mh-space-4);

            .form-label {
                margin-bottom: 0;
            }

            .date-tip {
                font-size: var(--mh-font-1);
                color: var(--mh-text-3);
            }
        }

        .section {
            p {
                font-size: var(--mh-font-3);
                color: var(--mh-text-2);
                margin-bottom: var(--mh-space-3);
            }
        }

        .detail-form {
            .form-group {
                margin-bottom: var(--mh-space-4);
            }

            .form-label {
                margin-bottom: var(--mh-space-2);
                font-size: var(--mh-font-3);
                font-weight: 500;
                color: var(--mh-text-2);
            }

            .action-buttons {
                margin-top: var(--mh-space-6);
                display: flex;
                justify-content: flex-end;
                gap: var(--mh-space-3);
            }
        }
    }
}
</style>
