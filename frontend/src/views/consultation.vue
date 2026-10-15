<template>
    <div class="consultation-container">
        <div class="sidebar">
            <!-- AI助手信息 -->
             <div class="ai-assistant-info">
                <div class="breathing-circle">
                    <el-image :src="iconUrl" style="width: 25px;height:25px" alt="AI助手" />
                </div>
                <h3 class="assistant-name">心理健康AI助手</h3>
                <div class="online-status">
                    <div class="status-dot"></div>
                    在线服务中
                </div>
             </div>
             <!-- 情绪花园 -->
             <div class="emotion-garden">
                <div class="garden-header">
                    <div class="garden-title"> 情绪花园 </div>
                </div>
                <div class="emotion-info" :class="{ negative: currentEmotion.isNegative }">
                    <div class="emotion-name">{{ currentEmotion.primaryEmotion }}</div>
                    <div class="emotion-score">{{ currentEmotion.emotionScore }}</div>
                </div>
                <div class="warm-tips">
                    <div class="emotion-status-text">
                        <span class="status-label">今天感觉</span>
                        <span class="status-emotion" :class="currentEmotion.isNegative ? 'is-negative' : 'is-positive'">
                            {{ currentEmotion.isNegative ? '需要关注' : '很不错' }}
                        </span>
                    </div>
                    <div class="emotion-intensity">
                        <span class="intensity-dots">
                            <span v-for="dot in 3" :key="dot" class="dot" :class="{'active': getIntensityClass(currentEmotion.emotionScore) >= dot}"></span>
                        </span>                        <span class="intensity-text">
                            {{ getRiskText(currentEmotion.riskLevel) }}
                        </span>
                    </div>
                    <!-- 温暖建议卡片 -->
                     <div class="warm-suggestion" v-if="currentEmotion.suggestion">
                        <div class="suggestion-icon">💝</div>
                        <div class="suggestion-content">
                            <div class="suggestion-title">给你的小建议</div>
                            <div class="suggestion-text">{{ currentEmotion.suggestion }}</div>
                        </div>
                     </div>
                     <!-- 治愈行动 -->
                      <div class="healing-actions" v-if="currentEmotion.improvementSuggestions.length > 0">
                        <div class="actions-title">治愈小行动</div>
                        <div class="actions-list">
                            <div v-for="action in currentEmotion.improvementSuggestions" :key="action" class="action-item">
                                <div class="action-icon">✨</div>
                                <div class="action-text">{{ action }}</div>
                            </div>
                        </div>
                      </div>
                      <!-- 风险提示 -->
                    <div class="risk-notice" v-if="currentEmotion.isNegative && currentEmotion.riskLevel > 1">
                        <div class="notice-icon">🤗</div>
                        <div class="notice-content">
                            <div class="notice-title">温馨提示</div>
                            <div class="notice-text">{{ currentEmotion.riskDescription }}</div>
                        </div>
                    </div>
                </div>
             </div>
             <!-- 会话列表 -->
             <div class="session-history">
                <h4 class="section-title">会话列表</h4>
                <div class="session-list">
                    <p v-if="sessionList.length === 0" class="no-sessions-text">暂无历史会话，直接开始新的对话吧</p>
                    <div
                        v-for="session in sessionList"
                        :key="session.id"
                        @click="handleSessionClick(session)"
                        class="session-item"
                        :class="{ active: isActiveSession(session) }"
                    >
                        <div class="session-info">
                            <div class="session-title">
                                <span>{{ session.sessionTitle }}</span>
                                <div class="session-meta">
                                    <span class="session-time">{{ session.startedAt }}</span>
                                </div>
                                <div class="session-preview">
                                    {{ session.lastMessageContent }}
                                </div>
                                <div class="session-stats">
                                    <span>
                                        <el-icon>
                                            <ChatRound />
                                        </el-icon>
                                        {{ session.messageCount || 0 }}
                                    </span>
                                    <span>
                                        <el-icon>
                                            <Clock />
                                        </el-icon>
                                        {{ session.durationMinutes || 0 }} 分钟
                                    </span>
                                </div>
                            </div>
                            <div class="session-actions">
                                <!-- .stop 必须加：否则删会话的同时会触发外层点击，把被删的会话又打开 -->
                                <el-button
                                    text
                                    type="danger"
                                    size="small"
                                    title="删除该会话"
                                    @click.stop="handleDeleteSession(session.id)"
                                >
                                    <el-icon>
                                        <DeleteFilled />
                                    </el-icon>
                                </el-button>
                            </div>
                        </div>
                    </div>
                </div>
             </div>
        </div>
        <div class="chat-main">
            <div class="chat-header">
                <div class="header-left">
                    <div class="chat-avatar">
                        <el-image :src="iconUrl1" style="width: 30px;height: 30px" />
                    </div>
                    <div class="chat-info">
                        <h2>心理健康AI助手</h2>
                        <p>您的贴心AI心理健康助手</p>
                    </div>
                </div>
                <el-button circle @click="createNewFrontendSession" title="新建会话">
                    <el-icon>
                        <Plus />
                    </el-icon>
                </el-button>
            </div>
            <!-- 聊天消息区域 -->
            <div class="chat-messages">
                <!-- 欢迎用语 -->
                <div class="message-item ai-message" v-if="messages.length === 0">
                    <div class="message-avatar">
                        <el-image :src="iconUrl" style="width: 18px;height: 18px" />
                    </div>
                    <div class="message-content">
                        <div class="message-bubble">
                            <p>您好！我是小暖，您的AI心理健康助手。很高兴陪伴您，为您提供温暖的心理支持。请告诉我，今天您感觉怎么样？有什么想要分享的吗？</p>
                        </div>
                        <div class="message-time">刚刚</div>
                    </div>
                </div>
                <!-- 消息列表 -->
                <div v-for="msg in messages" :key="msg.id" class="message-item" :class="msg.senderType === 1 ?  'user-message' : 'ai-message'">
                    <div class="message-avatar">
                        <el-image v-if="msg.senderType === 1" style="width: 18px; height:18px" :src="iconUrl2"></el-image>
                        <el-image v-if="msg.senderType === 2" style="width: 18px; height:18px" :src="iconUrl"></el-image>
                    </div>
                    <div class="message-content">
                        <div class="message-bubble">
                            <!-- AI正在思考中 -->
                            <div v-if="msg.senderType === 2 && isAiTyping && !msg.content" class="typing-indicator">
                                <div class="typing-dot"></div>
                                <div class="typing-dot"></div>
                                <div class="typing-dot"></div>
                            </div>
                            <!-- AI错误提示 -->
                            <div v-else-if="msg.isError" class="error-message">
                                <p>{{ msg.content }}</p>
                            </div>
                            <!-- AI正常返回消息 -->
                             <MarkdownRenderer v-else-if="msg.senderType === 2 && !msg.isError" :content="msg.content" :is-ai-message="true" />
                             <p v-else-if="msg.content" v-html="formatMessageContent(msg.content)"></p>
                        </div>
                        <div class="message-time">{{ msg.senderType === 2 && isAiTyping ? '正在输入中...' : msg.createdAt }}</div>
                    </div>
                </div>
            </div>
            <!-- 消息输入区域 -->
            <div class="chat-input">
                <div class="input-container">
                    <el-input
                        v-model="userMessage"
                        placeholder="请输入您想要分享的内容..."
                        type="textarea"
                        :rows="3"
                        :disabled="isAiTyping"
                        @keydown="handleKeyDown"
                        class="message-input"
                        clearable />
                        <div class="input-footer">
                            <span>按Enter发送，Shift+Enter换行</span>
                            <span>{{ userMessage.length }}/500</span>
                        </div>
                </div>
                <el-button :disabled="!userMessage.trim() || userMessage.length > 500" type="primary" class="send-btn" @click="sendMessage">
                    <el-icon>
                        <Promotion />
                    </el-icon>
                </el-button>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { startSession, getSessionList, deleteSession, getSessionDetail, getSessionEmotion } from '@/api/frontend'
