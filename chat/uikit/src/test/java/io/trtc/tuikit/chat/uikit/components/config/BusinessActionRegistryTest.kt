package io.trtc.tuikit.chat.uikit.components.config

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessActionRegistryTest {

    @After
    fun tearDown() {
        BusinessActionRegistry.handler = null
    }

    @Test
    fun missingHandlerKeepsStockSdkPath() {
        val handled = BusinessActionRegistry.dispatch(
            BusinessAction.DeleteFriend("user-a"),
            RecordingCompletion()
        )

        assertFalse(handled)
    }

    @Test
    fun registeredHandlerOwnsOperationAndReturnsResult() {
        val completion = RecordingCompletion()
        BusinessActionRegistry.handler = BusinessActionHandler { action, callback ->
            assertEquals(BusinessAction.DeleteFriend("user-a"), action)
            callback.onSuccess(BusinessActionResult("ok"))
            true
        }

        assertTrue(BusinessActionRegistry.dispatch(BusinessAction.DeleteFriend("user-a"), completion))
        assertEquals("ok", completion.result?.identifier)
    }

    @Test
    fun handlerExceptionDoesNotFallThroughToSecondSdkWrite() {
        val completion = RecordingCompletion()
        BusinessActionRegistry.handler = BusinessActionHandler { _, _ -> error("business failed") }

        assertTrue(BusinessActionRegistry.dispatch(BusinessAction.DeleteFriend("user-a"), completion))
        assertEquals("business failed", completion.failure)
    }

    private class RecordingCompletion : BusinessActionCompletion {
        var result: BusinessActionResult? = null
        var failure: String? = null

        override fun onSuccess(result: BusinessActionResult) {
            this.result = result
        }

        override fun onFailure(code: Int, description: String) {
            failure = description
        }
    }
}
