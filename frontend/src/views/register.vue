<template>
    <div class="container">
        <router-link to="/" class="back-home">
            <el-icon><Back /></el-icon>
            <span>返回首页</span>
        </router-link>
        <div class="title-text">
            <h2>创建您的账户</h2>
            <p>只需填写下面几项，即可开始使用</p>
        </div>
        <el-form
            class="form-container"
            label-position="top"
            :model="formData"
            :rules="rules"
            ref="submitFormRef"
            @submit.prevent
        >
            <el-form-item label="用户名或邮箱" prop="username">
                <el-input v-model="formData.username" placeholder="请输入用户名" size="large" clearable />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
                <el-input v-model="formData.email" placeholder="请输入邮箱" size="large" clearable />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
                <el-input v-model="formData.nickname" placeholder="请输入昵称（可选）" size="large" clearable />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
                <el-input v-model="formData.phone" placeholder="请输入手机号（可选）" size="large" clearable />
            </el-form-item>
            <el-form-item label="密码" prop="password">
                <el-input v-model="formData.password" placeholder="请输入密码" size="large" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
                <el-input
                    v-model="formData.confirmPassword"
                    placeholder="请再次输入密码"
                    size="large"
                    type="password"
                    show-password
                    @keyup.enter="submitForm(submitFormRef)"
                />
            </el-form-item>
            <el-button class="btn" type="primary" size="large" :loading="loading" @click="submitForm(submitFormRef)">
                注册
            </el-button>
        </el-form>
        <div class="footer">
            <p>已有账户？<router-link to="/auth/login">去登录</router-link></p>
        </div>
    </div>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { register } from '@/api/frontend'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
const router = useRouter()
const formData = reactive({
    "username": "",
    "email": "",
    "nickname": "",
    "phone": "",
    "password": "",
    "confirmPassword": "",
    "gender": 0, // 性别
    "userType": 1 // 1为普通用户
})

const validateConfirm = (rule, value, callback) => {
    if (!value) {
        callback(new Error('请再次输入密码'))
    } else if (value !== formData.password) {
        callback(new Error('两次输入的密码不一致'))
    } else {
        callback()
    }
}

const rules = reactive({
    "username": [
        { required: true, message: "请输入用户名", trigger: "blur" }
    ],
    "email": [
        { required: true, message: "请输入邮箱", trigger: "blur" },
        { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
    ],
    "password": [
        { required: true, message: "请输入密码", trigger: "blur" }
    ],
    "confirmPassword": [
        { required: true, validator: validateConfirm, trigger: "blur" }
    ]
})

// 表单提交
const submitFormRef = ref(null)
const loading = ref(false)
const submitForm = async (formEl) => {
    if (!formEl) return
    formEl.validate((valid) => {
        if (!valid) return
        loading.value = true
        register(formData).then(() => {
            ElMessage.success('注册成功')
            // 注册成功后跳转到登录页
            router.push('/auth/login')
        }).catch(() => {
            // 失败提示（含业务异常 code=BUSINESS_ERROR）已由 request.js 统一处理
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
    margin-top: var(--mh-space-5);

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
