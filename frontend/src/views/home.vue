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
                <!-- 呼吸球：点击开始 / 结束引导式呼吸练习 -->
                <div class="hero-art">
                    <button
                        type="button"
                        class="breath-ball"
                        :class="{ 'is-active': breathing }"
                        :style="ballStyle"
                        :aria-pressed="breathing ? 'true' : 'false'"
                        :aria-label="breathing ? '结束呼吸练习' : '开始呼吸练习'"
                        @click="toggleBreathing"
                    >
                        <span class="ball-halo" aria-hidden="true"></span>
                        <svg class="ball-ring" viewBox="0 0 100 100" aria-hidden="true">
                            <circle class="ring-track" cx="50" cy="50" r="46" />
                            <circle class="ring-progress" cx="50" cy="50" r="46" :style="{ strokeDashoffset: ringOffset }" />
                        </svg>
                        <span class="ball-core">
                            <template v-if="breathing">
                                <span class="ball-phase">{{ phase.text }}</span>
                                <span class="ball-count">{{ remaining }}</span>
                            </template>
                            <template v-else>
                                <span class="ball-face">
                                    <img class="ball-image" :src="iconUrl" alt="AI 心理助手" />
                                </span>
                                <span class="ball-label">呼吸练习</span>
                            </template>
                        </span>
                    </button>
                    <p class="ball-hint">
                        <template v-if="breathing">第 {{ cycles }} 轮 · {{ phase.tip }} · 点击结束</template>
                        <template v-else>点击圆球，跟随节拍做 {{ totalSeconds }} 秒呼吸练习</template>
                    </p>
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const iconUrl = new URL('@/assets/images/robot-fill.png', import.meta.url).href

const isLoggedIn = ref(false)

/* ── 呼吸球：4-4-6-2 引导呼吸（焦虑时常用来把呼吸放慢的节奏） ── */
const BREATH_PHASES = [
    { key: 'inhale', text: '吸气', seconds: 4, scale: 1.16, tip: '用鼻子慢慢吸气' },
    { key: 'hold', text: '屏息', seconds: 4, scale: 1.16, tip: '轻轻屏住就好' },
    { key: 'exhale', text: '呼气', seconds: 6, scale: 0.86, tip: '用嘴缓缓呼气' },
    { key: 'rest', text: '停留', seconds: 2, scale: 0.86, tip: '放松，准备下一轮' }
]
const RING_LENGTH = 2 * Math.PI * 46

const breathing = ref(false)
const phaseIndex = ref(0)
const remaining = ref(BREATH_PHASES[0].seconds)
const cycles = ref(0)
const reducedMotion = ref(false)
let timer = null

const phase = computed(() => BREATH_PHASES[phaseIndex.value])
const totalSeconds = computed(() => BREATH_PHASES.reduce((sum, item) => sum + item.seconds, 0))

// 每个阶段用不同的过渡时长，球体大小就与阶段秒数严格同步
const ballStyle = computed(() => {
    if (!breathing.value || reducedMotion.value) {
        return { transform: 'scale(1)', '--ball-duration': '0.8s' }
    }
    return { transform: `scale(${phase.value.scale})`, '--ball-duration': `${phase.value.seconds}s` }
})

// 圆环随当前阶段倒计时逐渐走完
const ringOffset = computed(() => {
    if (!breathing.value) return 0
    return RING_LENGTH * (1 - remaining.value / phase.value.seconds)
})

const tick = () => {
    remaining.value -= 1
    if (remaining.value > 0) return
    const next = (phaseIndex.value + 1) % BREATH_PHASES.length
    if (next === 0) cycles.value += 1
    phaseIndex.value = next
    remaining.value = BREATH_PHASES[next].seconds
}

const startBreathing = () => {
    phaseIndex.value = 0
    remaining.value = BREATH_PHASES[0].seconds
    cycles.value = 1
    breathing.value = true
    timer = setInterval(tick, 1000)
}

const stopBreathing = () => {
    breathing.value = false
    cycles.value = 0
    phaseIndex.value = 0
    remaining.value = BREATH_PHASES[0].seconds
    if (timer) {
        clearInterval(timer)
        timer = null
    }
}

const toggleBreathing = () => (breathing.value ? stopBreathing() : startBreathing())

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
    reducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
})

onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
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
    padding: 72px var(--mh-space-5);
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--mh-space-10);
}

.hero-text {
    max-width: 560px;
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

/* ── 呼吸球（点击开始引导式呼吸练习） ── */
.hero-art {
    width: 268px;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--mh-space-4);
}

.breath-ball {
    position: relative;
    width: 228px;
    height: 228px;
    padding: 0;
    border: 0;
    border-radius: 50%;
    cursor: pointer;
    font-family: inherit;
    display: flex;
    align-items: center;
    justify-content: center;
    background: radial-gradient(
        circle at 32% 26%,
        rgba(255, 255, 255, 0.96) 0%,
        rgba(255, 255, 255, 0.78) 46%,
        var(--mh-primary-light-9) 100%
    );
    box-shadow: var(--mh-shadow-lg), inset 0 0 0 1px rgba(255, 255, 255, 0.9);
    transform: scale(1);
    /* --ball-duration 由脚本按当前阶段秒数注入，球体缩放与呼吸节拍严格同步 */
    transition: transform var(--ball-duration, 0.8s) ease-in-out, box-shadow 0.35s ease;
    -webkit-tap-highlight-color: transparent;

    &:hover,
    &.is-active {
        box-shadow: var(--mh-shadow-primary), inset 0 0 0 1px rgba(255, 255, 255, 0.9);
    }

    &:focus-visible {
        outline: 2px solid var(--mh-primary);
        outline-offset: 6px;
    }

    .ball-halo {
        position: absolute;
        inset: -16px;
        border-radius: 50%;
        background: var(--mh-mint-light-7);
        opacity: 0;
        animation: ball-breathe 7s ease-in-out infinite;
        pointer-events: none;
    }

    &.is-active .ball-halo {
        animation: none;
        opacity: 0.35;
    }

    .ball-ring {
        position: absolute;
        inset: 9px;
        width: calc(100% - 18px);
        height: calc(100% - 18px);
        transform: rotate(-90deg);
        pointer-events: none;

        .ring-track,
        .ring-progress {
            fill: none;
            stroke-linecap: round;
        }

        .ring-track {
            stroke: var(--mh-border-strong);
            stroke-width: 1.5;
            opacity: 0.8;
        }

        .ring-progress {
            stroke: var(--mh-primary);
            stroke-width: 3;
            /* 2π × 46，与脚本里的 RING_LENGTH 一致（r 见模板中的 circle） */
            stroke-dasharray: 289.03;
            transition: stroke-dashoffset 1s linear;
        }
    }

    .ball-core {
        position: relative;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: var(--mh-space-3);
        color: var(--mh-primary);
    }

    /* iconUrl 是白色剪影 PNG，必须垫一层底色才看得见（见 CLAUDE.md 三.9） */
    .ball-face {
        width: 124px;
        height: 124px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: linear-gradient(150deg, var(--mh-primary-light-3) 0%, var(--mh-mint) 100%);
        box-shadow: var(--mh-shadow-sm);
        transition: transform 0.35s ease;
    }

    &:hover .ball-face {
        transform: scale(1.06);
    }

    .ball-image {
        width: 78px;
        height: 78px;
        object-fit: contain;
    }

    .ball-label {
        font-size: var(--mh-font-2);
        letter-spacing: 2px;
        text-indent: 2px;
        color: var(--mh-text-3);
    }

    .ball-phase {
        font-size: var(--mh-font-4);
        font-weight: 600;
        letter-spacing: 4px;
        text-indent: 4px;
    }

    .ball-count {
        font-size: var(--mh-font-7);
        font-weight: 600;
        line-height: 1.2;
        color: var(--mh-text-1);
        font-variant-numeric: tabular-nums;
    }
}

.ball-hint {
    height: 18px;
    font-size: var(--mh-font-2);
    color: var(--mh-text-3);
    text-align: center;
}

@keyframes ball-breathe {
    0%,
    100% {
        opacity: 0;
        transform: scale(0.94);
    }
    50% {
        opacity: 0.5;
        transform: scale(1.06);
    }
}

@media (prefers-reduced-motion: reduce) {
    .breath-ball {
        transition: box-shadow 0.35s ease;
    }

    .breath-ball .ball-halo {
        animation: none;
    }
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
