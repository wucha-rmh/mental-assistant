<template>
    <!--
      头像上传：无可见 DOM，父组件通过 ref 调用 pick() 打开文件选择框。
      管理员导航栏与前台头部共用这一份上传/校验/写 store 的逻辑。
    -->
    <input ref="inputRef" class="avatar-file-input" type="file" accept="image/*" @change="handleChange" />
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadAvatar } from '@/api/frontend'
import { useUserStore } from '@/stores/user'

/** 头像大小上限（MB） */
const MAX_SIZE_MB = 5

const userStore = useUserStore()
const inputRef = ref(null)
const uploading = ref(false)

/** 打开系统文件选择框（由父组件的「更换头像」菜单项调用） */
const pick = () => {
    if (uploading.value) {
        return
    }
    if (inputRef.value) {
        inputRef.value.click()
    }
}

const handleChange = async (event) => {
    const file = event.target.files && event.target.files[0]
    // 先清空 value，否则连续选同一个文件不会再触发 change
    event.target.value = ''
    if (!file) {
        return
    }
    if (!file.type.startsWith('image/')) {
        ElMessage.warning('请选择图片文件')
        return
    }
    if (file.size > MAX_SIZE_MB * 1024 * 1024) {
        ElMessage.warning(`头像不能超过 ${MAX_SIZE_MB}MB`)
        return
    }

    uploading.value = true
    try {
        // 后端返回更新后的用户信息（含新的 avatar 相对路径）
        const info = await uploadAvatar(file)
        userStore.setInfo(info)
        ElMessage.success('头像已更新')
    } catch (e) {
        // 失败提示由 utils/request.js 的响应拦截器统一弹出
    } finally {
        uploading.value = false
    }
}

defineExpose({ pick })
</script>

<style scoped>
.avatar-file-input {
    display: none;
}
</style>
