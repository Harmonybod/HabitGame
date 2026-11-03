package com.habitgame.domain.notes

/**
 * Formatting for notes, kept in the note's plain text so it syncs and shares as-is:
 * lines start with "• " (bullet), "1. " (numbered), "☐ " / "☑ " (checklist) or "# " / "## " (heading),
 * and may be indented; inside a line **bold**, *italic*, ~~strike~~ and __underline__.
 * Every function takes the text and the selection and returns the new text and selection.
 */
object NoteFormat {
    const val BULLET = "• "
    const val SUB_BULLET = "◦ "
    const val BOX = "☐ "
    const val CHECKED = "☑ "
    const val INDENT = "    "

    enum class ListKind { BULLET, NUMBER, CHECK }

    /** Text with its selection ([start] ≤ [end]; equal when it's just the cursor). */
    data class Edit(val text: String, val start: Int, val end: Int = start)

    /** How a line starts: its indent, its list marker (if any) and where its own text begins. */
    data class Prefix(val indent: String, val marker: String, val kind: ListKind?, val number: Int?) {
        val length: Int get() = indent.length + marker.length
    }

    private val prefixRegex = Regex("""^( *)(• |◦ |☐ |☑ |(\d+)\. )?""")

    fun prefixOf(line: String): Prefix {
        val m = prefixRegex.find(line)!!
        val marker = m.groupValues[2]
        val kind = when {
            marker.isEmpty() -> null
            marker == BULLET || marker == SUB_BULLET -> ListKind.BULLET
            marker == BOX || marker == CHECKED -> ListKind.CHECK
            else -> ListKind.NUMBER
        }
        return Prefix(m.groupValues[1], marker, kind, m.groupValues[3].toIntOrNull())
    }

    /** Start of the line holding [index]. */
    fun lineStart(text: String, index: Int): Int = text.lastIndexOf('\n', (index - 1).coerceAtLeast(-1)).let { if (index == 0) 0 else it + 1 }

    fun lineEnd(text: String, index: Int): Int = text.indexOf('\n', index).let { if (it < 0) text.length else it }

    // ── Typing ──────────────────────────────────────────────────────────────

    /**
     * Applies the automatic bits after the user typed: Enter on a list line continues the list
     * (Enter on an empty item ends it), a new sentence or list item starts with a capital, and
     * numbered lists stay numbered 1, 2, 3.
     */
    fun afterTyping(before: Edit, after: Edit): Edit {
        var e = continueList(before, after) ?: after
        e = capitalize(before, e)
        return renumber(e)
    }

    /** Enter pressed at the end of (or inside) a list line. */
    fun continueList(before: Edit, after: Edit): Edit? {
        val cursor = after.start
        if (after.text.length != before.text.length + 1 || cursor < 1 || after.text[cursor - 1] != '\n') return null
        if (after.text.removeRange(cursor - 1, cursor) != before.text) return null
        val prevStart = lineStart(after.text, cursor - 1)
        val prevLine = after.text.substring(prevStart, cursor - 1)
        val prefix = prefixOf(prevLine)
        if (prefix.kind == null) return null
        // Enter on an empty item: end the list there instead of making another empty one
        if (prevLine.length == prefix.length) {
            val text = before.text.removeRange(prevStart, prevStart + prefix.length)
            return Edit(text, prevStart)
        }
        val next = when (prefix.kind) {
            ListKind.NUMBER -> "${(prefix.number ?: 0) + 1}. "
            ListKind.CHECK -> BOX
            ListKind.BULLET -> prefix.marker
        }
        val insert = prefix.indent + next
        return Edit(after.text.substring(0, cursor) + insert + after.text.substring(cursor), cursor + insert.length)
    }

    /** A lowercase letter typed at the start of a line's text or of a sentence becomes a capital. */
    fun capitalize(before: Edit, after: Edit): Edit {
        val i = after.start - 1
        if (after.text.length != before.text.length + 1 || i < 0 || after.start != after.end) return after
        val c = after.text[i]
        if (!c.isLowerCase()) return after
        val start = lineStart(after.text, i)
        val line = after.text.substring(start, lineEnd(after.text, start))
        val textStart = start + prefixOf(line).length + headingPrefix(line).length
        val sentenceStart = i == textStart ||
            (i >= 2 && after.text[i - 1] == ' ' && after.text[i - 2] in ".!?" && i - 2 >= textStart)
        if (!sentenceStart) return after
        return after.copy(text = after.text.substring(0, i) + c.uppercaseChar() + after.text.substring(i + 1))
    }

