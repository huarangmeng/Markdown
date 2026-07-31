package com.hrm.markdown.renderer.internal.layout.engine

import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.core.model.InternalRenderDocumentModel
import com.hrm.markdown.renderer.internal.core.model.InternalRenderBlockModel
import com.hrm.markdown.renderer.internal.layout.inline.InlineLayoutEpoch
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

    fun prefetch(index: Int) {
        if (index in 0 until blockCount) blockAt(index)
    }
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
    private val sharedBlockCache: MarkdownBlockLayoutCache? = null,
) : MarkdownLayoutSource {
    private val blockCache = mutableMapOf<Int, InternalLayoutBlockModel>()

    override val identity: RenderIdentity get() = document.identity
    override val blockCount: Int get() = document.blocks.size
    override val metadata: InternalLayoutDocumentMetadata = engine.metadata(document)

    internal val laidOutBlockCount: Int get() = blockCache.size
    internal var prefetchRequestCount: Int = 0
        private set

    override fun stableIdAt(index: Int): Long = document.blocks[index].identity.stableId

    override fun blockAt(index: Int): InternalLayoutBlockModel {
        require(index in 0 until blockCount) { "Markdown block index out of bounds: $index" }
        return blockCache.getOrPut(index) {
            val block = document.blocks[index]
            sharedBlockCache?.getOrPut(block, environment) {
                engine.layoutBlock(block, environment)
            } ?: engine.layoutBlock(block, environment)
        }
    }

    override fun prefetch(index: Int) {
        if (index !in 0 until blockCount) return
        prefetchRequestCount++
        blockAt(index)
    }
}

/** Bounded cache shared by successive layout sessions owned by one renderer instance. */
internal class MarkdownBlockLayoutCache(
    private val maxEntries: Int = 2_048,
) {
    private data class Key(
        val stableId: Long,
        val contentRevision: Long,
        val layoutRevision: Long,
        val paintRevision: Long,
        val viewportWidthBits: Int,
        val blockSpacingBits: Int,
        val epoch: InlineLayoutEpoch,
    )

    private val values = LinkedHashMap<Key, InternalLayoutBlockModel>()

    var hitCount: Long = 0
        private set
    var computationCount: Long = 0
        private set

    fun getOrPut(
        block: InternalRenderBlockModel,
        environment: LayoutEnvironment,
        compute: () -> InternalLayoutBlockModel,
    ): InternalLayoutBlockModel {
        val key = Key(
            stableId = block.identity.stableId,
            contentRevision = block.identity.contentRevision,
            layoutRevision = block.identity.layoutRevision,
            paintRevision = block.identity.paintRevision,
            viewportWidthBits = environment.viewportWidth.toBits(),
            blockSpacingBits = environment.blockSpacing.toBits(),
            epoch = environment.inlineLayoutEpoch,
        )
        values[key]?.let {
            hitCount++
            return it
        }
        val value = compute()
        computationCount++
        values[key] = value
        while (values.size > maxEntries) {
            val eldest = values.keys.firstOrNull() ?: break
            values.remove(eldest)
        }
        return value
    }
}
