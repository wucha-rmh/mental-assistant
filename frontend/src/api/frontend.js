import service from '@/utils/request'

export const register = (data) => {
    return service.post('/user/register', data)
}

export const startSession = (data) => {
    return service.post('/psychological-chat/session/start', data)
}

export const getSessionList = (params) => {
    return service.get('/psychological-chat/sessions', { params })
}

export const deleteSession = (sessionId) => {
    return service.delete(`/psychological-chat/sessions/${sessionId}`)
}

export const getSessionDetail = (sessionId) => {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export const getSessionEmotion = (sessionId) => {
    return service.get(`/psychological-chat/session/${sessionId}/emotion`)
}

export const addEmotionDiary = (data) => {
    return service.post('/emotion-diary', data)
}

// 我的情绪日记（只返回当前登录用户自己的记录，可用 diaryDate 精确取某一天）
export const getMyEmotionDiaries = (params) => {
    return service.get('/emotion-diary/page', { params })
}

// 删除自己的一篇情绪日记（后端按「主键 + 登录用户」校验归属）
export const deleteMyEmotionDiary = (id) => {
    return service.delete(`/emotion-diary/${id}`)
}

export const getKnowledgeList = (params) => {
    return service.get('/knowledge/article/page', { params })
}

export const getKnowledgeDetail = (articleId) => {
    return service.get(`/knowledge/article/${articleId}`)
}

// 上传/更换当前登录用户的头像（返回更新后的用户信息）
export const uploadAvatar = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return service.post('/user/avatar', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}

// ==================== 个人中心：资料 / 密码 / 留言 ====================

// 修改个人资料（昵称 / 性别 / 生日），返回更新后的用户信息
export const updateProfile = (data) => {
    return service.put('/user/profile', data)
}

// 修改自己的密码：必须带 oldPassword（后端会校验）
export const updatePassword = (data) => {
    return service.put('/user/password', data)
}

// 给管理员留言（登录用户）
export const submitFeedback = (data) => {
    return service.post('/feedback', data)
}

// 给管理员留言（未登录：忘了密码、账号登不上时用，无需 token）
export const submitGuestFeedback = (data) => {
    return service.post('/feedback/guest', data)
}

// 我的留言（含管理员的回复）
export const getMyFeedbackPage = (params) => {
    return service.get('/feedback/mine', { params })
}