    /** Numbered lists counted 1, 2, 3 again after lines are added, removed or moved. */
    fun renumber(e: Edit): Edit {
        val lines = e.text.split('\n')
        val counters = sortedMapOf<Int, Int>()
        val out = StringBuilder()
        var shiftStart = 0
        var shiftEnd = 0
        var pos = 0
        lines.forEachIndexed { index, line ->
            val prefix = prefixOf(line)
            var newLine = line
            val indent = prefix.indent.length
            if (prefix.kind == ListKind.NUMBER) {
                counters.keys.filter { it > indent }.forEach { counters.remove(it) }
                val n = (counters[indent] ?: 0) + 1
                counters[indent] = n
                if (prefix.number != n) newLine = prefix.indent + "$n. " + line.substring(prefix.length)
            } else if (line.isBlank() && prefix.kind == null) {
                counters.clear()
            } else {
                counters.keys.filter { it >= indent && prefix.kind == null }.forEach { counters.remove(it) }
            }
            val delta = newLine.length - line.length
            if (delta != 0) {
                val markerEnd = pos + prefix.length
                if (e.start >= markerEnd) shiftStart += delta
                if (e.end >= markerEnd) shiftEnd += delta
            }
            out.append(newLine)
            if (index < lines.lastIndex) out.append('\n')
            pos += line.length + 1
        }
        return Edit(out.toString(), e.start + shiftStart, e.end + shiftEnd)
    }

    /**
     * Tapping a checklist box ticks or unticks it. [before] and [after] have the same text; the
     * cursor moved onto a "☐"/"☑" at the start of a line.
     */
    fun tapToggle(before: Edit, after: Edit): Edit? {
        if (before.text != after.text || after.start != after.end || before.start == after.start) return null
        val start = lineStart(after.text, after.start)
        val line = after.text.substring(start, lineEnd(after.text, start))
        val prefix = prefixOf(line)
        if (prefix.kind != ListKind.CHECK) return null
        val box = start + prefix.indent.length
        if (after.start !in box..box + 1) return null
        return Edit(toggleCheckAt(after.text, start), box + 2)
    }

    /** Ticks or unticks the checklist line starting at [lineStart]. */
    fun toggleCheckAt(text: String, lineStart: Int): String {
        val line = text.substring(lineStart, lineEnd(text, lineStart))
        val prefix = prefixOf(line)
        if (prefix.kind != ListKind.CHECK) return text
        val at = lineStart + prefix.indent.length
        return text.substring(0, at) + (if (prefix.marker == BOX) CHECKED else BOX) + text.substring(at + 2)
    }

    // ── Toolbar ─────────────────────────────────────────────────────────────

    /** The lines the selection touches: their start offsets. */
    private fun selectedLines(e: Edit): List<Int> {
        val starts = mutableListOf(lineStart(e.text, e.start))
        var i = e.text.indexOf('\n', e.start)
        while (i in 0 until e.end) { starts += i + 1; i = e.text.indexOf('\n', i + 1) }
        return starts
    }

    /** Applies [change] to every selected line (last first, so earlier offsets stay right), keeping the selection on them. */
    private fun mapLines(e: Edit, change: (line: String, index: Int) -> String): Edit {
        val starts = selectedLines(e)
        var text = e.text
        var startShift = 0
        var endShift = 0
        starts.withIndex().reversed().forEach { (index, start) ->
            val end = lineEnd(text, start)
            val line = text.substring(start, end)
            val changed = change(line, index)
            val delta = changed.length - line.length
            text = text.substring(0, start) + changed + text.substring(end)
            if (index == 0) startShift = delta
            endShift += delta
        }
        val firstStart = starts.first()
        val newStart = if (e.start == firstStart) e.start else (e.start + startShift).coerceAtLeast(firstStart)
        return renumber(Edit(text, newStart.coerceIn(0, text.length), (e.end + endShift).coerceIn(0, text.length)))
    }

