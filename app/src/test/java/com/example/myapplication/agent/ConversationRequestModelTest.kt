package com.example.myapplication.agent

import com.example.myapplication.agent.model.AgentRequest
import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.Conversation
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRequestModelTest {
    private val gson = Gson()

    @Test
    fun agentRequestCarriesConversationIdInsteadOfFrontendHistory() {
        val request = AgentRequest(
            text = "明天下午三点出去玩",
            sessionId = "conversation-1",
            conversationId = "conversation-1",
        )

        val json = gson.toJson(request)

        assertTrue(json.contains("\"conversation_id\":\"conversation-1\""))
        assertTrue(json.contains("\"history\":[]"))
    }

    @Test
    fun backendConversationAndMessagesCanBeDecoded() {
        val conversation = gson.fromJson(
            """
            {
              "id": "conversation-1",
              "title": "明天安排",
              "createdAt": 100,
              "updatedAt": 200,
              "lastMessageAt": 300,
              "messageCount": 2
            }
            """.trimIndent(),
            Conversation::class.java,
        )
        val message = gson.fromJson(
            """
            {
              "id": "message-1",
              "conversationId": "conversation-1",
              "role": "assistant",
              "content": "已记录",
              "kind": "MESSAGE",
              "createdAt": 300
            }
            """.trimIndent(),
            ChatMessage::class.java,
        )

        assertEquals("明天安排", conversation.title)
        assertEquals(2, conversation.messageCount)
        assertEquals("conversation-1", message.conversationId)
        assertEquals("已记录", message.content)
    }
}
