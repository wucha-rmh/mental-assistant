<template>
    <el-dialog
        :title="isEdit ? '编辑文章' : '新增文章'"
        v-model="dialogVisible"
        width="720px"
        top="6vh"
        destroy-on-close
        @close="handleClose"
    >
        <el-alert
            v-if="!isEdit"
            class="draft-tip"
            type="info"
            :closable="false"
            show-icon
            title="新建的文章先存为草稿，需在列表里点「发布」后，前台才会展示"
        />
        <el-form :model="formData" :rules="rules" ref="formRef" label-width="120px">
            <el-form-item label="文章标题" prop="title">
                <el-input v-model="formData.title" placeholder="请输入文章标题" maxlength="200" show-word-limit clearable />
            </el-form-item>
            <el-form-item label="所属分类" prop="categoryId">
                <el-select v-model="formData.categoryId" placeholder="请选择分类">
                    <el-option v-for="item in props.categories" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
            </el-form-item>
            <el-form-item label="文章摘要" prop="summary">
                <el-input type="textarea" v-model="formData.summary" placeholder="请输入文章摘要(可选)" maxlength="2000" show-word-limit :rows="4" />
            </el-form-item>
            <el-form-item label="标签" prop="tags">
                <el-select v-model="formData.tagArray" placeholder="请输入文章标签(逗号分隔)" multiple filterable allow-create style="width: 100%">
                    <el-option v-for="tag in commonTags" :key="tag" :label="tag" :value="tag" />
                </el-select>
            </el-form-item>
            <el-form-item label="封面图片">
                <div class="cover-upload">
                    <el-upload
                        class="avatar-uploader"
                        action="#"
                        :before-upload="beforeUpload"
                        :http-request="handleUploadRequest"
                        :show-file-list="false"
                        accept=".jpg,.jpeg,.png,.gif,.bmp,.webp"
                    >
                        <div v-if="!imgUrl" class="cover-placeholder">
                            <p>点击上传封面</p>
                        </div>
                        <img v-else :src="imgUrl" class="cover-image" alt="封面图片" />
                    </el-upload>
                    <div v-if="imgUrl" class="cover-remove">
                        <el-button type="danger" size="small" @click="handleRemove">移除封面</el-button>
                    </div>
                </div>
            </el-form-item>
            <el-form-item label="文章内容" prop="content">
                <RichTextEditor
                    v-model="formData.content"
                    placeholder="请输入文章内容，支持富文本格式\n\n可以使用加粗、斜体、列表、标题等格式来丰富文章内容。"
                    :maxCharCount="20000"
                    @change="handleContentChange"
                    @created="handleEditorCreated"
                    :imageUploader="handleContentImageUpload"
                    min-height="400px"
                    />
            </el-form-item>
        </el-form>
        <div v-if="btnPreview">
            <h3>内容预览</h3>
            <div v-html="formData.content"></div>
        </div>
        <template #footer>
            <el-button @click="btnPreview = !btnPreview">{{ btnPreview ? '隐藏预览' : '预览效果' }}</el-button>
            <el-button @click="handleClose">取消</el-button>
            <el-button type="primary" @click="handleSubmit" :loading="loading">{{ isEdit ? '更新文章' : '保存为草稿' }}</el-button>
        </template>
    </el-dialog>
</template>
<script setup>
import { ref, reactive, computed, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile, uploadContentImage, createArticle, updateArticle } from '@/api/admin'
import { fileBaseUrl } from '@/config/index.js'
import RichTextEditor from '@/components/RichTextEditor.vue'

const props = defineProps({
    modelValue: {
        type: Boolean,
        default: false
    },
    categories: {
        type: Array,
        default: () => []
    },
    article: {
        type: Object,
        default: null
    }
})

const emit = defineEmits(['update:modelValue', 'success'])

const dialogVisible = computed({
    get() {
        return props.modelValue
    },
    set(val) {
        emit('update:modelValue', val)
    }
})

const isEdit = computed(() => !!props.article?.id)

// 监听编辑数据
watch(() => props.article, (newVal) => {
    if (newVal) {
        nextTick(() => {
            Object.assign(formData, newVal)
            // 使用现有ID
            businessId.value = newVal.id
            // 封面Url
            imgUrl.value = fileBaseUrl + newVal.coverImage
        })
    }
})

const handleClose = () => {
    // 重置表单
    formRef.value.resetFields()
    // 重置ID
    businessId.value = null
    // 重置标签
    formData.tagArray = []
    // 重置封面图片和数据
    handleRemove()
    emit('update:modelValue', false)
}

