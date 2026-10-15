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