import { dayjs, ElMessage, ElMessageBox } from 'element-plus'
import { ChatRound, DeleteFilled } from '@element-plus/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'

const iconUrl = new URL('@/assets/images/robot-fill.png', import.meta.url).href
const iconUrl1 = new URL('@/assets/images/like.png', import.meta.url).href
const iconUrl2 = new URL('@/assets/images/users.png', import.meta.url).href

// 新建会话
const createNewFrontendSession = () => {
    // 创建一个新的会话对象
    const newSession = {
        sessionId: `temp_${Date.now()}`,
        status: 'TEMP',
        sessionTitle: '新对话'
    }
    currentSession.value = newSession
}

// 定义一个当前会话对象
const currentSession = ref(null)
const sessionList = ref([])

// 定义对话消息
const messages = ref([])
// 定义用户输入消息
const userMessage = ref('')
// 定义AI助手是否正在输入
const isAiTyping = ref(false)

// 情绪花园（默认值集中在一处：接口失败或字段缺失时，模板里的插值与 .length 都不会报错）
const defaultEmotion = {
    primaryEmotion: '中性',
    emotionScore: 50,
    isNegative: false,
    riskLevel: 0,
    suggestion: '情绪状态平稳',
    riskDescription: '',
    improvementSuggestions: []
}
const currentEmotion = ref({ ...defaultEmotion })

