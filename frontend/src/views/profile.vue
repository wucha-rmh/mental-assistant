<template>
    <div class="profile-page">
        <div class="header-section">
            <h2>个人中心</h2>
            <p>管理你的资料与密码；忘记密码时可以给管理员留言</p>
        </div>

        <div class="content">
            <AvatarUploader ref="avatarUploader" />

            <!-- 基本资料 -->
            <el-card class="mh-card--flush section-card">
                <template #header>
                    <div class="card-head">
                        <span class="card-title">基本资料</span>
                        <el-button text type="primary" @click="pickAvatar">更换头像</el-button>
                    </div>
                </template>
                <div class="card-body">
                    <div class="avatar-row">
                        <el-avatar :size="64" :src="userStore.avatar" />
                        <div class="avatar-meta">
                            <div class="avatar-name">{{ userStore.name }}</div>
                            <div class="avatar-account">@{{ userStore.info.username || '-' }}</div>
                        </div>
                    </div>

                    <el-form
                        ref="profileFormRef"
                        :model="profileForm"
                        :rules="profileRules"
                        label-width="90px"
                        @submit.prevent
                    >
                        <el-form-item label="昵称" prop="nickname">
                            <el-input
                                v-model="profileForm.nickname"
                                maxlength="50"
                                show-word-limit
                                placeholder="请输入昵称"
                            />
                        </el-form-item>
                        <el-form-item label="性别" prop="gender">
                            <el-radio-group v-model="profileForm.gender">
                                <el-radio :value="0">保密</el-radio>
                                <el-radio :value="1">男</el-radio>
                                <el-radio :value="2">女</el-radio>
                            </el-radio-group>
                        </el-form-item>
                        <el-form-item label="生日" prop="birthday">
                            <el-date-picker
                                v-model="profileForm.birthday"
                                type="date"
                                value-format="YYYY-MM-DD"
                                placeholder="选择生日"
                                :editable="false"
                                :disabled-date="disableFutureDate"
                            />
                        </el-form-item>
                        <el-form-item label="邮箱">
                            <div class="readonly-field">
                                <span :class="{ 'is-empty': !userStore.info.email }">
                                    {{ userStore.info.email || '未填写' }}
                                </span>
                            </div>
                        </el-form-item>
                        <el-form-item label="手机号">
                            <div class="readonly-field">
                                <span :class="{ 'is-empty': !userStore.info.phone }">
                                    {{ userStore.info.phone || '未填写' }}
                                </span>
                            </div>
                        </el-form-item>
                        <el-form-item>
                            <div class="readonly-hint">
                                邮箱与手机号用于登录和找回密码，不在这里修改；如需变更请在下方给管理员留言。
                            </div>
                        </el-form-item>
                        <el-form-item>
                            <el-button type="primary" :loading="profileLoading" @click="submitProfile(profileFormRef)">
                                保存资料
                            </el-button>
                        </el-form-item>
                    </el-form>
                </div>
            </el-card>

            <!-- 修改密码 -->
            <el-card class="mh-card--flush section-card">
                <template #header>
                    <div class="card-head">
                        <span class="card-title">修改密码</span>
                    </div>
                </template>
                <div class="card-body">
                    <el-alert
                        class="password-tip"
                        type="info"
                        :closable="false"
                        show-icon
                        title="忘记原密码？请在下方给管理员留言，由管理员重置后再登录修改"
                    />
                    <el-form
                        ref="passwordFormRef"
                        :model="passwordForm"
                        :rules="passwordRules"
                        label-width="90px"
                        @submit.prevent
                    >
                        <el-form-item label="原密码" prop="oldPassword">
                            <el-input
                                v-model="passwordForm.oldPassword"
                                type="password"
                                show-password
                                placeholder="请输入当前密码"
                            />
                        </el-form-item>
                        <el-form-item label="新密码" prop="newPassword">
                            <el-input
                                v-model="passwordForm.newPassword"
                                type="password"
                                show-password
                                placeholder="6-32 位"
                            />
                        </el-form-item>
                        <el-form-item label="确认新密码" prop="confirmPassword">
                            <el-input
                                v-model="passwordForm.confirmPassword"
                                type="password"
                                show-password
                                placeholder="再次输入新密码"
                                @keyup.enter="submitPassword(passwordFormRef)"
                            />
                        </el-form-item>
                        <el-form-item>
                            <el-button type="primary" :loading="passwordLoading" @click="submitPassword(passwordFormRef)">
                                修改密码
                            </el-button>
                        </el-form-item>
                    </el-form>
                </div>
            </el-card>

            <!-- 我的留言 -->
            <el-card class="mh-card--flush section-card">
                <template #header>
                    <div class="card-head">
                        <span class="card-title">我的留言</span>
                        <el-button type="primary" plain @click="openFeedbackDialog">我要留言</el-button>
                    </div>
                </template>
                <div class="card-body">
                    <div v-loading="feedbackLoading">
                        <el-empty v-if="!feedbackLoading && feedbackList.length === 0" description="还没有留言" />
                        <div v-for="item in feedbackList" :key="item.id" class="feedback-item">
                            <div class="feedback-head">
                                <el-tag size="small" effect="plain">{{ item.feedbackTypeName }}</el-tag>
                                <el-tag
                                    size="small"
                                    effect="plain"
                                    :type="item.status === 1 ? 'success' : 'warning'"
                                >
                                    {{ item.statusName }}
                                </el-tag>
                                <span class="feedback-time">{{ item.createdAt }}</span>
                            </div>
                            <div class="feedback-content">{{ item.content }}</div>
                            <div v-if="item.adminReply" class="feedback-reply">
                                <span class="reply-label">管理员回复：</span>{{ item.adminReply }}
                            </div>
                        </div>
                        <div v-if="feedbackTotal > feedbackSize" class="feedback-pagination">
                            <el-pagination
                                v-model:current-page="feedbackPage"
                                :page-size="feedbackSize"
                                :total="feedbackTotal"
                                layout="prev, pager, next"
                                @current-change="loadFeedbacks"
                            />
                        </div>
                    </div>
                </div>
            </el-card>
        </div>

        <!-- 留言弹窗 -->
        <el-dialog v-model="feedbackDialogVisible" title="给管理员留言" width="520px" top="12vh" :close-on-click-modal="false">
            <el-form ref="feedbackFormRef" :model="feedbackForm" :rules="feedbackRules" label-width="90px">
                <el-form-item label="留言类型" prop="feedbackType">
                    <el-select v-model="feedbackForm.feedbackType" class="full-width" placeholder="请选择">
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
                        v-model="feedbackForm.content"
                        type="textarea"
                        :rows="4"
                        maxlength="1000"
                        show-word-limit
                        placeholder="请描述你的问题，比如「忘记密码，登录不上」"
                    />
                </el-form-item>
                <el-form-item label="联系方式" prop="contact">
                    <el-input v-model="feedbackForm.contact" maxlength="100" placeholder="选填，方便管理员联系你" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="feedbackDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="feedbackSubmitting" @click="submitFeedbackForm(feedbackFormRef)">
                    提交
                </el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import AvatarUploader from '@/components/AvatarUploader.vue'
