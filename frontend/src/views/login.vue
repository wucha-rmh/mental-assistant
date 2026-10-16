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
            <p class="footer-tip">
                忘记密码或登录不上？<a @click="openGuestDialog">给管理员留言</a>
            </p>
        </div>

        <!-- 未登录留言：忘记密码 / 账号登不上时唯一的通道（无需 token） -->
        <el-dialog v-model="guestDialogVisible" title="给管理员留言" width="520px" :close-on-click-modal="false">
            <el-alert
                class="guest-tip"
                type="info"
                :closable="false"
                show-icon
                title="留言不需要登录；如果是忘记密码，请写清用户名，管理员核实后会帮你重置密码"
            />
            <el-form ref="guestFormRef" :model="guestForm" :rules="guestRules" label-width="90px">
                <el-form-item label="用户名" prop="guestName">
                    <el-input v-model="guestForm.guestName" maxlength="50" placeholder="请输入你的用户名 / 注册邮箱" />
                </el-form-item>
                <el-form-item label="留言类型" prop="feedbackType">
                    <el-select v-model="guestForm.feedbackType" class="full-width">
                        <el-option
                            v-for="type in FEEDBACK_TYPES"
                            :key="type.value"
                            :label="type.label"
                            :value="type.value"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="留言内容" prop="content">
                    <el-input
                        v-model="guestForm.content"
                        type="textarea"
                        :rows="4"
                        maxlength="1000"
                        show-word-limit
                        placeholder="请描述你遇到的问题"
                    />
                </el-form-item>
                <el-form-item label="联系方式" prop="contact">
                    <el-input v-model="guestForm.contact" maxlength="100" placeholder="选填（手机号/邮箱），方便管理员联系你" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="guestDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="guestSubmitting" @click="submitGuestForm(guestFormRef)">
                    提交留言
                </el-button>
            </template>
        </el-dialog>
    </div>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { login } from '@/api/admin'
import { submitGuestFeedback } from '@/api/frontend'
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

/* ---------------- 未登录留言（忘记密码） ---------------- */
const FEEDBACK_TYPES = [
    { value: 1, label: '忘记密码/无法登录' },
    { value: 2, label: '账号问题' },
    { value: 3, label: '功能建议' },
    { value: 4, label: '其它' }
]

const guestDialogVisible = ref(false)
const guestFormRef = ref()
const guestSubmitting = ref(false)

const guestForm = reactive({
    guestName: '',
    feedbackType: 1,
    content: '',
    contact: ''
})

const guestRules = reactive({
    guestName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    feedbackType: [{ required: true, message: '请选择留言类型', trigger: 'change' }],
    content: [{ required: true, message: '请输入留言内容', trigger: 'blur' }]
})

const openGuestDialog = () => {
    guestForm.guestName = formData.username || ''
    guestForm.feedbackType = 1
    guestForm.content = ''
    guestForm.contact = ''
    guestDialogVisible.value = true
}

const submitGuestForm = (formEl) => {
    if (!formEl) return
    formEl.validate((valid) => {
        if (!valid) return
        guestSubmitting.value = true
        submitGuestFeedback({
            guestName: guestForm.guestName,
            feedbackType: guestForm.feedbackType,
            content: guestForm.content,
            contact: guestForm.contact || null
        }).then(() => {
            ElMessage.success('留言已提交，管理员会尽快处理')
            guestDialogVisible.value = false
        }).catch(() => {
            // 失败提示已由 request.js 统一处理
        }).finally(() => {
            guestSubmitting.value = false
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
        cursor: pointer;
    }

    .footer-tip {
        margin-top: var(--mh-space-2);
    }
}

.guest-tip {
    margin-bottom: var(--mh-space-4);
}

.full-width {
    width: 100%;
}
</style>