const loadSessionEmotion = (sessionId) => {
   // 确保sessionID格式正确
    const id = sessionId.toString().startsWith('session_') ? sessionId : `session_${sessionId}`

    getSessionEmotion(id).then(res => {
        if (res) {
            // 与默认值合并，保证 improvementSuggestions 始终是数组
            currentEmotion.value = {
                ...defaultEmotion,
                ...res,
                improvementSuggestions: res.improvementSuggestions || []
            }
        }
    }).catch(() => {
        // 失败提示已由 request.js 统一处理，这里保留面板原值
    })
}

// 当前正在查看的会话高亮（旧实现里 .active 这个类名从未被绑定过）
const isActiveSession = (session) => currentSession.value?.sessionId === `session_${session.id}`

const getIntensityClass = (score) => {
    if (score >= 61) {
        return 3
    }
    if (score >= 31) {
        return 2
    }
    return 1
}

const getRiskText = (level) => {
    switch (level) {
        case 0:
            return '正常'
        case 1:
            return '关注'
        case 2:
            return '预警'
        case 3:
            return '危机'
        default:
            return '正常'
    }
}

// 定义处理键盘事件：Enter 发送、Shift+Enter 换行（输入框下方的提示文案就是这个意思，旧实现按了没反应）
const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        sendMessage()
    }
}

// 用户发送消息
const sendMessage = () => {
    if (!userMessage.value.trim()) return

    if (isAiTyping.value) {
        ElMessage.error('AI助手正在输入中，请稍后')
        return
    }

    const message = userMessage.value.trim()
    userMessage.value = ''

    // 如果没有会话或者是临时会话，就需要创建一个新的会话
    if (currentSession.value.status === 'TEMP') {
       startNewSession(message)
    } else {
        // 继续现有会话
        messages.value.push({
            id: Date.now(),
            senderType: 1,
            content: message,
            createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss')
        })
        startAIResponse(currentSession.value.sessionId, message)
    }
}

const startNewSession = (message) => {
    // 构建会话参数
    const sessionParams = {
        initialMessage: message
    }
    if (currentSession.value.sessionTitle === '新对话') {
        sessionParams.sessionTitle = `心理健康AI助手 - ${new Date().toLocaleString()}`
    } else {
        // 如果历史会话记录
        sessionParams.sessionTitle = currentSession.value.sessionTitle
    }
    // 调用后端接口创建新会话
    startSession(sessionParams).then(res => {
       // 将后端返回的数据转为前端会话格式
       const sessionData = {
            sessionId: res.sessionId,
            status: res.status,
            sessionTitle: sessionParams.sessionTitle
       }
       // 如果当前是临时会话，更新数据
       if (currentSession.value && currentSession.value.status === 'TEMP') {
            // 更新为正式会话 
            Object.assign(currentSession.value, sessionData)
       } else {
            // 否则，创建一个新的会话
            currentSession.value = sessionData
       }
       // 更新会话列表
       getSessionPage()

       // 添加初始用户消息
       messages.value.push({
        id: Date.now(),
        senderType: 1,
        content: message,
        createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss')
       })

       // 开始流式对话
       startAIResponse(currentSession.value.sessionId, message)
    }).catch(() => {
       // 创建会话失败的提示已由 request.js 统一处理
    })
}

