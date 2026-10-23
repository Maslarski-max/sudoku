package com.maslarski.sudoku.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maslarski.sudoku.domain.engine.HintEngine
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.domain.model.Topology
import com.maslarski.sudoku.domain.model.Variant
import com.maslarski.sudoku.ui.theme.LocalBoardColors
import kotlin.math.floor
import kotlin.math.max

/** Zoom/pan state for the board. Exposed so the screen can offer a "reset zoom" action. */
class BoardTransform {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var maxScale by mutableFloatStateOf(4f)
        private set
    private var initialized = false

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun initialize(variant: Variant?, topology: Topology, viewport: Size) {
        maxScale = if (variant == Variant.SAMURAI || variant == Variant.FLOWER) 3f else 4f
        if (initialized || viewport.width <= 0f || viewport.height <= 0f) return
        initialized = true
        if (variant != Variant.SAMURAI && variant != Variant.FLOWER) {
            reset()
            return
        }
        val (row, col) = if (variant == Variant.SAMURAI) 6 to 6 else 3 to 3
        val initialScale = (topology.width / 9f).coerceAtMost(maxScale)
        val cell = viewport.width / topology.width
        scale = initialScale
        offset = Offset(
            x = viewport.width / 2f - (col + 4.5f) * cell * initialScale,
            y = viewport.height / 2f - (row + 4.5f) * cell * initialScale,
        )
        clamp(viewport)
    }

    fun keepVisible(index: Int, topology: Topology, viewport: Size, outsideMargin: Int) {
        if (index < 0 || !topology.active[index] || viewport.width <= 0f) return
        val margin = outsideMargin / 2
        val contentWidth = topology.width + outsideMargin
        val cell = viewport.width / contentWidth
        val row = index / topology.width
        val col = index % topology.width
        val left = (margin + col) * cell * scale + offset.x
        val top = (margin + row) * cell * scale + offset.y
        val right = left + cell * scale
        val bottom = top + cell * scale
        var x = offset.x
        var y = offset.y
        if (left < 0f) x -= left
        else if (right > viewport.width) x -= right - viewport.width
        if (top < 0f) y -= top
        else if (bottom > viewport.height) y -= bottom - viewport.height
        offset = Offset(x, y)
        clamp(viewport)
    }

    fun clamp(viewport: Size) {
        scale = scale.coerceIn(MIN_SCALE, maxScale)
        val contentW = viewport.width * scale
        val contentH = viewport.height * scale
        val maxX = max(0f, (contentW - viewport.width))
        val maxY = max(0f, (contentH - viewport.height))
        offset = Offset(offset.x.coerceIn(-maxX, 0f), offset.y.coerceIn(-maxY, 0f))
    }

    companion object {
        const val MIN_SCALE = 1f
    }
}

/**
 * Renders the whole grid on a single Canvas. One draw pass keeps 18x18 (324 cells + notes) cheap,
 * and pinch-to-zoom / pan is a pure transform so panning stays fluid on phones.
 */
