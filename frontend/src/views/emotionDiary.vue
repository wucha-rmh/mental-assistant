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
            <!-- 情绪评分 -->
            <div class="diary-card">
                <div class="title">今日情绪评分</div>
                <div class="section">
                    <p>您今天的整体情绪状态如何？（1-10 分）</p>
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
            <!-- 主要情绪 -->
            <div class="diary-card">
                <div class="title">主要情绪</div>
                <div class="emotion-grid">
                    <div
                        v-for="emotion in emotionOptions"
                        :key="emotion.name"
                        class="emotion-card"
                        :class="{ 'selected': emotion.name === diaryForm.dominantEmotion }"
                        @click="selectEmotion(emotion.name)"
                    >
                        <el-image :src="emotion.url" class="emotion-image" />
                        <div class="emotion-name">{{ emotion.name }}</div>
                    </div>
                </div>
            </div>
            <!-- 详细记录 -->
            <div class="diary-card">
                <div class="title">详细记录</div>
                <div class="detail-form">
                    <div class="form-group">
                        <div class="form-label">情绪触发因素</div>
                        <el-input v-model="diaryForm.emotionTriggers" placeholder="今天什么事情影响了您的情绪？" type="textarea" :rows="3" maxLength="1000" show-word-limit></el-input>
                    </div>
                    <div class="form-group">
                        <div class="form-label">今日感想</div>
                        <el-input v-model="diaryForm.diaryContent" placeholder="写下您今天的想法、感受或发生的有趣事情..." type="textarea" :rows="5" maxLength="2000" show-word-limit></el-input>
                    </div>
                    <!-- 生活指标 -->
                    <div class="life-indicators">
                        <div class="indicator-group">
                            <div class="form-label">睡眠质量</div>
                            <el-select v-model="diaryForm.sleepQuality" placeholder="请选择">
                                <el-option label="很差" :value="1"></el-option>
                                <el-option label="较差" :value="2"></el-option>
                                <el-option label="一般" :value="3"></el-option>
                                <el-option label="良好" :value="4"></el-option>
                                <el-option label="优秀" :value="5"></el-option>
                            </el-select>
                        </div>
                        <div class="indicator-group">
                            <div class="form-label">压力水平</div>
                            <el-select v-model="diaryForm.stressLevel" placeholder="请选择">
                                <el-option label="很低" :value="1"></el-option>
                                <el-option label="较低" :value="2"></el-option>
                                <el-option label="中等" :value="3"></el-option>
                                <el-option label="较高" :value="4"></el-option>
                                <el-option label="很高" :value="5"></el-option>
                            </el-select>
                        </div>
                    </div>
                    <div class="action-buttons">
                        <el-button @click="resetForm">重置</el-button>
                        <el-button type="primary" @click="submit">提交记录</el-button>
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
                            <el-tag v-if="item.dominantEmotion" effect="light" round>{{ item.dominantEmotion }}</el-tag>
                        </div>
                        <p
                            v-if="item.diaryContent"
                            class="item-content"
                            :class="{ 'is-expanded': expandedIds.includes(item.id) }"
                        >{{ item.diaryContent }}</p>
                        <div class="item-foot">
                            <span v-if="item.emotionTriggers">触发因素：{{ item.emotionTriggers }}</span>
                            <span v-if="item.sleepQuality">睡眠 {{ item.sleepQuality }}/5</span>
                            <span v-if="item.stressLevel">压力 {{ item.stressLevel }}/5</span>
                            <el-button
                                v-if="item.diaryContent && item.diaryContent.length > 80"
                                text
                                type="primary"
                                @click="toggleExpand(item.id)"
                            >{{ expandedIds.includes(item.id) ? '收起' : '展开' }}</el-button>
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
    import { dayjs, ElMessage } from 'element-plus'
    import { onMounted, reactive, ref } from 'vue'
    import { addEmotionDiary, getMyEmotionDiaries } from '@/api/frontend'

    // 情绪评分
    const emotionStatus = ['绝望崩溃', '消沉抑郁', '焦虑烦躁', '低落不悦', '平静淡然', '轻松惬意', '愉悦舒心', '欢欣满足', '兴奋欣喜', '极致幸福']

    // 情绪选项
    const emotionOptions = [
        { name: '开心', url: new URL('@/assets/images/开心.png', import.meta.url).href },
        { name: '平静', url: new URL('@/assets/images/平静.png', import.meta.url).href },
        { name: '焦虑', url: new URL('@/assets/images/焦虑.png', import.meta.url).href },
        { name: '悲伤', url: new URL('@/assets/images/悲伤.png', import.meta.url).href },
        { name: '兴奋', url: new URL('@/assets/images/兴奋.png', import.meta.url).href },
        { name: '疲惫', url: new URL('@/assets/images/疲惫.png', import.meta.url).href },
        { name: '惊讶', url: new URL('@/assets/images/惊讶.png', import.meta.url).href },
        { name: '困惑', url: new URL('@/assets/images/困惑.png', import.meta.url).href },
    ]

    const selectEmotion = (emotion) => {
        diaryForm.dominantEmotion = emotion
    }

    const diaryForm = reactive({
        diaryDate: dayjs().format('YYYY-MM-DD'),
        moodScore: null,
        dominantEmotion: '',
        emotionTriggers: '',
        diaryContent: '',
        sleepQuality: null,
        stressLevel: null
    })

    const resetForm = () => {
        Object.assign(diaryForm, {
            diaryDate: dayjs().format('YYYY-MM-DD'),
            moodScore: null,
            dominantEmotion: '',
            emotionTriggers: '',
            diaryContent: '',
            sleepQuality: null,
            stressLevel: null
        })
    }

    const submit = () => {
        if (!diaryForm.moodScore) {
            ElMessage.error('请选择情绪评分')
            return
        }
        addEmotionDiary(diaryForm).then(() => {
            ElMessage.success('提交成功')
            resetForm()
            // 提交后回到列表第一页，立刻能看到刚才写的那一篇
            listPage.value = 1
            fetchDiaryList()
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
                }
            }
        }

        .section {
            p {
                font-size: var(--mh-font-3);
                color: var(--mh-text-2);
                margin-bottom: var(--mh-space-3);
            }
        }

        .emotion-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: var(--mh-space-4);

            .emotion-card {
                padding: var(--mh-space-4);
                border: 2px solid var(--mh-border);
                border-radius: var(--mh-radius);
                background: var(--mh-surface-soft);
                text-align: center;
                cursor: pointer;
                transition: all 0.2s ease;

                &:hover {
                    border-color: var(--mh-primary-light-5);
                    transform: translateY(-2px);
                }

                .emotion-image {
                    width: 48px;
                    height: 48px;
                }

                /* 旧版这里写死 padding: 0 75px，把文字挤成一条 */
                .emotion-name {
                    margin-top: var(--mh-space-2);
                    font-size: var(--mh-font-3);
                    color: var(--mh-text-2);
                }

                &.selected {
                    border-color: var(--mh-primary);
                    background: var(--mh-primary-light-9);

                    .emotion-name {
                        color: var(--mh-primary);
                        font-weight: 600;
                    }
                }
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

            .life-indicators {
                display: flex;
                gap: var(--mh-space-5);

                .indicator-group {
                    flex: 1;
                }
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
