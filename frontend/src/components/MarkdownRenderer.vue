<template>
  <div class="markdown-content" :class="{ 'ai-markdown': isAiMessage }">
    <div v-html="renderedContent"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  content: {
    type: String,
    required: true
  },
  isAiMessage: {
    type: Boolean,
    default: false
  }
})

// 简单的Markdown渲染器
const renderedContent = computed(() => {
  let html = props.content

  // 转义HTML标签（防止XSS）
  html = html.replace(/</g, '&lt;').replace(/>/g, '&gt;')

  // 处理代码块（```）
  html = html.replace(/```(\w+)?\n([\s\S]*?)\n```/g, (match, lang, code) => {
    return `<pre class="code-block"><code class="language-${lang || 'text'}">${code.trim()}</code></pre>`
  })

  // 处理行内代码（`）
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')

  // 处理粗体（**）
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')

  // 处理斜体（*）
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>')

  // 处理标题
  html = html.replace(/^### (.*$)/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.*$)/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.*$)/gm, '<h1>$1</h1>')

  // 处理链接
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')

  // 处理无序列表
  html = html.replace(/^- (.*)$/gm, '<li>$1</li>')
  html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')

  // 处理有序列表
  html = html.replace(/^\d+\. (.*)$/gm, '<li>$1</li>')

  // 处理引用
  html = html.replace(/^> (.*)$/gm, '<blockquote>$1</blockquote>')

  // 处理分割线
  html = html.replace(/^---$/gm, '<hr>')

  // 处理换行
  html = html.replace(/\n/g, '<br>')

  // 清理多余的br标签
  html = html.replace(/<br><br>/g, '<br>')

  return html
})
</script>

<style scoped>
.markdown-content {
  line-height: 1.6;
  color: inherit;
}

.markdown-content h1,
.markdown-content h2,
.markdown-content h3 {
  margin: 1em 0 0.5em 0;
  font-weight: 600;
  line-height: 1.3;
}

.markdown-content h1 {
  font-size: 1.5em;
  border-bottom: 2px solid var(--mh-border);
  padding-bottom: 0.3em;
}

.markdown-content h2 {
  font-size: 1.3em;
  color: var(--mh-text-1);
}

.markdown-content h3 {
  font-size: 1.1em;
  color: var(--mh-text-2);
}

.markdown-content p {
  margin: 0.5em 0;
}

.markdown-content ul,
.markdown-content ol {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.markdown-content li {
  margin: 0.3em 0;
}

.markdown-content blockquote {
  border-left: 4px solid var(--mh-border-strong);
  padding-left: 1em;
  margin: 1em 0;
  color: var(--mh-text-3);
  font-style: italic;
  background: var(--mh-surface-soft);
  border-radius: 0 0.5em 0.5em 0;
  padding: 0.5em 1em;
}

.ai-markdown blockquote {
  border-left-color: var(--mh-primary);
  background: var(--mh-primary-light-9);
}

.markdown-content hr {
  border: none;
  border-top: 2px solid var(--mh-border);
  margin: 1.5em 0;
}

.markdown-content code.inline-code {
  background: var(--mh-surface-soft);
  padding: 0.2em 0.4em;
  border-radius: 0.25em;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.85em;
  color: var(--mh-danger);
}

.ai-markdown code.inline-code {
  background: var(--mh-primary-light-8);
  color: var(--mh-primary-dark-2);
}

.markdown-content pre.code-block {
  background: var(--mh-text-1);
  color: #eef3f5;
  padding: 1em;
  border-radius: 0.5em;
  overflow-x: auto;
  margin: 1em 0;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.85em;
  line-height: 1.4;
}

.markdown-content pre.code-block code {
  background: none;
  padding: 0;
  color: inherit;
}

.markdown-content a {
  color: var(--mh-primary);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.2s ease;
}

.markdown-content a:hover {
  border-bottom-color: var(--mh-primary);
}

.ai-markdown a {
  color: var(--mh-primary-dark-2);
}

.ai-markdown a:hover {
  border-bottom-color: var(--mh-primary-dark-2);
}

.markdown-content strong {
  font-weight: 600;
  color: var(--mh-text-1);
}

.ai-markdown strong {
  color: var(--mh-primary-dark-2);
}

.markdown-content em {
  font-style: italic;
  color: var(--mh-text-3);
}
</style>





