package com.hrm.markdown.parser.ast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class MutationThreadTest {
    @Test
    fun should_keepTokenStableAndSeparateThreads_when_runningOnNative() = runBlocking {
        val callerToken = currentMutationThread()
        assertSame(callerToken, currentMutationThread())
        withContext(Dispatchers.Default) {
            val workerToken = currentMutationThread()
            assertSame(workerToken, currentMutationThread())
            assertNotSame(callerToken, workerToken)
        }
    }
}
