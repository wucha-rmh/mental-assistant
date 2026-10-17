import service from '@/utils/request'

export function login(data) {
    return service.post('/user/login', data)
}


export function categoryTree() {
    return service.get('/knowledge/category/tree')
}

export function articlePage(params) {
     return service.get('/knowledge/article/page', { params })
}

export function uploadFile(file, businessInfo) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('businessType', 'ARTICLE')
    formData.append('businessId', businessInfo.businessId)
    formData.append('businessField', 'cover')

    return service.post('/file/upload', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}


/**
 * 上传「正文插图」。
 *
 * 与封面共用 /file/upload，只是 businessField 记为 content（封面是 cover），
 * 后端按 businessType=ARTICLE 落到 uploads/bussiness/article/。
 * 必须走这个 axios 实例：wangEditor 自带的 XHR 上传不带 token 头，会被网关 401。
 */
export function uploadContentImage(file) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('businessType', 'ARTICLE')
    formData.append('businessField', 'content')

    return service.post('/file/upload', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}


export function createArticle(data) {
    return service.post('/knowledge/article', data)
}

export function getArticleDetail(id) {
    return service.get(`/knowledge/article/${id}`)
}


export function updateArticle(id, data) {
    return service.put(`/knowledge/article/${id}`, data)
}

export function changeArticleStatus(id, data) {
    return service.put(`/knowledge/article/${id}/status`, data)
}

export function deleteArticle(id) {
    return service.delete(`/knowledge/article/${id}`)
}

export function getConsultationPage(params) {
    return service.get('/psychological-chat/sessions', { params })
}

export function getSessionDetail(sessionId) {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export function getEmotionalPage(params) {
    return service.get('/emotion-diary/admin/page', { params })
}

export function deleteEmotional(id) {
    return service.delete(`/emotion-diary/admin/${id}`)
}

export function getUserPage(params) {
    return service.get('/user/admin/page', { params })
}

export function updateUserStatus(id, data) {
    return service.put(`/user/admin/${id}/status`, data)
}

// 重置用户密码（用户忘记密码时的兜底手段，不需要原密码）
export function resetUserPassword(id, data) {
    return service.put(`/user/admin/${id}/password`, data)
}

// ==================== 留言管理（后台） ====================

export function getFeedbackPage(params) {
    return service.get('/feedback/admin/page', { params })
}

export function replyFeedback(id, data) {
    return service.put(`/feedback/admin/${id}/reply`, data)
}

export function deleteFeedback(id) {
    return service.delete(`/feedback/admin/${id}`)
}

export function getFeedbackPendingCount() {
    return service.get('/feedback/admin/pending-count')
}

export function getAnalyticsOverview() {
    return service.get(`/data-analytics/overview`)
}

export function logout() {
    return service.post('/user/logout')
}