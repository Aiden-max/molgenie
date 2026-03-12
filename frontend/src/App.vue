<template>
  <div class="page">
    <header class="header">
      <div class="brand">
        <div class="logo">MG</div>
        <div class="brandText">
          <div class="title">MolGenie</div>
          <div class="subtitle">Drug discovery assistant</div>
        </div>
      </div>

      <nav class="tabs" role="tablist" aria-label="MolGenie tabs">
        <button
          class="tab"
          :class="{ active: activeTab === 'discover' }"
          role="tab"
          :aria-selected="activeTab === 'discover'"
          @click="activeTab = 'discover'"
        >
          发现
        </button>
        <button
          class="tab"
          :class="{ active: activeTab === 'kb' }"
          role="tab"
          :aria-selected="activeTab === 'kb'"
          @click="activeTab = 'kb'"
        >
          知识库
        </button>
      </nav>
    </header>

    <main class="content">
      <section v-if="activeTab === 'discover'" class="card">
        <div class="cardHeader">
          <div class="cardTitle">发现</div>
          <div class="cardHint">
            需求描述（必填） + 分子文件（可选）
            <span class="dot" />
            自动调用 <code>/apiGraph/discover</code> 并联动知识库
          </div>
        </div>

        <div class="form">
          <div class="field">
            <div class="labelText">需求描述</div>
            <textarea
              v-model="query"
              class="textarea"
              rows="6"
              placeholder="例如：帮我分析这批 SDF 中的分子，并给出结构特点、成药性和后续优化建议…"
            />
          </div>

          <div class="field">
            <div class="labelText">上传分子文件（SDF）</div>
            <div class="fileRow">
              <input class="file" type="file" accept=".sdf" @change="onPickFile" />
              <div v-if="fileName" class="fileMeta">{{ fileName }}</div>
            </div>
          </div>

          <div class="actions">
            <button class="btn" :disabled="loading" @click="run">
              {{ loading ? '运行中…' : '开始分析' }}
            </button>
            <button class="btnGhost" :disabled="loading" @click="reset">清空</button>
          </div>
        </div>

        <div v-if="error" class="alert alertError">
          <div class="alertTitle">请求失败</div>
          <div class="alertBody">{{ error }}</div>
        </div>

        <div class="resultWrap">
          <div class="resultHeader">
            <div class="resultTitle">输出</div>
            <button v-if="result" class="btnGhost small" @click="copy(result)">复制</button>
          </div>
          <div v-if="!result && !loading" class="empty">暂无结果</div>
          <div v-if="loading" class="empty">处理中…</div>
          <pre v-if="result" class="result">{{ result }}</pre>
        </div>
      </section>

      <section v-else class="card">
        <div class="cardHeader">
          <div class="cardTitle">知识库</div>
          <div class="cardHint">
            Milvus 向量检索：SMILES 相似度 + 文本语义
            <span class="dot" />
            <span class="statInline">已入库：{{ kbStats.molecules ?? '-' }}</span>
          </div>
        </div>

        <div class="form">
          <div class="field">
            <div class="labelText">导入文件（.docx .xlsx .sdf .mol）</div>
            <div class="fileRow">
              <input
                class="file"
                type="file"
                accept=".docx,.xlsx,.sdf,.mol"
                multiple
                @change="onPickKbFiles"
              />
              <div v-if="kbFileNames.length" class="fileMeta">
                {{ kbFileNames.length }} 个文件
              </div>
            </div>
          </div>

          <div class="actions">
            <button class="btn" :disabled="kbLoading || !kbFiles.length" @click="ingestKb">
              {{ kbLoading ? '导入中…' : '导入' }}
            </button>
            <button class="btnGhost" :disabled="kbLoading" @click="refreshKbStats">刷新统计</button>
          </div>

          <div class="field">
            <div class="labelText">检索（输入 SMILES 或一段研究描述）</div>
            <div class="searchRow">
              <input v-model="kbQuery" class="input" placeholder="例如：c1ccccc1 或 ‘EGFR 抑制剂，疏水口袋…’" />
              <button class="btnGhost" :disabled="kbLoading" @click="searchKb">搜索</button>
            </div>
          </div>
        </div>

        <div v-if="kbError" class="alert alertError">
          <div class="alertTitle">知识库操作失败</div>
          <div class="alertBody">{{ kbError }}</div>
        </div>

        <div class="list">
          <div v-if="kbLoading" class="empty">查询中…</div>
          <div v-else-if="!kbResults.length" class="empty">暂无结果</div>
          <div v-else class="kbList">
            <button
              v-for="m in kbResults"
              :key="m.id"
              class="kbItem"
              type="button"
              @click="copy(m.smiles)"
              title="点击复制 SMILES"
            >
              <div class="kbSmiles">{{ m.smiles }}</div>
              <div class="kbMeta">
                <span class="pill">{{ m.sourceType }}</span>
                <span class="kbSource">{{ m.sourceFileName }}</span>
              </div>
            </button>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

