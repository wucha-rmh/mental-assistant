<template>
  <div class="dashboard-container" v-loading="loading">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card v-if="aiData.systemOverview">
          <div class="card-content">
            <div class="avatar users">
              <el-image style="width: 40px; height: 40px" :src="iconUrl1" />
            </div>
            <div class="info">
              <p class="title">总用户数</p>
              <p class="number">{{ aiData.systemOverview.totalUsers }}</p>
              <p class="subtitle-title">活跃用户：{{ aiData.systemOverview.activeUsers }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card v-if="aiData.systemOverview">
          <div class="card-content">
            <div class="avatar like">
              <el-image style="width: 40px; height: 40px" :src="iconUrl2" />
            </div>
            <div class="info">
              <p class="title">情绪日志</p>
              <p class="number">{{ aiData.systemOverview.totalDiaries }}</p>
              <p class="subtitle-title">今日新增：{{ aiData.systemOverview.todayNewDiaries }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card v-if="aiData.systemOverview">
          <div class="card-content">
            <div class="avatar comments">
              <el-image style="width: 40px; height: 40px" :src="iconUrl3" />
            </div>
            <div class="info">
              <p class="title">咨询会话</p>
              <p class="number">{{ aiData.systemOverview.totalSessions }}</p>
              <p class="subtitle-title">今日新增：{{ aiData.systemOverview.todayNewSessions }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card v-if="aiData.systemOverview">
          <div class="card-content">
            <div class="avatar smile">
              <el-image style="width: 40px; height: 40px" :src="iconUrl4" />
            </div>
            <div class="info">
              <p class="title">平均情绪</p>
              <p class="number">{{ aiData.systemOverview.avgMoodScore }}/10</p>
              <p class="subtitle-title">情绪健康指数</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row class="chart-row" :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              情绪趋势分析
            </div>
          </template>
          <div class="chart-content">
            <div ref="emotionChartRef" style="width: 100%; height:300px"></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              咨询会话统计
            </div>
          </template>
          <div class="chart-content">
            <div v-if="aiData.consultationStats" class="consultation-stats">
              <div class="stat-item">
                <div class="stat-label">总会话数</div>
                <div class="stat-value">{{ aiData.consultationStats.totalSessions }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-label">平均时长</div>
                <div class="stat-value">{{ aiData.consultationStats.avgDurationMinutes }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-label">活跃用户</div>
                <div class="stat-value">{{ aiData.systemOverview.activeUsers }}</div>
              </div>
            </div>
            <div ref="consultationChartRef" style="width: 100%; height:260px"></div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row class="chart-row">
      <el-card style="width: 100%">
        <template #header>
          <div class="card-header">
            用户活跃度趋势
          </div>
        </template>
        <div class="chart-content">
          <div ref="userActivityChartRef" style="width: 100%; height:300px"></div>
        </div>
      </el-card>
    </el-row>
    <el-empty v-if="!loading && !hasData" description="暂无统计数据" />
  </div>
</template>
<script setup>
import { getAnalyticsOverview } from '@/api/admin'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'

// 统计图片引入
const iconUrl1 = new URL('@/assets/images/users.png', import.meta.url).href
const iconUrl2 = new URL('@/assets/images/like.png', import.meta.url).href
const iconUrl3 = new URL('@/assets/images/comments.png', import.meta.url).href
const iconUrl4 = new URL('@/assets/images/smile.png', import.meta.url).href

const aiData = ref({})
const loading = ref(false)
const hasData = computed(() => !!aiData.value.systemOverview)

// 初始化图标
const initCharts = () => {
  initEmotionChart()
  initConsultationChart()
  initUserActivityChart()
}

// 情绪趋势
let emotionChart = null
const emotionChartRef = ref(null)
const initEmotionChart = () => {
  if (!emotionChartRef.value) return
  // 销毁现有的图表
  if (emotionChart) {
    emotionChart.dispose()
  }
  // 创建echarts实例
  emotionChart = echarts.init(emotionChartRef.value)
  // 获取情绪趋势的数据
  const TrendData = aiData.value.emotionTrend
  // 配置项
  const option = {
    // 标题已由卡片 header 承担，图表内不再重复渲染 title
    color: ['#7fb69e', '#4e8098'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: '#e6edf1',
      borderWidth: 1,
      textStyle: {
        color: '#2f3e46'
      }
    },
    legend: {
      data: ['平均情绪评分', '记录数量'],
      top: 6,
      textStyle: {
        color: '#5c6b73'
      }
    },
    grid: { // 控制容器样式
      left: '3%',
      right: '4%',
      top: 48,
      bottom: '3%'
    },
    xAxis: {
      type: 'category',
      data: TrendData.map(item => item.date),
      axisLine: {
        lineStyle: {
          color: '#d5e0e6'
        }
      },
      axisLabel: {
        color: '#8a9aa3'
      }
    },
    yAxis: [{
      type: 'value',
      name: '情绪评分',
      position: 'left',
      nameTextStyle: {
        color: '#8a9aa3'
      },
      axisLine: {
        lineStyle: {
          color: '#d5e0e6'
        }
      },
      axisLabel: {
        color: '#8a9aa3'
      },
      splitLine: {
        lineStyle: {
          color: '#eef3f5'
        }
      }
    }, {
      type: 'value',
      name: '记录数量',
      position: 'right',
      nameTextStyle: {
        color: '#8a9aa3'
      },
      axisLine: {
        lineStyle: {
          color: '#d5e0e6'
        }
      },
      axisLabel: {
        color: '#8a9aa3'
      },
      splitLine: {
        show: false
      }
    }],
    series: [{
      name: '平均情绪评分',
      type: 'line',
      data: TrendData.map(item => item.avgMoodScore),
      smooth: true,
      symbolSize: 6,
      lineStyle: {
        width: 3,
        color: '#7fb69e'
      },
      itemStyle: {
        color: '#7fb69e'
      },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(127, 182, 158, 0.28)' },
            { offset: 1, color: 'rgba(127, 182, 158, 0.02)' }
          ]
        }
      }
    },
    {
      name: '记录数量',
      type: 'line',
      data: TrendData.map(item => item.recordCount),
      smooth: true,
      symbolSize: 6,
      lineStyle: {
        width: 3,
        color: '#4e8098'
      },
      itemStyle: {
        color: '#4e8098'
      }
    }]
  }

  emotionChart.setOption(option)
}

// 咨询会话统计
let consultationChart = null
const consultationChartRef = ref(null)
const initConsultationChart = () => {
  if (!consultationChartRef.value) return
  // 销毁现有的图表
  if (consultationChart) {
    consultationChart.dispose()
  }
  // 创建echarts实例
  consultationChart = echarts.init(consultationChartRef.value)
  // 获取数据
  const dailyTrend = aiData.value.consultationStats.dailyTrend
  const option = {
    // 标题由卡片 header 承担
    color: ['#4e8098', '#7fb69e'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: '#e6edf1',
      borderWidth: 1,
      textStyle: {
        color: '#2f3e46'
      }
    },
    legend: {
      data: ['会话数量', '参与用户数'],
      top: 6,
      textStyle: {
        color: '#5c6b73'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: 48,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dailyTrend.map(item => item.date),
      axisLine: {
        lineStyle: {
          color: '#d5e0e6'
        }
      },
      axisLabel: {
        color: '#8a9aa3'
      }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#8a9aa3'
      },
      axisLine: {
        lineStyle: {
          color: '#d5e0e6'
        }
      },
      splitLine: {
        lineStyle: {
          color: '#eef3f5'
        }
      }
    },
    series: [
      {
        name: '会话数量',
        type: 'bar',
        data: dailyTrend.map(item => item.sessionCount),
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#7ba3b8' },
              { offset: 1, color: '#4e8098' }
            ]
          }
        },
        barWidth: '40%'
      },
      {
        name: '参与用户数',
        type: 'bar',
        data: dailyTrend.map(item => item.userCount),
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#9cc8b5' },
              { offset: 1, color: '#7fb69e' }
            ]
          }
        },
        barWidth: '40%'
      }
    ]
  }
  consultationChart.setOption(option)
}

