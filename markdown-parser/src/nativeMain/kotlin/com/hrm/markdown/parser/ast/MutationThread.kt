package com.hrm.markdown.parser.ast

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private object MutationThreadToken

internal actual fun currentMutationThread(): Any = MutationThreadToken