    /** Turns the selected lines into a list of [kind], or back to plain lines if they already are one. */
    fun toggleList(e: Edit, kind: ListKind): Edit {
        val starts = selectedLines(e)
        val all = starts.all { prefixOf(e.text.substring(it, lineEnd(e.text, it))).kind == kind }
        return mapLines(e) { line, index ->
            val prefix = prefixOf(line)
            val body = line.substring(prefix.length).removePrefix("## ").removePrefix("# ")
            val marker = when {
                all -> ""
                kind == ListKind.BULLET -> if (prefix.indent.isEmpty()) BULLET else SUB_BULLET
                kind == ListKind.NUMBER -> "${index + 1}. "
                else -> if (prefix.marker == CHECKED) CHECKED else BOX
            }
            prefix.indent + marker + body
        }
    }

    /** "# " (big) or "## " (smaller) heading on the selected lines; again to remove it. */
    fun toggleHeading(e: Edit, level: Int): Edit {
        val mark = "#".repeat(level) + " "
        return mapLines(e) { line, _ ->
            val prefix = prefixOf(line)
            val body = line.substring(prefix.length)
            val current = headingPrefix(body)
            prefix.indent + if (current == mark) body.removePrefix(mark) else mark + body.removePrefix(current)
        }
    }

    fun headingPrefix(line: String): String = when {
        line.startsWith("## ") -> "## "
        line.startsWith("# ") -> "# "
        else -> ""
    }

    /** Moves the selected lines in (+1) or out (−1) one step; nested bullets become ◦. */
    fun indent(e: Edit, direction: Int): Edit = mapLines(e) { line, _ ->
        val prefix = prefixOf(line)
        val indent = if (direction > 0) prefix.indent + INDENT else prefix.indent.removeSuffix(INDENT).let { if (it == prefix.indent) prefix.indent.drop(INDENT.length) else it }
        val marker = when (prefix.marker) {
            BULLET, SUB_BULLET -> if (indent.isEmpty()) BULLET else SUB_BULLET
            else -> prefix.marker
        }
        indent + marker + line.substring(prefix.length)
    }

    /** Wraps the selection in [mark] (e.g. "**"), or unwraps it; with nothing selected, adds a pair to type between. */
    fun wrap(e: Edit, mark: String): Edit {
        val t = e.text
        if (e.start == e.end) return Edit(t.substring(0, e.start) + mark + mark + t.substring(e.start), e.start + mark.length)
        val inside = t.substring(e.start, e.end)
        val wrappedAround = e.start >= mark.length && t.startsWith(mark, e.start - mark.length) && t.startsWith(mark, e.end)
        return when {
            wrappedAround -> Edit(t.substring(0, e.start - mark.length) + inside + t.substring(e.end + mark.length), e.start - mark.length, e.end - mark.length)
            inside.length >= mark.length * 2 && inside.startsWith(mark) && inside.endsWith(mark) ->
                Edit(t.substring(0, e.start) + inside.substring(mark.length, inside.length - mark.length) + t.substring(e.end), e.start, e.end - mark.length * 2)
            else -> Edit(t.substring(0, e.start) + mark + inside + mark + t.substring(e.end), e.start + mark.length, e.end + mark.length)
        }
    }

    // ── Reading ─────────────────────────────────────────────────────────────

    private val inlineMarks = Regex("""\*\*|~~|__|(?<![*\w])\*(?=\S)|(?<=\S)\*(?![*\w])""")

    /** The note without heading and bold/italic marks, for previews in the list. */
    fun plain(text: String): String = text.lines().joinToString("\n") { line ->
        val prefix = prefixOf(line)
        val body = line.substring(prefix.length)
        line.substring(0, prefix.length) + body.removePrefix(headingPrefix(body)).replace(inlineMarks, "")
    }

    /** Checklist items ticked and in total. */
    fun checklist(text: String): Pair<Int, Int> {
        val kinds = text.lines().map { prefixOf(it) }.filter { it.kind == ListKind.CHECK }
        return kinds.count { it.marker == CHECKED } to kinds.size
    }

    fun words(text: String): Int = plain(text).split(Regex("""\s+""")).count { w -> w.any { it.isLetterOrDigit() } }
}