@Composable
fun SudokuBoard(
    game: GameState,
    selectedIndex: Int,
    activeHint: Hint?,
    candidateMasks: IntArray?,
    transform: BoardTransform,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    val textMeasurer = rememberTextMeasurer()
    val topology = game.topology
    val n = topology.width
    val outsideMargin = if (game.variant == Variant.OUTSIDE) 2 else 0
    val iconMode = game.iconMode && game.variant == null && (n == 9 || n == 12)
    val iconPainters = if (iconMode) HolidayIcons.take(n).map { rememberVectorPainter(it) } else emptyList()
    // Digits repeat across the board, so each (text, style) is laid out once per cell size instead of
    // once per cell per frame (18x18 = 324 cells plus notes).
    val layoutCache = remember(n, colors, game.variant) { TextLayoutCache(textMeasurer) }

    val hintRelated = remember(activeHint) { activeHint?.relatedIndices?.toSet() ?: emptySet() }
    val selectedPeers = remember(selectedIndex, topology) {
        if (selectedIndex >= 0) HintEngine.peersOf(selectedIndex, topology).toSet() else emptySet()
    }
    val selectedValue = if (selectedIndex >= 0 && topology.active[selectedIndex]) game.cells[selectedIndex].value else 0
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(selectedIndex, viewportSize, topology) {
        transform.keepVisible(
            selectedIndex,
            topology,
            Size(viewportSize.width.toFloat(), viewportSize.height.toFloat()),
            outsideMargin,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clipToBounds()
            .onSizeChanged { size ->
                viewportSize = size
                transform.initialize(
                    game.variant,
                    topology,
                    Size(size.width.toFloat(), size.height.toFloat()),
                )
            }
            .pointerInput(n, topology.height) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val viewport = Size(size.width.toFloat(), size.height.toFloat())
                    val oldScale = transform.scale
                    val newScale = (oldScale * zoom).coerceIn(BoardTransform.MIN_SCALE, transform.maxScale)
                    // Keep the point under the fingers fixed while zooming.
                    val focal = centroid - transform.offset
                    transform.offset = transform.offset - focal * (newScale / oldScale - 1f) + pan
                    transform.scale = newScale
                    transform.clamp(viewport)
                }
            }
            .pointerInput(n, topology.height, outsideMargin) {
                detectTapGestures { tap ->
                    val local = (tap - transform.offset) / transform.scale
                    val cell = size.width.toFloat() / (n + outsideMargin)
                    val margin = if (outsideMargin > 0) cell else 0f
                    val col = floor((local.x - margin) / cell).toInt()
                    val row = floor((local.y - margin) / cell).toInt()
                    if (row in 0 until topology.height && col in 0 until topology.width) {
                        val index = row * topology.width + col
                        if (topology.active[index]) onCellTap(index)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            translate(transform.offset.x, transform.offset.y) {
                scale(transform.scale, pivot = Offset.Zero) {
                    drawBoard(
                        game = game,
                        selectedIndex = selectedIndex,
                        selectedValue = selectedValue,
                        selectedPeers = selectedPeers,
                        activeHint = activeHint,
                        hintRelated = hintRelated,
                        candidateMasks = candidateMasks,
                        iconPainters = iconPainters,
                        colors = colors,
                        layouts = layoutCache,
                        outsideMargin = outsideMargin,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawBoard(
    game: GameState,
    selectedIndex: Int,
    selectedValue: Int,
    selectedPeers: Set<Int>,
    activeHint: Hint?,
    hintRelated: Set<Int>,
    candidateMasks: IntArray?,
    iconPainters: List<Painter>,
    colors: com.maslarski.sudoku.ui.theme.BoardColors,
    layouts: TextLayoutCache,
    outsideMargin: Int,
) {
    val topology = game.topology
    val width = topology.width
    val height = topology.height
    val marginCells = outsideMargin / 2
    val cell = size.width / (width + outsideMargin)
    val origin = marginCells * cell
    layouts.forCellSize(cell)

    drawRect(colors.cellBackground)

    for (index in topology.activeIndices) {
        val row = index / width
        val col = index % width
        val c = game.cells[index]
        val bg = when {
            activeHint != null && index == activeHint.index -> colors.hintTarget
            index in hintRelated -> colors.hintRelated
            index == selectedIndex -> colors.selected
            selectedValue != 0 && c.value == selectedValue -> colors.sameValue
            index in selectedPeers -> colors.related
            else -> null
        }
        if (bg != null) {
            drawRect(bg, topLeft = Offset(origin + col * cell, origin + row * cell), size = Size(cell, cell))
        }
    }

    val valueSize = spOf(cell * 0.55f)
    val valueCount = if (game.variant == null) game.gridSize.size else 9
    val noteCols = if (game.variant == null) game.gridSize.boxCols.coerceAtLeast(3) else 3
    val noteRows = (valueCount + noteCols - 1) / noteCols
    val noteSize = spOf(cell / max(noteCols, noteRows) * 0.8f)
    val valueStyleGiven = TextStyle(fontSize = valueSize, fontWeight = FontWeight.SemiBold, color = colors.givenText)
    val valueStyleEntered = TextStyle(fontSize = valueSize, color = colors.enteredText)
    val valueStyleError = TextStyle(fontSize = valueSize, color = colors.errorText)
    val noteStyle = TextStyle(fontSize = noteSize, color = colors.noteText)
    val errorBackground = colors.errorText.copy(alpha = 0.25f)
    val enteredBarHeight = 2.dp.toPx()

    for (index in topology.activeIndices) {
        val row = index / width
        val col = index % width
        val c = game.cells[index]
        val x = origin + col * cell
        val y = origin + row * cell
        if (c.value != 0) {
            if (iconPainters.isNotEmpty() && c.value <= iconPainters.size) {
                if (c.isError) {
                    drawRect(errorBackground, topLeft = Offset(x, y), size = Size(cell, cell))
                }
                val iconSize = cell * 0.72f
                with(iconPainters[c.value - 1]) {
                    translate(x + (cell - iconSize) / 2f, y + (cell - iconSize) / 2f) {
                        draw(Size(iconSize, iconSize))
                    }
                }
                if (!c.isGiven) {
                    drawRect(
                        color = colors.enteredText,
                        topLeft = Offset(x, y + cell - enteredBarHeight),
                        size = Size(cell, enteredBarHeight),
                    )
                }
            } else {
                val style = when {
                    c.isGiven -> valueStyleGiven
                    c.isError -> valueStyleError
                    else -> valueStyleEntered
                }
                val layout = layouts.get(c.value, style)
                drawText(
                    layout,
                    topLeft = Offset(x + (cell - layout.size.width) / 2f, y + (cell - layout.size.height) / 2f),
                )
            }
        } else if ((candidateMasks?.getOrNull(index) ?: c.notes) != 0) {
            val notes = candidateMasks?.getOrNull(index) ?: c.notes
            val subW = cell / noteCols
            val subH = cell / noteRows
            if (iconPainters.isNotEmpty()) {
                val iconSize = minOf(subW, subH) * 0.72f
                for (v in 1..valueCount) {
                    if (notes and (1 shl (v - 1)) == 0 || v > iconPainters.size) continue
                    val k = v - 1
                    val nc = k % noteCols
                    val nr = k / noteCols
                    with(iconPainters[v - 1]) {
                        translate(
                            x + nc * subW + (subW - iconSize) / 2f,
                            y + nr * subH + (subH - iconSize) / 2f,
                        ) {
                            draw(Size(iconSize, iconSize))
                        }
                    }
                }
            } else {
                for (v in 1..valueCount) {
                    if (notes and (1 shl (v - 1)) == 0) continue
                    val k = v - 1
                    val nc = k % noteCols
                    val nr = k / noteCols
                    val layout = layouts.get(v, noteStyle)
                    drawText(
                        layout,
                        topLeft = Offset(
                            x + nc * subW + (subW - layout.size.width) / 2f,
                            y + nr * subH + (subH - layout.size.height) / 2f,
                        ),
                    )
                }
            }
        }
    }

    val thin = max(1f, cell * 0.02f)
    val thick = max(2f, cell * 0.06f)
    for (index in topology.activeIndices) {
        val row = index / width
        val col = index % width
        val left = origin + col * cell
        val top = origin + row * cell
        if (col + 1 < width && topology.active[index + 1]) {
            drawLine(colors.thinLine, Offset(left + cell, top), Offset(left + cell, top + cell), thin)
        }
        if (row + 1 < height && topology.active[index + width]) {
            drawLine(colors.thinLine, Offset(left, top + cell), Offset(left + cell, top + cell), thin)
        }
    }
    topology.boxOutlines.forEach { outline ->
        drawRect(
            color = colors.thickLine,
            topLeft = Offset(origin + outline.col * cell, origin + outline.row * cell),
            size = Size(outline.width * cell, outline.height * cell),
            style = Stroke(width = thick),
        )
    }

    val clues = game.puzzle.outsideClues
    if (clues != null && outsideMargin > 0) {
        val clueStyle = TextStyle(
            fontSize = spOf(cell * 0.34f),
            fontWeight = FontWeight.Bold,
            color = colors.noteText,
        )
        fun drawStack(values: List<Int>, left: Float, top: Float) {
            val visible = values.take(2)
            if (visible.isEmpty()) return
            val layoutsForValues = visible.map { layouts.get(it, clueStyle) }
            val totalHeight = layoutsForValues.sumOf { it.size.height.toDouble() }.toFloat()
            var y = top + (cell - totalHeight) / 2f
            layoutsForValues.forEach { layout ->
                drawText(
                    layout,
                    topLeft = Offset(left + (cell - layout.size.width) / 2f, y),
                )
                y += layout.size.height
            }
        }
        for (line in 0 until 9) {
            drawStack(clues.rowsFromLeft.getOrNull(line).orEmpty(), 0f, origin + line * cell)
            drawStack(clues.rowsFromRight.getOrNull(line).orEmpty(), origin + 9 * cell, origin + line * cell)
            drawStack(clues.columnsFromTop.getOrNull(line).orEmpty(), origin + line * cell, 0f)
            drawStack(clues.columnsFromBottom.getOrNull(line).orEmpty(), origin + line * cell, origin + 9 * cell)
        }
    }
}

private class TextLayoutCache(private val measurer: androidx.compose.ui.text.TextMeasurer) {
    private val layouts = HashMap<Pair<Int, TextStyle>, TextLayoutResult>()
    private var cellSize = -1f

    fun forCellSize(cell: Float) {
        if (cell != cellSize) {
            layouts.clear()
            cellSize = cell
        }
    }

    fun get(value: Int, style: TextStyle): TextLayoutResult =
        layouts.getOrPut(value to style) { measurer.measure(value.toString(), style) }
}

private fun DrawScope.spOf(px: Float) = (px / density / fontScale).sp
