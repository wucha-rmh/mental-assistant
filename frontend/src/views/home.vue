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
                <!-- 可滚动的球体：按住左右拖动即滚动，松手带一点惯性 -->
                <div class="hero-art">
                    <div
                        class="roll-ball"
                        :class="{ 'is-dragging': dragging }"
                        role="img"
                        aria-label="可滚动的球体，按住左右拖动"
                        tabindex="0"
                        @pointerdown="onPointerDown"
                        @pointermove="onPointerMove"
                        @pointerup="onPointerUp"
                        @pointercancel="endDrag"
                        @keydown.left.prevent="spin(-28)"
                        @keydown.right.prevent="spin(28)"
                    >
                        <span class="ball-sphere">
                            <span class="sphere-texture" :style="{ transform: `rotate(${roll}deg)` }"></span>
                            <span class="sphere-shade"></span>
                            <span class="sphere-sheen"></span>
                        </span>
                    </div>
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

const isLoggedIn = ref(false)

/* ── 可滚动的球体：按住左右拖动即滚动，松手带一点惯性 ── */
const reducedMotion = ref(false)
const roll = ref(0)
const dragging = ref(false)
let pointerId = null
let lastX = 0
let velocity = 0
let inertiaId = null

const stopInertia = () => {
    if (inertiaId) {
        cancelAnimationFrame(inertiaId)
        inertiaId = null
    }
}

const glide = () => {
    velocity *= 0.94
    if (Math.abs(velocity) < 0.06) {
        inertiaId = null
        return
    }
    roll.value += velocity
    inertiaId = requestAnimationFrame(glide)
}

const onPointerDown = (event) => {
    if (event.pointerType === 'mouse' && event.button !== 0) return
    pointerId = event.pointerId
    dragging.value = true
    lastX = event.clientX
    velocity = 0
    stopInertia()
    event.currentTarget.setPointerCapture?.(pointerId)
}

const onPointerMove = (event) => {
    if (!dragging.value || event.pointerId !== pointerId) return
    const delta = event.clientX - lastX
    lastX = event.clientX
    roll.value += delta * 0.6
    velocity = delta * 0.6
}

const endDrag = () => {
    if (!dragging.value) return
    dragging.value = false
    pointerId = null
    if (!reducedMotion.value) glide()
}

const onPointerUp = () => endDrag()

// 键盘也能转：左右方向键各推一把
const spin = (deg) => {
    stopInertia()
    roll.value += deg
    if (!reducedMotion.value) {
        velocity = deg * 0.4
        glide()
    }
}

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
    stopInertia()
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

/* ── 可滚动的球体（按住左右拖动即滚动） ── */
.hero-art {
    width: 268px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
}

.roll-ball {
    position: relative;
    width: 244px;
    height: 244px;
    border-radius: 50%;
    cursor: grab;
    touch-action: pan-y;
    user-select: none;
    -webkit-tap-highlight-color: transparent;

    &:focus-visible {
        outline: 2px solid var(--mh-primary);
        outline-offset: 6px;
    }

    /* ── 3D 球体：内层纹理负责「滚」，外层负责体积感 ── */
    .ball-sphere {
        position: absolute;
        inset: 0;
        border-radius: 50%;
        overflow: hidden;
        background: radial-gradient(circle at 50% 45%, #f3f8fa 0%, #d7e5ec 100%);
        /* 落地投影：让球「坐」在页面上而不是贴在上面 */
        box-shadow: 0 18px 30px -14px rgba(47, 62, 70, 0.45);
        pointer-events: none;
    }

    .sphere-texture {
        position: absolute;
        inset: -30%;
        border-radius: 50%;
        /* 经线 + 色块：旋转时能明确看出「球在滚」 */
        background:
            repeating-linear-gradient(
                90deg,
                rgba(78, 128, 152, 0.3) 0 1.5px,
                rgba(78, 128, 152, 0) 1.5px 24px
            ),
            conic-gradient(
                from 0deg,
                rgba(78, 128, 152, 0.5) 0deg,
                rgba(127, 182, 158, 0.45) 62deg,
                rgba(255, 255, 255, 0.85) 122deg,
                rgba(78, 128, 152, 0.5) 182deg,
                rgba(127, 182, 158, 0.45) 242deg,
                rgba(255, 255, 255, 0.85) 302deg,
                rgba(78, 128, 152, 0.5) 360deg
            );
        filter: blur(3px);
        /* 越靠边越淡，模拟球面把纹理带走 */
        mask-image: radial-gradient(circle at 50% 50%, rgba(0, 0, 0, 1) 52%, rgba(0, 0, 0, 0.2) 100%);
        -webkit-mask-image: radial-gradient(circle at 50% 50%, rgba(0, 0, 0, 1) 52%, rgba(0, 0, 0, 0.2) 100%);
        will-change: transform;
    }

    .sphere-shade,
    .sphere-sheen {
        position: absolute;
        inset: 0;
        border-radius: 50%;
        pointer-events: none;
    }

    /* 左上受光、右下环境反光、边缘压暗 —— 球体体积感主要来自这一层 */
    .sphere-shade {
        background:
            radial-gradient(
                circle at 33% 26%,
                rgba(255, 255, 255, 0.82) 0%,
                rgba(255, 255, 255, 0.12) 22%,
                rgba(255, 255, 255, 0) 42%
            ),
            radial-gradient(circle at 72% 78%, rgba(214, 234, 225, 0.7) 0%, rgba(255, 255, 255, 0) 46%),
            radial-gradient(circle at 64% 70%, rgba(255, 255, 255, 0) 40%, rgba(78, 128, 152, 0.42) 100%);
        box-shadow: inset -16px -18px 30px rgba(78, 128, 152, 0.34), inset 6px 8px 18px rgba(255, 255, 255, 0.38),
            inset 0 0 0 1px rgba(255, 255, 255, 0.45);
    }

    /* 镜面高光：不随纹理转动，固定在天光方向 */
    .sphere-sheen {
        background:
            radial-gradient(ellipse 22% 15% at 29% 22%, rgba(255, 255, 255, 0.95) 0%, rgba(255, 255, 255, 0) 70%),
            radial-gradient(circle at 30% 23%, rgba(255, 255, 255, 0.9) 0%, rgba(255, 255, 255, 0) 13%);
    }

    &.is-dragging {
        cursor: grabbing;
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