// 用户活跃度分析
let userActivityChart = null
const userActivityChartRef = ref(null)
const initUserActivityChart = () => {
  if (!userActivityChartRef.value) return
  // 销毁现有的图表
  if (userActivityChart) {
    userActivityChart.dispose()
  }
  // 创建echarts实例
  userActivityChart = echarts.init(userActivityChartRef.value)
  // 获取数据
  const activityData = aiData.value.userActivity
  const option = {
    // 标题由卡片 header 承担
    color: ['#4e8098', '#7fb69e', '#e0a87e', '#8a9aa3'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: '#e6edf1',
      borderWidth: 1,
      textStyle: {
        color: '#2f3e46'
      }
    },
    legend: {
      data: ['活跃用户', '新增用户', '日记用户', '咨询用户'],
      top: 6,
      textStyle: {
        color: '#5c6b73'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: 48,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: activityData.map(item => item.date),
      axisLine: {
        lineStyle: {
          color: '#d5e0e6'
        }
      },
      axisLabel: {
        color: '#8a9aa3'
      }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#8a9aa3'
      },
      axisLine: {
        lineStyle: {
          color: '#d5e0e6'
        }
      },
      splitLine: {
        lineStyle: {
          color: '#eef3f5'
        }
      }
    },
    series: [
      {
        name: '活跃用户',
        type: 'line',
        data: activityData.map(item => item.activeUsers),
        smooth: true,
        symbolSize: 5,
        lineStyle: {
          width: 3,
          color: '#4e8098'
        },
        itemStyle: {
          color: '#4e8098'
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(78, 128, 152, 0.28)' },
              { offset: 1, color: 'rgba(78, 128, 152, 0.02)' }
            ]
          }
        }
      },
      {
        name: '新增用户',
        type: 'line',
        data: activityData.map(item => item.newUsers),
        smooth: true,
        symbolSize: 5,
        lineStyle: {
          width: 3,
          color: '#7fb69e'
        },
        itemStyle: {
          color: '#7fb69e'
        }
      },
      {
        name: '日记用户',
        type: 'line',
        data: activityData.map(item => item.diaryUsers),
        smooth: true,
        symbolSize: 5,
        lineStyle: {
          width: 3,
          color: '#e0a87e'
        },
        itemStyle: {
          color: '#e0a87e'
        }
      },
      {
        name: '咨询用户',
        type: 'line',
        data: activityData.map(item => item.consultationUsers),
        smooth: true,
        symbolSize: 5,
        lineStyle: {
          width: 3,
          color: '#8a9aa3'
        },
        itemStyle: {
          color: '#8a9aa3'
        }
      }
    ]
  }
  userActivityChart.setOption(option)
}