import { updateProfile, updatePassword, submitFeedback, getMyFeedbackPage } from '@/api/frontend'

const router = useRouter()
const userStore = useUserStore()

// 留言类型字典（与后端 UserFeedbackType 保持一致）
const FEEDBACK_TYPES = [
    { value: 1, label: '忘记密码/无法登录' },
    { value: 2, label: '账号问题' },
    { value: 3, label: '功能建议' },
    { value: 4, label: '其它' }
]

/* ---------------- 基本资料 ---------------- */
const avatarUploader = ref(null)
const profileFormRef = ref(null)
const profileLoading = ref(false)

const profileForm = reactive({
    nickname: userStore.info.nickname || '',
    gender: userStore.info.gender ?? 0,
    birthday: userStore.info.birthday || null
})

const profileRules = reactive({
    nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
    birthday: []
})

const disableFutureDate = (date) => date.getTime() > Date.now()

const pickAvatar = () => {
    if (avatarUploader.value) {
        avatarUploader.value.pick()
    }
}

const submitProfile = (formEl) => {
    if (!formEl) return
    formEl.validate((valid) => {
        if (!valid) return
        profileLoading.value = true
        updateProfile({
            nickname: profileForm.nickname,
            gender: profileForm.gender,
            birthday: profileForm.birthday || null
        }).then((res) => {
            // 只合并后端返回的字段：userType 等登录态字段不能被覆盖掉（路由守卫要用）
            if (res) {
                userStore.setInfo({ ...userStore.info, ...res })
            }
            ElMessage.success('资料已更新')
        }).catch(() => {
            // 失败提示已由 request.js 统一处理
        }).finally(() => {
            profileLoading.value = false
        })
    })
}

/* ---------------- 修改密码 ---------------- */
const passwordFormRef = ref(null)
const passwordLoading = ref(false)

