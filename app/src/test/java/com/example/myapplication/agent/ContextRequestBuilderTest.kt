package com.example.myapplication.agent

import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.ChatMessageKind
import com.example.myapplication.agent.model.ContextSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextRequestBuilderTest {
    @Test
    fun smallHistorySendsAllMessagesWithoutSummaryRefresh() {
        val messages = numberedMessages(6)

        val request = messages.toContextRequest(
            ContextSnapshot(summary = "用户喜欢早上处理日程", summarizedMessageCount = 0)
        )

        assertEquals(6, request.history.size)
        assertEquals("用户喜欢早上处理日程", request.contextSummary)
        assertTrue(request.summaryHistory.isEmpty())
        assertEquals(0, request.nextSummarizedMessageCount)
    }

    @Test
    fun longHistorySendsRecentWindowAndOldestUnsummaryBatch() {
        val messages = numberedMessages(25)

        val request = messages.toContextRequest(ContextSnapshot())

        assertEquals(RECENT_CONTEXT_MESSAGE_LIMIT, request.history.size)
        assertEquals("message-13", request.history.first().content)
        assertEquals("message-24", request.history.last().content)
        assertEquals(13, request.summaryHistory.size)
        assertEquals("message-0", request.summaryHistory.first().content)
        assertEquals("message-12", request.summaryHistory.last().content)
        assertEquals(13, request.nextSummarizedMessageCount)
    }

    @Test
    fun existingSummaryOnlyRefreshesUnsummarizedOldMessages() {
        val messages = numberedMessages(30)

        val request = messages.toContextRequest(
            ContextSnapshot(summary = "旧摘要", summarizedMessageCount = 10)
        )

        assertEquals(RECENT_CONTEXT_MESSAGE_LIMIT, request.history.size)
        assertEquals(8, request.summaryHistory.size)
        assertEquals("message-10", request.summaryHistory.first().content)
        assertEquals("message-17", request.summaryHistory.last().content)
        assertEquals(18, request.nextSummarizedMessageCount)
    }

    @Test
    fun receiptsAreExcludedFromModelContext() {
        val messages = listOf(
            ChatMessage(role = "assistant", content = "receipt", kind = ChatMessageKind.ACTION_RECEIPT),
            ChatMessage(role = "user", content = "message-1"),
        )

        val request = messages.toContextRequest(ContextSnapshot())

        assertEquals(1, request.history.size)
        assertEquals("message-1", request.history.single().content)
    }
}

private fun numberedMessages(count: Int): List<ChatMessage> {
    return (0 until count).map { index ->
        ChatMessage(
            role = if (index % 2 == 0) "user" else "assistant",
            content = "message-$index",
            createdAt = index.toLong(),
        )
    }
}
