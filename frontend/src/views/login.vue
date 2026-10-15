<template>
    <div class="container">
        <router-link to="/" class="back-home">
            <el-icon><Back /></el-icon>
            <span>返回首页</span>
        </router-link>
        <div class="title-text">
            <h2>登录您的账户</h2>
            <p>继续与 AI 助手聊聊近况</p>
        </div>
        <el-form
            class="form-container"
            ref="ruleFormRef"
            :model="formData"
            :rules="rules"
            label-position="top"
            @submit.prevent
        >
            <el-form-item label="用户名或邮箱" prop="username">
                <el-input v-model="formData.username" size="large" placeholder="请输入用户名" clearable />
            </el-form-item>
            <el-form-item label="密码" prop="password">
                <el-input
                    v-model="formData.password"
                    size="large"
                    placeholder="请输入密码"
                    type="password"
                    show-password
                    @keyup.enter="submitForm(ruleFormRef)"
                />
            </el-form-item>
            <el-button class="btn" size="large" type="primary" :loading="loading" @click="submitForm(ruleFormRef)">
                登录
            </el-button>
        </el-form>
        <div class="footer">
            <p>还没有账户？<router-link to="/auth/register">去注册</router-link></p>
        </div>
    </div>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { login } from '@/api/admin'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const ruleFormRef = ref()
const loading = ref(false)

const formData = reactive({
    username: '',
    password: ''
})
const rules = reactive({
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' }
    ]
})

// 登录
const router = useRouter()
const submitForm = async (formEl) => {
    if (!formEl) return
    await formEl.validate((valid) => {
        if (!valid) return
        loading.value = true
        login(formData).then(data => {
            // 判断token是否存在
            if (!data.token) {
                return console.error('登录失败')
            }
            // 登录成功，保存token和用户信息
            localStorage.setItem('token', data.token)
            localStorage.setItem('userInfo', JSON.stringify(data.userInfo))
            // 同步到 pinia，让导航栏/头部的头像与昵称立即生效
            useUserStore().setInfo(data.userInfo)
            // 根据用户角色决定跳转的路径
            if (data.userInfo.userType === 2) {
                router.push('/back/dashboard')
            } else {
                router.push('/')
            }
        }).catch(() => {
            // 失败提示已由 request.js 统一处理
        }).finally(() => {
            loading.value = false
        })
    })
}

</script>
<style scoped lang="scss">
.container {
    width: 100%;
}

.back-home {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: var(--mh-font-2);
    color: var(--mh-text-3);
    transition: color 0.18s ease;

    &:hover {
        color: var(--mh-primary);
    }
}

.title-text {
    margin-top: var(--mh-space-6);

    h2 {
        font-size: var(--mh-font-7);
        color: var(--mh-text-1);
        margin-bottom: var(--mh-space-2);
    }

    p {
        font-size: var(--mh-font-3);
        color: var(--mh-text-3);
    }
}

.form-container {
    margin-top: var(--mh-space-6);

    .btn {
        margin-top: var(--mh-space-4);
        width: 100%;
        height: 46px;
        font-size: var(--mh-font-4);
    }
}

.footer {
    margin-top: var(--mh-space-5);
    padding-top: var(--mh-space-4);
    border-top: 1px solid var(--mh-border);
    text-align: center;
    font-size: var(--mh-font-2);
    color: var(--mh-text-3);

    a {
        color: var(--mh-primary);
        font-weight: 500;
    }
}
</style>