const passwordForm = reactive({
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
    if (value !== passwordForm.newPassword) {
        callback(new Error('两次输入的新密码不一致'))
        return
    }
    callback()
}

const passwordRules = reactive({
    oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
    newPassword: [
        { required: true, message: '请输入新密码', trigger: 'blur' },
        { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
    ],
    confirmPassword: [
        { required: true, message: '请再次输入新密码', trigger: 'blur' },
        { validator: validateConfirmPassword, trigger: 'blur' }
    ]
})

const submitPassword = (formEl) => {
    if (!formEl) return
    formEl.validate((valid) => {
        if (!valid) return
        passwordLoading.value = true
        updatePassword({
            oldPassword: passwordForm.oldPassword,
            newPassword: passwordForm.newPassword,
            confirmPassword: passwordForm.confirmPassword
        }).then(() => {
            // 后端不会让旧 token 失效（JWT 无状态），所以这里主动清掉本地登录态：
            // 改密码后重新登录是唯一能保证「新密码才管用」的办法。
            ElMessage.success('密码已修改，请用新密码重新登录')
            localStorage.removeItem('token')
            userStore.clear()
            router.push('/auth/login')
        }).catch(() => {
            // 原密码错误等提示已由 request.js 统一处理
        }).finally(() => {
            passwordLoading.value = false
        })
    })
}

/* ---------------- 我的留言 ---------------- */
const feedbackDialogVisible = ref(false)
const feedbackFormRef = ref(null)
const feedbackSubmitting = ref(false)
const feedbackLoading = ref(false)
const feedbackList = ref([])
const feedbackPage = ref(1)
const feedbackSize = 5
const feedbackTotal = ref(0)

const feedbackForm = reactive({
    feedbackType: 4,
    content: '',
    contact: ''
})

const feedbackRules = reactive({
    feedbackType: [{ required: true, message: '请选择留言类型', trigger: 'change' }],
    content: [{ required: true, message: '请输入留言内容', trigger: 'blur' }]
})

const loadFeedbacks = () => {
    feedbackLoading.value = true
    getMyFeedbackPage({ pageNum: feedbackPage.value, pageSize: feedbackSize }).then((res) => {
        feedbackList.value = (res && res.records) || []
        feedbackTotal.value = (res && res.total) || 0
    }).catch(() => {
        feedbackList.value = []
        feedbackTotal.value = 0
    }).finally(() => {
        feedbackLoading.value = false
    })
}

const openFeedbackDialog = () => {
    feedbackForm.feedbackType = 4
    feedbackForm.content = ''
    feedbackForm.contact = ''
    feedbackDialogVisible.value = true
}

const submitFeedbackForm = (formEl) => {
    if (!formEl) return
    formEl.validate((valid) => {
        if (!valid) return
        feedbackSubmitting.value = true
        submitFeedback({
            feedbackType: feedbackForm.feedbackType,
            content: feedbackForm.content,
            contact: feedbackForm.contact || null
        }).then(() => {
            ElMessage.success('留言已提交，管理员回复后可以在这里看到')
            feedbackDialogVisible.value = false
            feedbackPage.value = 1
            loadFeedbacks()
        }).catch(() => {
            // 失败提示已由 request.js 统一处理
        }).finally(() => {
            feedbackSubmitting.value = false
        })
    })
}

onMounted(() => {
    // 这个页面只给登录用户看：没 token 直接回登录页
    if (!localStorage.getItem('token')) {
        router.push('/auth/login')
        return
    }
    loadFeedbacks()
})
</script>

<style scoped lang="scss">
.header-section {
    padding: var(--mh-space-6) var(--mh-space-5) var(--mh-space-4);
    text-align: center;

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

.content {
    max-width: 820px;
    margin: 0 auto;
    padding: 0 var(--mh-space-5) var(--mh-space-6);
    display: flex;
    flex-direction: column;
    gap: var(--mh-space-5);
}

.card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.card-title {
    font-size: var(--mh-font-5);
    font-weight: 600;
    color: var(--mh-text-1);
}

.card-body {
    padding: var(--mh-space-4) var(--mh-space-5) var(--mh-space-2);
}

.avatar-row {
    display: flex;
    align-items: center;
    gap: var(--mh-space-4);
    margin-bottom: var(--mh-space-5);
}

.avatar-meta {
    min-width: 0;
}

.avatar-name {
    font-size: var(--mh-font-5);
    font-weight: 600;
    color: var(--mh-text-1);
}

.avatar-account {
    font-size: var(--mh-font-1);
    color: var(--mh-text-3);
}

/* 只读信息（邮箱/手机号）：做成和输入框同宽同高的浅底块，左对齐不再串行 */
.readonly-field {
    display: flex;
    align-items: center;
    width: 100%;
    min-height: 32px;
    padding: 0 var(--mh-space-3);
    background: var(--mh-bg);
    border-radius: var(--mh-radius-sm);
    color: var(--mh-text-2);
    word-break: break-all;

    .is-empty {
        color: var(--mh-text-3);
    }
}

.readonly-hint {
    margin-left: 90px;
    font-size: var(--mh-font-1);
    color: var(--mh-text-3);
    line-height: 1.6;
}

.password-tip {
    margin-bottom: var(--mh-space-4);
}

.feedback-item {
    padding: var(--mh-space-3) 0;
    border-bottom: 1px solid var(--mh-border);

    &:last-child {
        border-bottom: none;
    }
}

.feedback-head {
    display: flex;
    align-items: center;
    gap: var(--mh-space-2);
}

.feedback-time {
    margin-left: auto;
    font-size: var(--mh-font-1);
    color: var(--mh-text-3);
}

.feedback-content {
    margin-top: var(--mh-space-2);
    color: var(--mh-text-1);
    white-space: pre-wrap;
    word-break: break-word;
}

.feedback-reply {
    margin-top: var(--mh-space-2);
    padding: var(--mh-space-2) var(--mh-space-3);
    background: var(--mh-bg);
    border-radius: var(--mh-radius-sm);
    color: var(--mh-text-2);
    white-space: pre-wrap;
    word-break: break-word;

    .reply-label {
        color: var(--mh-primary);
        font-weight: 500;
    }
}

.feedback-pagination {
    margin-top: var(--mh-space-4);
    display: flex;
    justify-content: center;
}

.full-width {
    width: 100%;
}
</style>
