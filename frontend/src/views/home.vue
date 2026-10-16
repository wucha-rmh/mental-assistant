<template>
    <div class="home-page">
        <section class="hero">
            <div class="hero-inner">
                <div class="hero-text">
                    <el-tag class="hero-badge" effect="plain" round>心理健康 · 随时在线</el-tag>
                    <h1 class="hero-title">
                        一次温暖的对话，<br />
                        <span class="highlight">化孤独为慰藉</span>
                    </h1>
                    <p class="hero-desc">
                        每个深夜，每个焦虑的时刻，我们都在这里。不必独自承受，让心与心的连接温暖您的每一天。
                    </p>
                    <div class="hero-actions">
                        <el-button type="primary" size="large" round @click="go(consultTarget)">
                            <el-icon><ChatDotRound /></el-icon>
                            <span>开始倾诉，获得陪伴</span>
                        </el-button>
                        <el-button size="large" round @click="go(diaryTarget)">
                            <el-icon><Notebook /></el-icon>
                            <span>记录心情，释放情感</span>
                        </el-button>
                    </div>
                    <p v-if="!isLoggedIn" class="hero-hint">首次使用需要先登录 / 注册，几秒钟就好</p>
                </div>
            </div>
        </section>

        <section class="features">
            <div class="features-inner">
                <div
                    v-for="item in features"
                    :key="item.title"
                    class="feature-card"
                    @click="go(featureTarget(item.to))"
                >
                    <div class="feature-icon">
                        <el-icon :size="22"><component :is="item.icon" /></el-icon>
                    </div>
                    <h3 class="feature-title">{{ item.title }}</h3>
                    <p class="feature-desc">{{ item.desc }}</p>
                </div>
            </div>
        </section>
    </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const isLoggedIn = ref(false)

// 需要登录的入口在未登录时先引导到登录页，避免点进去一路报错
const needLogin = ['/consultation', '/emotion-diary']
const withAuth = (path) => (isLoggedIn.value || !needLogin.includes(path) ? path : '/auth/login')

const consultTarget = computed(() => withAuth('/consultation'))
const diaryTarget = computed(() => withAuth('/emotion-diary'))

const features = [
    {
        title: 'AI 倾诉陪伴',
        desc: '随时说出心里的事，AI 用温和的方式回应你，并给出可执行的小建议。',
        icon: 'ChatDotRound',
        to: '/consultation'
    },
    {
        title: '情绪日记',
        desc: '每天记一笔心情，慢慢看见自己的情绪起伏与触发点。',
        icon: 'Notebook',
        to: '/emotion-diary'
    },
    {
        title: '心理知识库',
        desc: '焦虑、睡眠、人际关系…… 有依据、可自助阅读的科普文章。',
        icon: 'Reading',
        to: '/knowledge'
    }
]

const featureTarget = (path) => withAuth(path)

const go = (path) => {
    router.push(path)
}

onMounted(() => {
    isLoggedIn.value = localStorage.getItem('token') !== null
})
</script>
<style scoped lang="scss">
.home-page {
    padding-bottom: var(--mh-space-8);
}

/* ── Hero：浅雾蓝→薄荷的柔和渐变，深色文字（不再用高饱和青绿 + 白字） ── */
.hero {
    background: linear-gradient(135deg, var(--mh-primary-light-9) 0%, var(--mh-mint-light-9) 100%);
    border-bottom: 1px solid var(--mh-border);
}

.hero-inner {
    max-width: var(--mh-content-max);
    margin: 0 auto;
    padding: 76px var(--mh-space-5);
    display: flex;
    align-items: center;
    justify-content: center;
}

.hero-text {
    max-width: 680px;
    text-align: center;
}

.hero-badge {
    margin-bottom: var(--mh-space-4);
    color: var(--mh-primary);
    border-color: var(--mh-primary-light-5);
    background: rgba(255, 255, 255, 0.7);
}

.hero-title {
    font-size: var(--mh-font-8);
    font-weight: 700;
    line-height: 1.35;
    color: var(--mh-text-1);
    margin-bottom: var(--mh-space-4);
}

.hero-title .highlight {
    color: var(--mh-primary);
}

.hero-desc {
    font-size: var(--mh-font-4);
    line-height: 1.9;
    color: var(--mh-text-2);
}

.hero-actions {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--mh-space-3);
    margin-top: var(--mh-space-6);

    .el-button {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 0 22px;
        height: 46px;
    }
}

.hero-hint {
    margin-top: var(--mh-space-3);
    font-size: var(--mh-font-2);
    color: var(--mh-text-3);
}

/* ── 三个功能入口 ── */
.features {
    padding: var(--mh-space-10) 0 0;
}

.features-inner {
    max-width: var(--mh-content-max);
    margin: 0 auto;
    padding: 0 var(--mh-space-5);
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: var(--mh-space-5);
}

.feature-card {
    background: var(--mh-surface);
    border: 1px solid var(--mh-border);
    border-radius: var(--mh-radius-lg);
    box-shadow: var(--mh-shadow-sm);
    padding: var(--mh-space-6);
    cursor: pointer;
    transition: transform 0.2s ease, box-shadow 0.2s ease;

    &:hover {
        transform: translateY(-3px);
        box-shadow: var(--mh-shadow);
    }

    .feature-icon {
        width: 46px;
        height: 46px;
        border-radius: var(--mh-radius);
        display: flex;
        align-items: center;
        justify-content: center;
        color: var(--mh-primary);
        background: var(--mh-primary-light-9);
        margin-bottom: var(--mh-space-4);
    }

    .feature-title {
        font-size: var(--mh-font-5);
        font-weight: 600;
        color: var(--mh-text-1);
        margin-bottom: var(--mh-space-2);
    }

    .feature-desc {
        font-size: var(--mh-font-3);
        line-height: 1.8;
        color: var(--mh-text-2);
    }
}
</style>