const activeTab = ref('discover')

const query = ref('')
const file = ref(null)
const loading = ref(false)
const result = ref('')
const error = ref('')

const fileName = computed(() => file.value?.name ?? '')

// Knowledge base UI state
const kbFiles = ref([])
const kbLoading = ref(false)
const kbError = ref('')
const kbStats = ref({})
const kbQuery = ref('')
const kbResults = ref([])

const kbFileNames = computed(() => kbFiles.value.map(f => f.name))

function onPickFile(e) {
  const f = e?.target?.files?.[0] ?? null
  file.value = f
}

function onPickKbFiles(e) {
  const list = Array.from(e?.target?.files ?? [])
  kbFiles.value = list
}

function reset() {
  query.value = ''
  file.value = null
  result.value = ''
  error.value = ''
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text ?? '')
  } catch {
    // ignore
  }
}

async function run() {
  error.value = ''
  result.value = ''
  loading.value = true
  try {
    const form = new FormData()
    if (query.value?.trim()) form.append('query', query.value.trim())
    if (file.value) form.append('file', file.value)

    const resp = await fetch('/apiGraph/discover', {
      method: 'POST',
      body: form
    })

    const text = await resp.text()
    if (!resp.ok) {
      throw new Error(text || `HTTP ${resp.status}`)
    }

    // 后端 controller 直接返回 Object，通常是字符串；这里尽量智能处理
    let parsed = text
    try {
      const maybeJson = JSON.parse(text)
      parsed = typeof maybeJson === 'string' ? maybeJson : JSON.stringify(maybeJson, null, 2)
    } catch {
      // 非 JSON，按纯文本显示
    }
    result.value = parsed
  } catch (e) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

async function refreshKbStats() {
  kbError.value = ''
  try {
    const resp = await fetch('/apiGraph/kb/stats')
    const data = await resp.json()
    kbStats.value = data
  } catch (e) {
    kbError.value = e?.message ?? String(e)
  }
}

async function ingestKb() {
  kbError.value = ''
  kbLoading.value = true
  try {
    const form = new FormData()
    for (const f of kbFiles.value) {
      form.append('files', f)
    }
    const resp = await fetch('/apiGraph/kb/ingest', { method: 'POST', body: form })
    const data = await resp.json().catch(() => null)
    if (!resp.ok) {
      throw new Error((data && JSON.stringify(data)) || `HTTP ${resp.status}`)
    }
    // show warnings in result pane (optional)
    if (data?.warnings?.length) {
      result.value = `导入完成（新增分子：${data.moleculesAdded}）\n\nWarnings:\n- ${data.warnings.join('\n- ')}`
    } else {
      result.value = `导入完成（新增分子：${data?.moleculesAdded ?? 0}）`
    }
    await refreshKbStats()
  } catch (e) {
    kbError.value = e?.message ?? String(e)
  } finally {
    kbLoading.value = false
  }
}

async function searchKb() {
  kbError.value = ''
  kbLoading.value = true
  try {
    const url = new URL('/apiGraph/kb/search', window.location.origin)
    if (kbQuery.value?.trim()) url.searchParams.set('q', kbQuery.value.trim())
    url.searchParams.set('limit', '50')
    const resp = await fetch(url.toString())
    const data = await resp.json()
    if (!resp.ok) throw new Error(JSON.stringify(data))
    kbResults.value = Array.isArray(data) ? data : []
  } catch (e) {
    kbError.value = e?.message ?? String(e)
  } finally {
    kbLoading.value = false
  }
}

onMounted(() => {
  refreshKbStats()
})
</script>

<style scoped>
.page {
  max-width: 980px;
  margin: 0 auto;
  padding: 28px 18px 34px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-weight: 800;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, rgba(110, 231, 255, 0.32), rgba(167, 139, 250, 0.24));
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.brandText {
  display: grid;
  gap: 2px;
}

.title {
  font-size: 20px;
  font-weight: 750;
  line-height: 1.2;
}

.subtitle {
  font-size: 12px;
  color: var(--muted);
  margin-top: 0;
}

.tabs {
  display: inline-flex;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(0, 0, 0, 0.18);
  border-radius: 999px;
  padding: 4px;
  gap: 4px;
}

.tab {
  border: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.75);
  padding: 8px 12px;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 650;
  letter-spacing: 0.2px;
}

