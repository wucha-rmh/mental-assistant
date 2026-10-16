import { createRouter, createWebHistory } from 'vue-router'
import BackendLayout from '@/components/BackendLayout.vue'
import AuthLayout from '@/components/AuthLayout.vue'
import FrontendLayout from '@/components/FrontendLayout.vue'


// 路由配置
const backendRoutes = [
    {
        path: '/back',
        redirect: '/back/dashboard',
        component: BackendLayout,
        children: [
            {
                path: 'dashboard',
                component: () => import('@/views/dashboard.vue'),
                meta: {
                    title: '数据分析',
                    icon: 'PieChart'
                }
            },
            {
                path: 'knowledge',
                component: () => import('@/views/knowledge.vue'),
                meta: {
                    title: '知识文章',
                    icon: 'ChatLineSquare'
                }
            },
            {
                path: 'consultations',
                component: () => import('@/views/consultations.vue'),
                meta: {
                    title: '咨询记录',
                    icon: 'Message'
                }
            },
            {
                path: 'emotional',
                component: () => import('@/views/emotional.vue'),
                meta: {
                    title: '情绪日志',
                    icon: 'User'
                }
            },
            {
                path: 'users',
                component: () => import('@/views/users.vue'),
                meta: {
                    title: '用户管理',
                    icon: 'UserFilled'
                }
            },
            {
                path: 'feedbacks',
                component: () => import('@/views/feedbacks.vue'),
                meta: {
                    title: '留言管理',
                    icon: 'ChatDotRound'
                }
            }
        ]
    },
    {
        path: '/auth',
        component: AuthLayout,
        children: [
            {
                path: 'login',
                component: () => import('@/views/login.vue'),
                meta: {
                    title: '登录'
                }
            },
            {
                path: 'register',
                component: () => import('@/views/register.vue'),
                meta: {
                    title: '注册'
                }
            }
        ]
    }
]

const frontendRoutes = [
    {
        path: '/',
        component: FrontendLayout,
        children: [
            {
                path: '',
                component: () => import('@/views/home.vue')
            },
            {
                path: 'consultation',
                component: () => import('@/views/consultation.vue')
            },
            {
                path: 'emotion-diary',
                component: () => import('@/views/emotionDiary.vue')
            },
            {
                path: 'knowledge',
                component: () => import('@/views/frontendKnowledge.vue')
            },
            {
                path: 'knowledge/article/:id',
                component: () => import('@/views/articleDetail.vue'),
                props: true
            },
            {
                path: 'profile',
                component: () => import('@/views/profile.vue')
            }
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes: [ ...backendRoutes, ...frontendRoutes]
})

// 路由前置守卫
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    // userInfo 可能缺失或被写坏（例如只清了一半登录态）。解析失败一律按「未登录」处理：
    // 老写法直接 JSON.parse + userInfo.userType，一旦抛错就走到没有 next() 的分支，
    // 整次导航会被静默吞掉 —— 表现就是「点按钮没反应、地址栏也不变」。
    let userInfo = null
    try {
        const parsed = JSON.parse(localStorage.getItem('userInfo') || 'null')
        userInfo = parsed && typeof parsed === 'object' ? parsed : null
    } catch (e) {
        userInfo = null
    }
    const userType = token && userInfo ? Number(userInfo.userType) : NaN

    if (userType === 2) {
        // 管理员只能进后台
        if (to.path.startsWith('/back')) {
            next()
        } else {
            next('/back/dashboard')
        }
    } else if (userType === 1) {
        // 用户端账号只能访问前台路由
        if (to.path.startsWith('/back') || to.path.startsWith('/auth')) {
            next('/')
        } else {
            next()
        }
    } else {
        // 未登录 / 登录信息不完整：后台引导去登录页，其余照常放行
        if (to.path.startsWith('/back')) {
            next('/auth/login')
        } else {
            next()
        }
    }
})

export default router
