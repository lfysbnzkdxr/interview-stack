package com.queststack.util

/**
 * 纯本地文本标准化工具：将用户粘贴的自由文本解析为问答对列表。
 *
 * 零依赖：仅使用 Kotlin 标准库（kotlin.text.Regex），无 IO、无协程、无三方库。
 */
object TextStandardizer {

    // ------------------------------------------------------------------
    // 正则定义
    // ------------------------------------------------------------------

    // 显式标记格式：任意位置出现的问题/答案标记（Q:/q:/问：/问题： 与 A:/a:/答：/答案：）
    private val ANY_MARKER = Regex("(?:q|问题|问|a|答案|答)[:：]", RegexOption.IGNORE_CASE)

    // 显式标记格式的识别入口：存在"行首问题标记"即判定为该格式
    private val EXPLICIT_DETECT =
        Regex("^(?:q|问题|问)[:：]", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    // 编号列表格式的编号条目模式：1. / 1、 / 1) / 1） / 问题1: / 第1题
    private const val NUMBERED_PATTERN =
        "(?:\\d+[.、)）])|(?:问题\\s*\\d+\\s*[:：])|(?:第\\s*\\d+\\s*题)"

    // 编号列表格式的识别入口：存在"行首编号条目"即判定为该格式
    private val NUMBERED_DETECT = Regex("^(?:$NUMBERED_PATTERN)", RegexOption.MULTILINE)

    // 编号列表格式的问题行：捕获编号之后的内容作为问题文本
    private val NUMBERED_QUESTION = Regex("^(?:$NUMBERED_PATTERN)(.*)$")

    // 编号列表格式的答案标记行：答案/答（可带分隔符，也可单独成行）或 A:/a:
    private val NUMBERED_ANSWER = Regex("^(?:(?:答案|答)(?:[:：]|$)|a[:：])(.*)$", RegexOption.IGNORE_CASE)

    // 压缩连续空行用
    private val BLANK_LINES = Regex("\n{2,}")

    // ------------------------------------------------------------------
    // 公开 API
    // ------------------------------------------------------------------

    /**
     * 文本规范化：
     * 1. 统一换行符（\r\n、\r 一律转换为 \n）；
     * 2. 去除每行首尾空白；
     * 3. 压缩连续空行为单个空行；
     * 4. 去除整体首尾空行。
     */
    fun normalize(text: String): String = text
        .replace("\r\n", "\n")          // Windows 换行
        .replace('\r', '\n')            // 旧式 Mac 换行
        .split('\n')                    // 逐行
        .joinToString("\n") { it.trim() } // 去除每行首尾空白
        .replace(BLANK_LINES, "\n")     // 压缩连续空行
        .trim()                         // 去除整体首尾空行

    /**
     * 将自由文本解析为问答对列表（Pair<问题, 答案>）。
     *
     * 按优先级依次尝试三种格式：
     * 1. 显式标记格式（Q:/A:、问：/答：、问题：/答案：）；
     * 2. 编号列表格式（1. / 1、 / 问题1: / 第1题，紧随的答案/答/A: 行为答案）；
     * 3. 单块文本（以上都识别不出时，问题=全文、答案为空）。
     */
    fun parseQaPairs(text: String): List<Pair<String, String>> {
        // 先规范化，再解析（答案跨行等结构依赖统一的换行符）
        val normalized = normalize(text)
        // 空文本没有任何可解析内容，直接返回空列表
        if (normalized.isEmpty()) return emptyList()

        // 分支 1：显式标记格式
        if (EXPLICIT_DETECT.containsMatchIn(normalized)) {
            return parseExplicit(normalized.split('\n'))
        }

        // 分支 2：编号列表格式
        if (NUMBERED_DETECT.containsMatchIn(normalized)) {
            return parseNumbered(normalized.split('\n'))
        }

        // 分支 3：单块文本，问题=全文、答案为空
        return listOf(normalized to "")
    }

    // ------------------------------------------------------------------
    // 内部解析实现
    // ------------------------------------------------------------------

    /** 解析过程中累积问答对的状态容器（两个解析分支共用）。 */
    private class QaBuilder {
        val pairs = mutableListOf<Pair<String, String>>()
        var question: String? = null
        var answer = StringBuilder()
        var answerStarted = false

        /** 开始一个新问题：先收尾上一对（若存在）。 */
        fun startQuestion(text: String) {
            flush()
            question = text
            answerStarted = false
        }

        /** 开始答案：若此前没有开放的问题，则以空问题兜底，保证内容不丢失。 */
        fun startAnswer(text: String) {
            if (question == null) question = ""
            answer = StringBuilder(text)
            answerStarted = true
        }

        /** 追加一行普通文本到当前开放部分：答案开始后归入答案，否则归入问题。 */
        fun appendLine(line: String) {
            val q = question ?: return
            if (answerStarted) {
                answer.append('\n').append(line)
            } else {
                question = "$q\n$line"
            }
        }

        /** 收尾当前开放的一对并清空状态。 */
        fun flush() {
            val q = question ?: return
            pairs.add(q.trim() to answer.toString().trim())
            question = null
            answer = StringBuilder()
            answerStarted = false
        }

        /** 结束解析：收尾最后一对并返回结果。 */
        fun finish(): List<Pair<String, String>> {
            flush()
            return pairs
        }
    }

    /**
     * 分支 1：显式标记格式。
     * - 支持行内与跨行：行首为标记时整行按标记切分（同一行可含 Q:...A: 多段）；
     * - 无标记的普通行作为当前开放部分（问题或答案）的延续，因此问题与答案均可跨多行；
     * - 只有行首为标记时才按标记解析该行，避免正文中"请问："之类子串被误判。
     */
    private fun parseExplicit(lines: List<String>): List<Pair<String, String>> {
        val builder = QaBuilder()
        for (line in lines) {
            val first = ANY_MARKER.find(line)
            // 行首无标记：整行作为当前开放部分的延续文本
            if (first == null || first.range.first > 0) {
                builder.appendLine(line)
                continue
            }
            // 行首为标记：依次处理该行内的每段标记（支持同行内 Q:...A: 多对）
            val matches = ANY_MARKER.findAll(line).toList()
            for (i in matches.indices) {
                val content = if (i < matches.lastIndex) {
                    line.substring(matches[i].range.last + 1, matches[i + 1].range.first)
                } else {
                    line.substring(matches[i].range.last + 1)
                }
                if (isQuestionMatch(matches[i].value)) {
                    builder.startQuestion(content)
                } else {
                    builder.startAnswer(content)
                }
            }
        }
        return builder.finish()
    }

    /**
     * 分支 2：编号列表格式。
     * - 编号条目行（1. / 1、 / 问题1: / 第1题 等）视为问题；
     * - 紧随的"答案/答/A:" 标记行及其后续普通行视为该问题的答案；
     * - 编号条目后无答案标记时，该对答案取空字符串。
     */
    private fun parseNumbered(lines: List<String>): List<Pair<String, String>> {
        val builder = QaBuilder()
        for (line in lines) {
            val q = NUMBERED_QUESTION.matchEntire(line)
            if (q != null) {
                builder.startQuestion(q.groupValues[1])
                continue
            }
            val a = NUMBERED_ANSWER.matchEntire(line)
            if (a != null) {
                builder.startAnswer(a.groupValues[1])
                continue
            }
            builder.appendLine(line)
        }
        return builder.finish()
    }

    /** 判断标记文本是否为问题标记（Q/q/问/问题），其余视为答案标记。 */
    private fun isQuestionMatch(marker: String): Boolean =
        marker.startsWith("q", ignoreCase = true) || marker.startsWith("问")
}