.tab.active {
  color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.10);
  border: 1px solid rgba(255, 255, 255, 0.14);
}

.content {
  display: grid;
  gap: 14px;
}

@media (max-width: 960px) {
  .header {
    align-items: flex-end;
  }
}

.card {
  background: var(--card);
  border: 1px solid var(--card-border);
  border-radius: 16px;
  padding: 16px;
  backdrop-filter: blur(10px);
}

.cardHeader {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.cardTitle {
  font-weight: 750;
  letter-spacing: 0.2px;
}

.cardHint {
  color: var(--muted);
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cardHint code {
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  display: inline-block;
}

.statInline {
  font-weight: 700;
}

.labelText {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 6px;
}

.form {
  display: grid;
  gap: 12px;
}

.field {
  display: grid;
  gap: 6px;
}

.textarea {
  width: 100%;
  resize: vertical;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: var(--text);
  outline: none;
}

.textarea:focus {
  border-color: rgba(110, 231, 255, 0.45);
  box-shadow: 0 0 0 3px rgba(110, 231, 255, 0.12);
}

.input {
  width: 100%;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: var(--text);
  outline: none;
}

.input:focus {
  border-color: rgba(110, 231, 255, 0.45);
  box-shadow: 0 0 0 3px rgba(110, 231, 255, 0.12);
}

.fileRow {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.file {
  color: var(--muted);
}

.fileMeta {
  font-size: 12px;
  color: var(--muted);
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.16);
}

.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.btn {
  appearance: none;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  color: var(--text);
  background: linear-gradient(135deg, rgba(110, 231, 255, 0.22), rgba(167, 139, 250, 0.18));
  font-weight: 750;
}

.btn:hover {
  border-color: rgba(110, 231, 255, 0.45);
}

.btnGhost {
  appearance: none;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.78);
  background: rgba(0, 0, 0, 0.10);
}

.btnGhost:hover {
  border-color: rgba(255, 255, 255, 0.22);
  background: rgba(0, 0, 0, 0.18);
}

.btn:disabled,
.btnGhost:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btnGhost.small {
  padding: 6px 10px;
  font-size: 12px;
}

.alert {
  margin-top: 12px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(0, 0, 0, 0.16);
  padding: 10px 12px;
}

.alertError {
  border-color: rgba(255, 107, 107, 0.38);
  background: rgba(255, 107, 107, 0.07);
}

.alertTitle {
  font-weight: 750;
  margin-bottom: 6px;
}

.alertBody {
  font-size: 13px;
  white-space: pre-wrap;
  color: rgba(255, 255, 255, 0.86);
}

.empty {
  color: var(--muted);
  font-size: 13px;
  padding: 12px 4px;
}

.resultWrap {
  margin-top: 14px;
}

.resultHeader {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.resultTitle {
  font-weight: 750;
}

.result {
  margin: 0;
  padding: 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.24);
  border: 1px solid rgba(255, 255, 255, 0.12);
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 220px;
}

.searchRow {
  display: flex;
  gap: 10px;
}

.list {
  margin-top: 12px;
}

.kbList {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

@media (max-width: 960px) {
  .kbList {
    grid-template-columns: 1fr;
  }
}

.kbItem {
  text-align: left;
  cursor: pointer;
  appearance: none;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.12);
  border-radius: 14px;
  padding: 10px 12px;
}

.kbItem:hover {
  border-color: rgba(255, 255, 255, 0.20);
  background: rgba(0, 0, 0, 0.18);
}

.kbSmiles {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono",
    "Courier New", monospace;
  font-size: 13px;
  word-break: break-word;
}

.kbMeta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 12px;
}

.pill {
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.82);
}

.kbSource {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

