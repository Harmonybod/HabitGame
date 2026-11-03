package com.habitgame.domain.notes

import com.habitgame.domain.notes.NoteFormat.Edit
import com.habitgame.domain.notes.NoteFormat.ListKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteFormatTest {
    /** Types [s] at the cursor, then applies the automatic formatting, one character at a time. */
    private fun type(e: Edit, s: String): Edit = s.fold(e) { acc, c ->
        val after = Edit(acc.text.substring(0, acc.start) + c + acc.text.substring(acc.end), acc.start + 1)
        NoteFormat.afterTyping(acc, after)
    }

    @Test
    fun enterContinuesBulletsNumbersAndChecklists() {
        assertEquals("• Milk\n• ", type(Edit("• Milk", 6), "\n").text)
        assertEquals("1. Eggs\n2. ", type(Edit("1. Eggs", 7), "\n").text)
        assertEquals("☑ Done\n☐ ", type(Edit("☑ Done", 6), "\n").text)   // a new item starts unticked
        assertEquals("    ◦ Sub\n    ◦ ", type(Edit("    ◦ Sub", 9), "\n").text)
    }

    @Test
    fun enterOnAnEmptyItemEndsTheList() {
        val e = type(Edit("• Milk\n• ", 9), "\n")
        assertEquals("• Milk\n", e.text)
        assertEquals(7, e.start)
    }

    @Test
    fun plainLinesDontBecomeLists() {
        assertNull(NoteFormat.continueList(Edit("Hello", 5), Edit("Hello\n", 6)))
    }

    @Test
    fun sentencesAndItemsStartWithACapital() {
        assertEquals("• Buy", type(Edit("• ", 2), "buy").text)
        assertEquals("Hi. There", type(Edit("Hi. ", 4), "there").text)
        assertEquals("Hi there", type(Edit("Hi ", 3), "there").text)
        assertEquals("## Plan", type(Edit("## ", 3), "plan").text)
    }

    @Test
    fun numberedListsRenumberAfterChanges() {
        val e = NoteFormat.renumber(Edit("1. a\n1. b\n5. c\n\n7. d", 0))
        assertEquals("1. a\n2. b\n3. c\n\n1. d", e.text)
    }

    @Test
    fun nestedNumbersCountOnTheirOwn() {
        val e = NoteFormat.renumber(Edit("1. a\n    1. x\n    5. y\n9. b", 0))
        assertEquals("1. a\n    1. x\n    2. y\n2. b", e.text)
    }

    @Test
    fun tappingABoxTicksIt() {
        val text = "☐ Call mum\n☐ Pay rent"
        val tapped = NoteFormat.tapToggle(Edit(text, 3), Edit(text, 11))!!
        assertEquals("☐ Call mum\n☑ Pay rent", tapped.text)
        assertNull(NoteFormat.tapToggle(Edit(text, 3), Edit(text, 15)))   // tapping the words just moves the cursor
    }

    @Test
    fun toolbarListsToggleOnTheSelectedLines() {
        val e = NoteFormat.toggleList(Edit("Eggs\nMilk", 0, 9), ListKind.NUMBER)
        assertEquals("1. Eggs\n2. Milk", e.text)
        assertEquals("Eggs\nMilk", NoteFormat.toggleList(Edit(e.text, 0, e.text.length), ListKind.NUMBER).text)
        assertEquals("☐ Eggs", NoteFormat.toggleList(Edit("• Eggs", 3), ListKind.CHECK).text)
    }

    @Test
    fun headingsAndIndent() {
        assertEquals("# Plan", NoteFormat.toggleHeading(Edit("Plan", 0), 1).text)
        assertEquals("## Plan", NoteFormat.toggleHeading(Edit("# Plan", 0), 2).text)
        assertEquals("Plan", NoteFormat.toggleHeading(Edit("## Plan", 0), 2).text)
        assertEquals("    ◦ Sub", NoteFormat.indent(Edit("• Sub", 2), +1).text)
        assertEquals("• Sub", NoteFormat.indent(Edit("    ◦ Sub", 6), -1).text)
    }

    @Test
    fun boldWrapsAndUnwraps() {
        val bold = NoteFormat.wrap(Edit("make it bold", 8, 12), "**")
        assertEquals("make it **bold**", bold.text)
        assertEquals(Edit("make it bold", 8, 12), NoteFormat.wrap(bold, "**"))
        assertEquals(Edit("****", 2), NoteFormat.wrap(Edit("", 0), "**"))
    }

    @Test
    fun previewAndCounts() {
        val text = "# Shopping\n**Today**: *quick*\n☑ Eggs\n☐ Milk"
        assertEquals("Shopping\nToday: quick\n☑ Eggs\n☐ Milk", NoteFormat.plain(text))
        assertEquals(1 to 2, NoteFormat.checklist(text))
        assertEquals(5, NoteFormat.words(text))
    }
}