// 表单数据
const formData = reactive({
    "title": "",
    "content": "",
    "coverImage": "",
    "categoryId": "",
    "summary": "",
    "tags": "",
    "id": "",
    // 标签选择器 v-model 绑的是这个数组；必须预先声明，
    // 否则「一个标签都没选就提交」时 formData.tagArray.join(',') 会抛 TypeError、请求根本发不出去
    "tagArray": []
})

const rules = reactive({
    title: [
        { required: true, message: '请输入文章标题', trigger: 'blur' },
        { max: 200, message: '文章标题最多200个字符', trigger: 'blur' }
    ],
    categoryId: [
        { required: true, message: '请选择分类', trigger: 'change' }
    ],
    content: [
        { required: true, message: '请输入文章内容', trigger: 'blur' },
        { max: 20000, message: '文章内容最多20000个字符', trigger: 'blur' }
    ],
})

const commonTags = [
  '情绪管理', '焦虑', '抑郁', '压力', '睡眠', 
  '冥想', '正念', '放松', '心理健康', '自我成长',
  '人际关系', '工作压力', '学习方法', '生活技巧'
]

// 上传
const imgUrl = ref('')
const beforeUpload = (file) => {
    // 针对上传的文件进行校验
    console.log(file)
    const isImage = file.type.startsWith('image/')
    const isLt5M = file.size / 1014 / 1014 < 5
    if (!isImage) {
        ElMessage.error('上传封面图片，请选择图片文件')
        return false
    }
    if (!isLt5M) {
        ElMessage.error('上传封面图片，图片大小不能超过5MB')
        return false
    }
    return true
}
const businessId = ref(null)
const handleUploadRequest = async ({ file }) => {
    // UUID生成
    businessId.value = crypto.randomUUID()
    const fileRes = await uploadFile(file, {
        businessId: businessId.value
    })
    console.log(fileRes)

    // 拼接完整的图片地址
    imgUrl.value = fileBaseUrl + fileRes.filePath
    formData.coverImage = fileRes.filePath
}

const handleRemove = () => {
    imgUrl.value = ''
    formData.coverImage = ''
}

/**
 * 正文插图上传：交给 RichTextEditor 的 uploadImage 菜单调用。
 * 返回的是可直接放进 <img src> 的地址（fileBaseUrl 目前是空串 = 同源 /files/...）。
 */
const handleContentImageUpload = async (file) => {
    const fileRes = await uploadContentImage(file)
    return fileBaseUrl + fileRes.filePath
}

// 富文本
const handleContentChange = (data) => {
    console.log(data, '富文本内容')
    formData.content = data.html
}

const editorInstance = ref(null)
const handleEditorCreated = (editor) => {
    editorInstance.value = editor
    // 编辑
    if (formData.content && editor) {
        nextTick(() => {
            editor.setHtml(formData.content)
        })
    }
}

const btnPreview = ref(false)

// 提交
const formRef = ref()
const loading = ref(false)
const handleSubmit = () => {
    formRef.value.validate((valid) => {
        if (!valid) {
            // 校验不通过就不要发请求（后端也会校验，但没必要多打一次）
            return
        }
        loading.value = true
        const tagList = Array.isArray(formData.tagArray) ? formData.tagArray : []
        const submitData = {
            ...formData,
            tags: tagList.join(',')
        }
        delete submitData.tagArray

        if (!isEdit.value) {
            submitData.id = businessId.value
            createArticle(submitData).then(res => {
                loading.value = false
                emit('success')
            }).catch(() => {
                // 错误提示由请求拦截器统一弹出，这里只负责结束加载状态
                loading.value = false
            })
        } else {
            updateArticle(props.article.id, submitData).then(res => {
                loading.value = false
                emit('success')
            }).catch(() => {
                loading.value = false
            })
        }
    })
}
</script>
<style lang="scss" scoped>
.draft-tip {
    margin-bottom: var(--mh-space-4);
}
.cover-placeholder {
    width: 200px;
    height: 120px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    color: var(--mh-text-3);
    background: var(--mh-surface-soft);
    border: 1px dashed var(--mh-border-strong);
    border-radius: var(--mh-radius-sm);
    font-size: var(--mh-font-2);
}
.cover-image {
    width: 200px;
    height: 120px;
    display: block;
    border-radius: var(--mh-radius-sm);
    object-fit: cover;
}
</style>
