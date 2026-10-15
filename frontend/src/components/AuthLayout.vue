<template>
    <div class="auth-layout">
        <div class="auth-inner">
            <section class="brand-panel">
                <div class="brand-logo-wrap">
                    <el-image class="brand-logo" :src="iconUrl" alt="心理健康AI助手" fit="contain" />
                </div>
                <h1 class="brand-name">心理健康AI助手</h1>
                <p class="brand-slogan">每个深夜，每个焦虑的时刻，我们都在这里。</p>
                <ul class="brand-points">
                    <li>
                        <el-icon><ChatDotRound /></el-icon>
                        <span>随时倾诉，AI 用温和的方式回应你</span>
                    </li>
                    <li>
                        <el-icon><Notebook /></el-icon>
                        <span>情绪日记，慢慢看见自己的变化</span>
                    </li>
                    <li>
                        <el-icon><Lock /></el-icon>
                        <span>对话与日记仅你自己可见</span>
                    </li>
                </ul>
            </section>
            <section class="form-panel">
                <router-view></router-view>
            </section>
        </div>
        <p class="auth-foot">&copy; 2026 心理健康AI助手</p>
    </div>
</template>
<script setup>

const iconUrl = new URL('@/assets/images/robot-fill.png', import.meta.url).href
</script>
<style scoped lang="scss">
.auth-layout {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: var(--mh-space-8) var(--mh-space-5);
    background: linear-gradient(135deg, var(--mh-primary-light-9) 0%, var(--mh-mint-light-9) 100%);
}

.auth-inner {
    display: flex;
    align-items: center;
    gap: 72px;
    width: 100%;
    max-width: 940px;
}

.brand-panel {
    flex: 1;
    min-width: 0;

    .brand-logo-wrap {
        width: 64px;
        height: 64px;
        border-radius: var(--mh-radius-lg);
        background: rgba(255, 255, 255, 0.85);
        box-shadow: var(--mh-shadow-sm);
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: var(--mh-space-5);
    }

    .brand-logo {
        width: 40px;
        height: 40px;
    }

    .brand-name {
        font-size: var(--mh-font-7);
        font-weight: 700;
        color: var(--mh-text-1);
        margin-bottom: var(--mh-space-3);
    }

    .brand-slogan {
        font-size: var(--mh-font-4);
        line-height: 1.9;
        color: var(--mh-text-2);
        margin-bottom: var(--mh-space-5);
    }

    .brand-points {
        display: flex;
        flex-direction: column;
        gap: var(--mh-space-3);

        li {
            display: flex;
            align-items: center;
            gap: var(--mh-space-2);
            color: var(--mh-text-2);
            font-size: var(--mh-font-3);

            .el-icon {
                color: var(--mh-primary);
            }
        }
    }
}

.form-panel {
    width: 420px;
    flex-shrink: 0;
    background: var(--mh-surface);
    border: 1px solid var(--mh-border);
    border-radius: var(--mh-radius-xl);
    box-shadow: var(--mh-shadow);
    padding: var(--mh-space-8) var(--mh-space-6);
}

.auth-foot {
    margin-top: var(--mh-space-8);
    font-size: var(--mh-font-1);
    color: var(--mh-text-3);
}
</style>
