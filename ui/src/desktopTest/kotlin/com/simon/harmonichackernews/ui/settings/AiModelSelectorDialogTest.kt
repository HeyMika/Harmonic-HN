@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.network.AiModel
import com.simon.harmonichackernews.network.AiModelCatalogRepository
import com.simon.harmonichackernews.network.AiModelCatalogSort
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiModelSelectorDialogTest {
    private val catalog = object : AiModelCatalogRepository {
        override suspend fun fetchModels(provider: AiSummaryProviders.Provider, sort: AiModelCatalogSort) =
            (1..30).map { AiModel("fixture/model-$it", "fixture/model-$it", "Model $it", 0, 0.0, 0.0, 1000) }
        override suspend fun resolveModel(provider: AiSummaryProviders.Provider, enteredModelId: String?) =
            AiModel(enteredModelId.orEmpty(), enteredModelId.orEmpty(), "Model", 0, 0.0, 0.0, 1000)
        override suspend fun fetchUptimeLastDay(provider: AiSummaryProviders.Provider, openRouterModelId: String) = null
    }

    @Test
    fun modelInputKeepsFocusWhenTheKeyboardShrinksTheSheet() = SwingUtilities.invokeAndWait {
        lateinit var windowInfo: WindowInfoImpl
        val scene = ImageComposeScene(412, 900, Density(1f)) {
            windowInfo = LocalWindowInfo.current as WindowInfoImpl
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                AiModelSelectorDialog(
                    initialModel = "", provider = AiSummaryProviders.defaultProvider,
                    catalogRepository = catalog, onSave = {}, onDismiss = {}, providerIcon = {},
                )
            }
        }
        var time = 0L
        fun frames(count: Int = 40) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
        fun tags() = scene.nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
        fun input() = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "ai-model-input" }
        try {
            frames()
            assertTrue("ai-model-catalog" in tags(), "A tall sheet pins the header: ${tags()}")
            input().config[SemanticsActions.RequestFocus].action!!.invoke()
            frames()
            assertTrue(input().config[SemanticsProperties.Focused])
            fun resize(height: Int) {
                // ImageComposeScene resizes its canvas without updating the layer's WindowInfo.
                windowInfo.containerSize = IntSize(412, height)
                windowInfo.containerDpSize = DpSize(412.dp, height.dp)
                scene.constraints = Constraints(maxWidth = 412, maxHeight = height)
                frames()
            }
            // The keyboard takes the lower part of the window and the sheet shrinks to fit.
            resize(520)
            assertTrue(input().config[SemanticsProperties.Focused], "Opening the keyboard must not unfocus the model input")
            assertTrue("ai-model-catalog" in tags(), "The layout must not change under a focused input: ${tags()}")
            val bounds = input().boundsInWindow
            assertTrue(bounds.top >= 0f && bounds.bottom <= 520f, "The input must stay above the keyboard: $bounds")

            resize(900)
            assertTrue(input().config[SemanticsProperties.Focused])
        } finally { scene.close() }
    }

    @Test
    fun shortSheetStillScrollsAsOnePageWhenTheInputIsNotFocused() = SwingUtilities.invokeAndWait {
        lateinit var windowInfo: WindowInfoImpl
        val scene = ImageComposeScene(412, 520, Density(1f)) {
            windowInfo = LocalWindowInfo.current as WindowInfoImpl
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                AiModelSelectorDialog(
                    initialModel = "", provider = AiSummaryProviders.defaultProvider,
                    catalogRepository = catalog, onSave = {}, onDismiss = {}, providerIcon = {},
                )
            }
        }
        var time = 0L
        fun frames(count: Int = 40) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
        fun tags() = scene.nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
        fun input() = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "ai-model-input" }
        try {
            frames()
            assertTrue("ai-model-whole-page" in tags(), "A short sheet scrolls as one page: ${tags()}")
            input().config[SemanticsActions.RequestFocus].action!!.invoke()
            frames()
            assertTrue(input().config[SemanticsProperties.Focused])
            assertTrue("ai-model-whole-page" in tags())
        } finally { scene.close() }
    }

    @Test
    fun selfHostedServerListsItsOwnModelsWithoutCatalogDetails() = SwingUtilities.invokeAndWait {
        var saved: String? = null
        val scene = ImageComposeScene(412, 900, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                AiModelSelectorDialog(
                    initialModel = "", provider = AiSummaryProviders.defaultProvider,
                    catalogRepository = catalog, onSave = { saved = it }, onDismiss = {}, providerIcon = {},
                    serverModels = { listOf("llama3.3", "qwen3:8b") },
                )
            }
        }
        var time = 0L
        fun frames(count: Int = 40) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
        fun texts() = scene.nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        try {
            frames()
            assertTrue(texts().containsAll(listOf("llama3.3", "qwen3:8b", "Models reported by your server")), "${texts()}")
            assertTrue(texts().none { "Fixture" in it || "per 1M" in it || it == "Popular" || it == "INPUT / OUTPUT" }, "${texts()}")
            scene.nodes().first { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    scene.textsUnder(node).contains("qwen3:8b")
            }.config[SemanticsActions.OnClick].action!!.invoke()
            frames()
            val input = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "ai-model-input" }
            assertEquals("qwen3:8b", input.config[SemanticsProperties.EditableText].text)
            scene.nodes().first { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null && scene.textsUnder(node) == listOf("Save model")
            }.config[SemanticsActions.OnClick].action!!.invoke()
            frames(90)
            assertEquals("qwen3:8b", saved)
        } finally { scene.close() }
    }

    @Test
    fun unreachableServerShowsTheReasonAndStillAcceptsATypedModel() = SwingUtilities.invokeAndWait {
        var attempts = 0
        val scene = ImageComposeScene(412, 900, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                AiModelSelectorDialog(
                    initialModel = "", provider = AiSummaryProviders.defaultProvider,
                    catalogRepository = catalog, onSave = {}, onDismiss = {}, providerIcon = {},
                    serverModels = {
                        if (attempts++ == 0) error("Could not load models: Connection refused")
                        listOf("llama3.3")
                    },
                )
            }
        }
        var time = 0L
        fun frames(count: Int = 40) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
        fun texts() = scene.nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        try {
            frames()
            assertTrue("Could not load models: Connection refused" in texts(), "${texts()}")
            val input = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "ai-model-input" }
            input.config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString("typed-model"))
            frames()
            scene.nodes().first { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null && scene.textsUnder(node) == listOf("Retry")
            }.config[SemanticsActions.OnClick].action!!.invoke()
            frames()
            assertTrue("llama3.3" in texts(), "${texts()}")
            assertEquals("typed-model", scene.nodes().first {
                it.config.getOrNull(SemanticsProperties.TestTag) == "ai-model-input"
            }.config[SemanticsProperties.EditableText].text)
        } finally { scene.close() }
    }

    private fun ImageComposeScene.textsUnder(node: SemanticsNode): List<String> {
        fun descend(current: SemanticsNode): List<SemanticsNode> = listOf(current) + current.children.flatMap(::descend)
        return descend(node).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
}