const startAIResponse = (sessionId, userMessage) => {
    // 防止重复发送
    if (isAiTyping.value) {
        ElMessage.error('AI助手正在输入中，请稍后')
        return
    }

    
    isAiTyping.value = true

    const aiMessage = {
        id: `ai_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        senderType: 2,
        content: '',
        createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss')
    }
    messages.value.push(aiMessage)

    // 调用流式接口
    const ctrl = new AbortController() // 用来中止fetch请求
    fetchEventSource('/api/psychological-chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Token': localStorage.getItem('token'),
            'Accept': 'text/event-stream'
        },
        body: JSON.stringify({
            sessionId,
            userMessage
        }),
        signal: ctrl.signal,
        onopen: (response) => {
            // 后端声明 produces=text/event-stream，这里只用前缀比对，容忍可能的 ;charset=utf-8
            const contentType = response.headers.get('Content-Type') || ''
            if (!contentType.startsWith('text/event-stream')) {
                ElMessage.error('服务器返回非流式数据')
            }
        },
        onmessage: (event) => {
            const raw = event.data.trim()
            if (!raw) return
            const eventName = event.event

            if (eventName === 'done') {
                isAiTyping.value = false
                ctrl.abort()
                // 情绪分析统一交给 onclose 触发一次，避免重复请求
                return
            }
            const payload = JSON.parse(raw)
            const ok = String(payload.code) === '200'
            // 当前会话的AI消息（流式片段不断追加到这一条上）
            const aiMessage = messages.value[messages.value.length - 1]
            if (ok && payload.data && payload.data.content) {
                if (aiMessage) {
                    aiMessage.content += payload.data.content
                }
            } else if (!ok) {
                // 后端统一响应体的提示字段是 msg（不是 message）
                handleError(payload.msg || 'AI回复失败')
            }
        },
        onerror: (err) => {
            // 收到 done 事件后主动 abort 不算错误
            if (err && err.name === 'AbortError') return
            handleError(err && err.message ? err.message : 'AI回复失败，请重试')
            throw err
        },
        onclose: () => {
            // 流结束（正常或异常）后刷新一次情绪分析
            if (currentSession.value && currentSession.value.sessionId) {
                loadSessionEmotion(currentSession.value.sessionId)
            }
        }
    })

}

// 错误处理函数：优先展示后端返回的提示（SSE error 事件的 msg）
const handleError = (message) => {
    const text = typeof message === 'string' && message.trim() ? message : 'AI回复失败，请重试'
    // 当前会话的AI消息
    const aiMessage = messages.value[messages.value.length - 1]
    if (aiMessage) {
        aiMessage.content = text
    }
    isAiTyping.value = false
    ElMessage.error(text)
}

const getSessionPage = () => {
    getSessionList({
        pageNum: 1,
        pageSize: 10
    }).then(res => {
        sessionList.value = (res && res.records) || []
    }).catch(() => {
        sessionList.value = []
    })
}

// 获取会话数据
const handleSessionClick = (session) => {
    // 点击会话时，获取会话详情
    getSessionDetail(session.id).then(res => {
        messages.value = res || []
    }).catch(() => {
        messages.value = []
    })
    loadSessionEmotion(session.id)
    // 更新当前会话对象数据
    const sessionData = {
        sessionId: "session_" + session.id,
        status: 'ACTIVE',
        sessionTitle: session.sessionTitle
    }
    currentSession.value = sessionData
}

const handleDeleteSession = (sessionId) => {
    ElMessageBox.confirm('确定删除这个会话吗？删除后对话记录不可恢复。', '删除确认', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(() => {
        deleteSession(sessionId).then(() => {
            ElMessage.success('删除成功')
            getSessionPage()
        }).catch(() => {
            // 失败提示已由 request.js 统一处理
        })
    }).catch(() => {
        // 用户取消，什么也不做
    })
}

// 简单的换行逻辑
const formatMessageContent = (content) => {
    return content.replace(/\n/g, '<br>')
}

onMounted(() => {
    // 初始化时获取会话列表
    getSessionPage()
    // 初始化时创建一个新会话
    createNewFrontendSession()
})
</script>
<style scoped lang="scss">
.consultation-container {
    width: 100%;
    max-width: var(--mh-content-max);
    margin: 0 auto;
    padding: var(--mh-space-5);
    display: flex;
    align-items: flex-start;
    gap: var(--mh-space-5);
}

/* ── 左栏 ───────────────────────────────────────────────── */
.sidebar {
    width: 320px;
    flex-shrink: 0;
    position: sticky;
    top: calc(var(--mh-header-height) + var(--mh-space-4));
    max-height: calc(100vh - var(--mh-header-height) - var(--mh-space-8));
    overflow-y: auto;
    padding-right: 4px;
    display: flex;
    flex-direction: column;
    gap: var(--mh-space-4);
}

/* AI 助手信息卡 */
.ai-assistant-info {
    background: var(--mh-surface);
    border: 1px solid var(--mh-border);
    border-radius: var(--mh-radius-lg);
    box-shadow: var(--mh-shadow-sm);
    padding: var(--mh-space-5) var(--mh-space-4);
    text-align: center;

    .breathing-circle {
        width: 56px;
        height: 56px;
        margin: 0 auto var(--mh-space-3);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: linear-gradient(135deg, var(--mh-primary-light-3) 0%, var(--mh-primary) 100%);
        box-shadow: var(--mh-shadow-primary);
        animation: breathing 4s ease-in-out infinite;
    }

    .assistant-name {
        font-size: var(--mh-font-4);
        font-weight: 600;
        color: var(--mh-text-1);
        margin: 0 0 var(--mh-space-2);
    }

    .online-status {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        font-size: var(--mh-font-1);
        font-weight: 600;
        color: var(--mh-mint-dark-2);

        .status-dot {
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background: var(--mh-mint);
            animation: pulse 2s infinite;
        }
    }
}

/* 情绪花园 */
.emotion-garden {
    background: linear-gradient(160deg, var(--mh-warm-light-9) 0%, #f7f4ef 100%);
    border: 1px solid var(--mh-border);
    border-radius: var(--mh-radius-lg);
    box-shadow: var(--mh-shadow-sm);
    padding: var(--mh-space-4);

    .garden-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: var(--mh-space-4);

        .garden-title {
            font-size: var(--mh-font-4);
            font-weight: 600;
            color: var(--mh-warm-dark-2);
        }
    }

    /* 白字压在中等饱和度底色上，对比度足够（旧版是白字压浅粉渐变，几乎看不清） */
    .emotion-info {
        width: 84px;
        height: 84px;
        margin: 0 auto var(--mh-space-4);
        border-radius: 50%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        color: #fff;
        border: 3px solid rgba(255, 255, 255, 0.85);
        box-shadow: var(--mh-shadow-sm);
        background: linear-gradient(135deg, var(--mh-mint-light-3) 0%, var(--mh-mint-dark-2) 100%);

        &.negative {
            background: linear-gradient(135deg, var(--mh-warm) 0%, var(--mh-warm-dark-2) 100%);
        }

        .emotion-name {
            font-size: var(--mh-font-3);
            font-weight: 600;
            line-height: 1.2;
        }

        .emotion-score {
            font-size: var(--mh-font-5);
            font-weight: 700;
            line-height: 1.1;
        }
    }

    .warm-tips {
        text-align: center;

        .emotion-status-text {
            margin-bottom: var(--mh-space-3);

            .status-label {
                font-size: var(--mh-font-3);
                color: var(--mh-text-3);
                margin-right: 6px;
            }

            .status-emotion {
                display: inline-block;
                padding: 3px 12px;
                border-radius: var(--mh-radius-pill);
                font-size: var(--mh-font-3);
                font-weight: 600;

                &.is-positive {
                    color: var(--mh-mint-dark-2);
                    background: var(--mh-mint-light-8);
                }

                &.is-negative {
                    color: var(--mh-warm-dark-2);
                    background: var(--mh-warm-light-9);
                }
            }
        }

        .emotion-intensity {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: var(--mh-space-2);
            margin-bottom: var(--mh-space-4);

            .intensity-dots {
                display: flex;
                gap: 4px;

                .dot {
                    width: 8px;
                    height: 8px;
                    border-radius: 50%;
                    background: var(--mh-border-strong);
                    transition: all 0.25s ease;

                    &.active {
                        background: var(--mh-warm);
                        transform: scale(1.15);
                    }
                }
            }

            .intensity-text {
                font-size: var(--mh-font-2);
                font-weight: 500;
                color: var(--mh-text-2);
            }
        }

        .warm-suggestion,
        .healing-actions,
        .risk-notice {
            text-align: left;
        }

        .warm-suggestion {
            display: flex;
            align-items: flex-start;
            gap: var(--mh-space-3);
            padding: var(--mh-space-3);
            margin-bottom: var(--mh-space-3);
            background: rgba(255, 255, 255, 0.9);
            border: 1px solid var(--mh-border);
            border-radius: var(--mh-radius);
            box-shadow: var(--mh-shadow-xs);

            .suggestion-icon {
                font-size: 18px;
                line-height: 1.2;
                flex-shrink: 0;
            }

            .suggestion-content {
                flex: 1;
                min-width: 0;

                .suggestion-title {
                    font-size: var(--mh-font-3);
                    font-weight: 600;
                    color: var(--mh-text-1);
                    margin-bottom: 4px;
                }

                .suggestion-text {
                    font-size: var(--mh-font-2);
                    line-height: 1.7;
                    color: var(--mh-text-2);
                }
            }
        }

        .healing-actions {
            margin-bottom: var(--mh-space-3);

            .actions-title {
                font-size: var(--mh-font-3);
                font-weight: 600;
                color: var(--mh-text-1);
                margin-bottom: var(--mh-space-2);
            }

            .actions-list {
                display: flex;
                flex-direction: column;
                gap: var(--mh-space-2);

                .action-item {
                    display: flex;
                    align-items: flex-start;
                    gap: var(--mh-space-2);
                    padding: var(--mh-space-2) var(--mh-space-3);
                    background: rgba(255, 255, 255, 0.9);
                    border: 1px solid var(--mh-border);
                    border-radius: var(--mh-radius-sm);
                    box-shadow: var(--mh-shadow-xs);

                    .action-icon {
                        font-size: 13px;
                        flex-shrink: 0;
                    }

                    .action-text {
                        flex: 1;
                        font-size: var(--mh-font-2);
                        line-height: 1.6;
                        color: var(--mh-text-2);
                    }
                }
            }
        }

        .risk-notice {
            display: flex;
            align-items: flex-start;
            gap: var(--mh-space-3);
            padding: var(--mh-space-3);
            background: var(--mh-warm-light-9);
            border: 1px solid var(--mh-warm);
            border-radius: var(--mh-radius);

            .notice-icon {
                font-size: 18px;
                line-height: 1.2;
                flex-shrink: 0;
            }

            .notice-content {
                flex: 1;
                min-width: 0;
                text-align: left;

                .notice-title {
                    font-size: var(--mh-font-3);
                    font-weight: 600;
                    color: var(--mh-warm-dark-2);
                    margin-bottom: 4px;
                }

                .notice-text {
                    font-size: var(--mh-font-2);
                    line-height: 1.7;
                    color: var(--mh-text-2);
                }
            }
        }
    }
}

/* 会话列表 */
.session-history {
    background: var(--mh-surface);
    border: 1px solid var(--mh-border);
    border-radius: var(--mh-radius-lg);
    box-shadow: var(--mh-shadow-sm);
    padding: var(--mh-space-4);

    .section-title {
        font-size: var(--mh-font-4);
        font-weight: 600;
        color: var(--mh-text-1);
        margin: 0 0 var(--mh-space-3);
    }

    .session-list {
        display: flex;
        flex-direction: column;
        gap: 6px;

        .no-sessions-text {
            text-align: center;
            font-size: var(--mh-font-2);
            color: var(--mh-text-3);
            padding: var(--mh-space-4) 0;
        }

        .session-item {
            position: relative;
            padding: var(--mh-space-3);
            border: 1px solid var(--mh-border);
            border-radius: var(--mh-radius);
            background: var(--mh-surface);
            cursor: pointer;
            transition: all 0.2s ease;

            &:hover {
                border-color: var(--mh-primary-light-5);
                background: var(--mh-primary-light-9);
            }

            &.active {
                border-color: var(--mh-primary);
                background: var(--mh-primary-light-9);
            }

            .session-info {
                min-width: 0;
            }

            /* 省略号只作用在标题文字本身：旧实现把它加在 .session-title 上，
               而时间/预览/统计都在这个容器里面，导致整块内容被一起裁掉 */
            .session-title {
                font-size: var(--mh-font-3);
                font-weight: 600;
                color: var(--mh-text-1);

                > span {
                    display: block;
                    overflow: hidden;
                    white-space: nowrap;
                    text-overflow: ellipsis;
                }
            }

            .session-meta {
                margin-top: 2px;

                .session-time {
                    font-size: var(--mh-font-1);
                    color: var(--mh-text-3);
                }
            }

            .session-preview {
                margin-top: 4px;
                font-size: var(--mh-font-2);
                color: var(--mh-text-2);
                overflow: hidden;
                white-space: nowrap;
                text-overflow: ellipsis;
            }

            .session-stats {
                display: flex;
                align-items: center;
                gap: var(--mh-space-3);
                margin-top: 6px;

                span {
                    display: flex;
                    align-items: center;
                    gap: 4px;
                    font-size: var(--mh-font-1);
                    color: var(--mh-text-3);
                }
            }

            /* 删除按钮平时淡出、hover 才明显，避免误点 */
            .session-actions {
                position: absolute;
                top: 6px;
                right: 8px;
                opacity: 0;
                transition: opacity 0.2s ease;
            }

            &:hover .session-actions {
                opacity: 1;
            }
        }
    }
}

/* ── 右侧聊天区 ─────────────────────────────────────────── */
.chat-main {
    flex: 1;
    min-width: 0;
    height: calc(100vh - 140px);
    min-height: 560px;
    display: flex;
    flex-direction: column;
    background: var(--mh-surface);
    border: 1px solid var(--mh-border);
    border-radius: var(--mh-radius-lg);
    box-shadow: var(--mh-shadow-sm);
    overflow: hidden;

    .chat-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--mh-space-4) var(--mh-space-5);
        background: linear-gradient(135deg, var(--mh-primary) 0%, var(--mh-primary-dark-2) 100%);
        color: var(--mh-text-inverse);
        flex-shrink: 0;

        .header-left {
            display: flex;
            align-items: center;
            gap: var(--mh-space-3);

            .chat-avatar {
                width: 44px;
                height: 44px;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                background: rgba(255, 255, 255, 0.22);
            }

            .chat-info {
                h2 {
                    font-size: var(--mh-font-5);
                    font-weight: 600;
                    color: var(--mh-text-inverse);
                    margin-bottom: 2px;
                }

                p {
                    font-size: var(--mh-font-2);
                    color: rgba(255, 255, 255, 0.85);
                }
            }
        }

        .el-button.is-circle {
            background: rgba(255, 255, 255, 0.2);
            border-color: transparent;
            color: var(--mh-text-inverse);

            &:hover {
                background: rgba(255, 255, 255, 0.32);
            }
        }
    }

    .chat-messages {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        padding: var(--mh-space-5);
        display: flex;
        flex-direction: column;
        gap: var(--mh-space-4);
        background: linear-gradient(180deg, var(--mh-surface) 0%, var(--mh-surface-soft) 100%);

        .message-item {
            display: flex;
            align-items: flex-start;
            gap: var(--mh-space-3);

            /* 用户消息整行反向排布并换底色（旧实现只改了头像底色，两种消息挤在同一侧分不清） */
            &.user-message {
                flex-direction: row-reverse;

                .message-avatar {
                    background: linear-gradient(135deg, var(--mh-mint-light-3) 0%, var(--mh-mint-dark-2) 100%);
                }

                .message-content {
                    align-items: flex-end;

                    .message-bubble {
                        background: var(--mh-primary-light-9);
                        border-color: var(--mh-primary-light-7);
                        border-radius: var(--mh-radius-lg) 4px var(--mh-radius-lg) var(--mh-radius-lg);
                    }

                    .message-time {
                        text-align: right;
                    }
                }
            }

            &.ai-message {
                .message-avatar {
                    background: linear-gradient(135deg, var(--mh-primary-light-3) 0%, var(--mh-primary) 100%);
                }
            }

            .message-avatar {
                width: 32px;
                height: 32px;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                flex-shrink: 0;
            }

            .message-content {
                display: flex;
                flex-direction: column;
                max-width: 74%;
                min-width: 0;

                .message-bubble {
                    padding: var(--mh-space-3) var(--mh-space-4);
                    background: var(--mh-surface);
                    border: 1px solid var(--mh-border);
                    border-radius: 4px var(--mh-radius-lg) var(--mh-radius-lg) var(--mh-radius-lg);
                    box-shadow: var(--mh-shadow-xs);
                    animation: fadeInUp 0.32s ease-out;
                    overflow-x: auto;

                    /* Markdown / v-html 内容的基础排版 */
                    :deep(p) {
                        margin: 0 0 8px;
                        line-height: 1.75;
                    }

                    :deep(p:last-child) {
                        margin-bottom: 0;
                    }

                    :deep(pre) {
                        background: var(--mh-surface-soft);
                        border: 1px solid var(--mh-border);
                        border-radius: var(--mh-radius-sm);
                        padding: var(--mh-space-3);
                        overflow-x: auto;
                    }

                    :deep(code) {
                        font-size: var(--mh-font-2);
                    }

                    .typing-indicator {
                        display: flex;
                        gap: 5px;
                        padding: 4px 0;

                        .typing-dot {
                            width: 7px;
                            height: 7px;
                            border-radius: 50%;
                            background: var(--mh-primary-light-3);
                            animation: typing 1.4s ease-in-out infinite;

                            &:nth-child(2) {
                                animation-delay: 0.2s;
                            }

                            &:nth-child(3) {
                                animation-delay: 0.4s;
                            }
                        }
                    }

                    .error-message {
                        display: flex;
                        align-items: center;
                        gap: var(--mh-space-2);
                        color: var(--mh-danger);
                        font-weight: 500;
                    }
                }

                .message-time {
                    margin-top: 6px;
                    font-size: var(--mh-font-1);
                    color: var(--mh-text-3);
                }
            }
        }
    }

    .chat-input {
        display: flex;
        align-items: flex-end;
        gap: var(--mh-space-3);
        padding: var(--mh-space-4) var(--mh-space-5);
        border-top: 1px solid var(--mh-border);
        background: var(--mh-surface);
        flex-shrink: 0;

        .input-container {
            flex: 1;
            min-width: 0;
        }

        .input-footer {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-top: 6px;
            font-size: var(--mh-font-1);
            color: var(--mh-text-3);
        }

        .send-btn {
            height: 56px;
            width: 56px;
            border-radius: var(--mh-radius);
            flex-shrink: 0;
        }
    }
}

/* ── 动画 ──────────────────────────────────────────────────
   旧代码引用了 breathing / pulse / typing / fadeInUp 四个动画，
   但全项目没有一处 @keyframes（等于全是静止的，思考中的三个点看起来像卡死）。 */
@keyframes breathing {
    0%, 100% {
        transform: scale(1);
    }
    50% {
        transform: scale(1.06);
    }
}

@keyframes pulse {
    0%, 100% {
        opacity: 1;
        box-shadow: 0 0 0 0 rgba(127, 182, 158, 0.5);
    }
    50% {
        opacity: 0.7;
        box-shadow: 0 0 0 6px rgba(127, 182, 158, 0);
    }
}

@keyframes typing {
    0%, 100% {
        transform: translateY(0);
        opacity: 0.5;
    }
    50% {
        transform: translateY(-3px);
        opacity: 1;
    }
}

@keyframes fadeInUp {
    from {
        opacity: 0;
        transform: translateY(6px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}
</style>