// 窗口尺寸变化时重绘；组件卸载时销毁实例（旧实现两样都没有：既会挤压变形，也泄漏实例）
const handleResize = () => {
  emotionChart?.resize()
  consultationChart?.resize()
  userActivityChart?.resize()
}

onMounted(() => {
  loading.value = true
  getAnalyticsOverview()
    .then((res) => {
      aiData.value = res
      initCharts()
    })
    .catch(() => {
      // 失败提示已由 request.js 统一处理，这里保持空布局
    })
    .finally(() => {
      loading.value = false
    })
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  emotionChart?.dispose()
  consultationChart?.dispose()
  userActivityChart?.dispose()
})
</script>
<style lang="scss" scoped>
.dashboard-container {
  .chart-row {
    margin-top: var(--mh-space-5);
  }

  .card-content {
    display: flex;
    align-items: center;

    /* 图标是「白色剪影」的 PNG，所以底色必须保持中深色，只统一色相与饱和度 */
    .avatar {
      flex-shrink: 0;
      margin-right: var(--mh-space-3);
      width: 52px;
      height: 52px;
      border-radius: var(--mh-radius);
      display: flex;
      align-items: center;
      justify-content: center;

      &.users {
        background: linear-gradient(135deg, #7ba3b8 0%, #4e8098 100%);
      }

      &.like {
        background: linear-gradient(135deg, #9cc8b5 0%, #6fae90 100%);
      }

      &.comments {
        background: linear-gradient(135deg, #e8c3a0 0%, #d9a05b 100%);
      }

      &.smile {
        background: linear-gradient(135deg, #b7a9c9 0%, #8a7fa8 100%);
      }
    }

    .info {
      min-width: 0;

      /* ⚠️ 类名必须与模板的 class="number" 一致（旧版写成 .value，4 个 KPI 数字因此完全没样式） */
      .number {
        font-size: var(--mh-font-7);
        font-weight: 700;
        line-height: 1.2;
        color: var(--mh-text-1);
        margin-bottom: 2px;
      }

      .title {
        font-size: var(--mh-font-2);
        color: var(--mh-text-3);
        margin-bottom: 4px;
      }

      .subtitle-title {
        font-size: var(--mh-font-1);
        color: var(--mh-text-3);
      }
    }
  }

  /* 高度交给内部带 px 高度的图表容器（echarts.init 需要能测到高度）：
     这里不要再固定 300px，更不要 canvas{height:100%!important} 把画布压扁 */
  .chart-content {
    padding: var(--mh-space-4) var(--mh-space-5) var(--mh-space-5);
    position: relative;

    .consultation-stats {
      display: flex;
      justify-content: space-around;
      margin-bottom: var(--mh-space-4);

      .stat-item {
        text-align: center;

        .stat-label {
          font-size: var(--mh-font-2);
          color: var(--mh-text-3);
          margin-bottom: 4px;
        }

        .stat-value {
          font-size: var(--mh-font-6);
          font-weight: 600;
          color: var(--mh-primary);
        }
      }
    }
  }
}
</style>
