package com.hrm.markdown.renderer.internal.layout.engine

import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.core.model.InternalRenderDocumentModel
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutBlockModel
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutDocumentMetadata
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutDocumentModel

/** Block-addressable input for the Compose painter. */
internal interface MarkdownLayoutSource {
    val identity: RenderIdentity
    val blockCount: Int
    val metadata: InternalLayoutDocumentMetadata

    fun stableIdAt(index: Int): Long

    fun blockAt(index: Int): InternalLayoutBlockModel
}

/** Existing eager layout path used by StaticColumn and compatibility tests. */
internal class EagerMarkdownLayoutSource(
    private val document: InternalLayoutDocumentModel,
) : MarkdownLayoutSource {
    override val identity: RenderIdentity get() = document.identity
    override val blockCount: Int get() = document.blocks.size
    override val metadata: InternalLayoutDocumentMetadata get() = document.metadata

    override fun stableIdAt(index: Int): Long = document.blocks[index].identity.stableId

    override fun blockAt(index: Int): InternalLayoutBlockModel = document.blocks[index]
}

/**
 * Lazy top-level layout session. A block is laid out at most once for this environment and only
 * when LazyColumn composes that item. Recreating the session invalidates the whole block cache.
 */
internal class MarkdownLayoutSession(
    private val document: InternalRenderDocumentModel,
    private val environment: LayoutEnvironment,
    private val engine: MarkdownLayoutEngine,
) : MarkdownLayoutSource {
    private val blockCache = mutableMapOf<Int, InternalLayoutBlockModel>()

    override val identity: RenderIdentity get() = document.identity
    override val blockCount: Int get() = document.blocks.size
    override val metadata: InternalLayoutDocumentMetadata = engine.metadata(document)

    internal val laidOutBlockCount: Int get() = blockCache.size

    override fun stableIdAt(index: Int): Long = document.blocks[index].identity.stableId

    override fun blockAt(index: Int): InternalLayoutBlockModel {
        require(index in 0 until blockCount) { "Markdown block index out of bounds: $index" }
        return blockCache.getOrPut(index) {
            engine.layoutBlock(document.blocks[index], environment)
        }
    }
}
