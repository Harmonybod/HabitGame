package com.habitgame.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCodeTest {

    private val json = """{"user":{"displayName":"Josiah 🔥","level":5,"totalCoins":557},"savings":[{"amount":150.0,"note":"Birthday money"}]}"""

    @Test
    fun codeRoundTrips() {
        val code = ProgressTransfer.encode(json)
        assertTrue(code.startsWith("HG1-"))
        assertEquals(json, ProgressTransfer.decode(code))
    }

    @Test
    fun lineBreaksAddedByChatAppsAreIgnored() {
        val code = ProgressTransfer.encode(json)
        val mangled = code.chunked(20).joinToString("\n ")
        assertEquals(json, ProgressTransfer.decode(mangled))
    }

    @Test
    fun codeIsSmallerThanTheData() {
        val big = (1..200).joinToString(",", "[", "]") { """{"date":"2026-10-$it","done":3,"total":5}""" }
        assertTrue(ProgressTransfer.encode(big).length < big.length / 3)
    }

    @Test
    fun cutOffCodeIsRejected() {
        val code = ProgressTransfer.encode(json)
        assertThrows(Exception::class.java) { ProgressTransfer.decode(code.dropLast(10)) }
    }

    @Test
    fun otherTextIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ProgressTransfer.decode("hello there") }
    }
}
