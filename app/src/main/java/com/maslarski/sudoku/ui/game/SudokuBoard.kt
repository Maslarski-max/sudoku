package com.maslarski.sudoku.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.maslarski.sudoku.domain.engine.HintEngine
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.ui.theme.LocalBoardColors
import kotlin.math.max

/** Zoom/pan state for the board. Exposed so the screen can offer a "reset zoom" action. */
class BoardTransform {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun clamp(viewport: Size) {
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        val contentW = viewport.width * scale
        val contentH = viewport.height * scale
        val maxX = max(0f, (contentW - viewport.width))
        val maxY = max(0f, (contentH - viewport.height))
        offset = Offset(offset.x.coerceIn(-maxX, 0f), offset.y.coerceIn(-maxY, 0f))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
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
    transform: BoardTransform,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    val textMeasurer = rememberTextMeasurer()
    val n = game.gridSize.size

    val hintRelated = remember(activeHint) { activeHint?.relatedIndices?.toSet() ?: emptySet() }
    val selectedPeers = remember(selectedIndex, game.gridSize) {
        if (selectedIndex >= 0) HintEngine.peersOf(selectedIndex, game.gridSize).toSet() else emptySet()
    }
    val selectedValue = if (selectedIndex >= 0) game.cells[selectedIndex].value else 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clipToBounds()
            .pointerInput(n) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val viewport = Size(size.width.toFloat(), size.height.toFloat())
                    val oldScale = transform.scale
                    val newScale = (oldScale * zoom).coerceIn(BoardTransform.MIN_SCALE, BoardTransform.MAX_SCALE)
                    // Keep the point under the fingers fixed while zooming.
                    val focal = centroid - transform.offset
                    transform.offset = transform.offset - focal * (newScale / oldScale - 1f) + pan
                    transform.scale = newScale
                    transform.clamp(viewport)
                }
            }
            .pointerInput(n) {
                detectTapGestures { tap ->
                    val local = (tap - transform.offset) / transform.scale
                    val cell = size.width.toFloat() / n
                    val col = (local.x / cell).toInt()
                    val row = (local.y / cell).toInt()
                    if (row in 0 until n && col in 0 until n) onCellTap(row * n + col)
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
                        colors = colors,
                        textMeasurer = textMeasurer,
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
    colors: com.maslarski.sudoku.ui.theme.BoardColors,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val gridSize = game.gridSize
    val n = gridSize.size
    val cell = size.width / n
    val boxRows = gridSize.boxRows
    val boxCols = gridSize.boxCols

    drawRect(colors.cellBackground)

    // Cell backgrounds
    for (index in 0 until n * n) {
        val row = index / n
        val col = index % n
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
            drawRect(bg, topLeft = Offset(col * cell, row * cell), size = Size(cell, cell))
        }
    }

    // Values and notes
    val valueSize = spOf(cell * 0.55f)
    val noteCols = gridSize.boxCols.coerceAtLeast(3)
    val noteRows = (n + noteCols - 1) / noteCols
    val noteSize = spOf(cell / max(noteCols, noteRows) * 0.8f)
    val valueStyleGiven = TextStyle(fontSize = valueSize, fontWeight = FontWeight.SemiBold, color = colors.givenText)
    val valueStyleEntered = TextStyle(fontSize = valueSize, color = colors.enteredText)
    val valueStyleError = TextStyle(fontSize = valueSize, color = colors.errorText)
    val noteStyle = TextStyle(fontSize = noteSize, color = colors.noteText)

    for (index in 0 until n * n) {
        val row = index / n
        val col = index % n
        val c = game.cells[index]
        val x = col * cell
        val y = row * cell
        if (c.value != 0) {
            val style = when {
                c.isGiven -> valueStyleGiven
                c.isError -> valueStyleError
                else -> valueStyleEntered
            }
            val layout = textMeasurer.measure(c.value.toString(), style)
            drawText(
                layout,
                topLeft = Offset(x + (cell - layout.size.width) / 2f, y + (cell - layout.size.height) / 2f),
            )
        } else if (c.notes != 0) {
            val subW = cell / noteCols
            val subH = cell / noteRows
            for (v in 1..n) {
                if (!c.hasNote(v)) continue
                val k = v - 1
                val nc = k % noteCols
                val nr = k / noteCols
                val layout = textMeasurer.measure(v.toString(), noteStyle)
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

    // Grid lines: thin between cells, thick on box borders.
    val thin = max(1f, cell * 0.02f)
    val thick = max(2f, cell * 0.06f)
    for (i in 0..n) {
        val isBoxRow = i % boxRows == 0
        val isBoxCol = i % boxCols == 0
        val p = i * cell
        drawLine(
            color = if (isBoxRow) colors.thickLine else colors.thinLine,
            start = Offset(0f, p),
            end = Offset(size.width, p),
            strokeWidth = if (isBoxRow) thick else thin,
        )
        drawLine(
            color = if (isBoxCol) colors.thickLine else colors.thinLine,
            start = Offset(p, 0f),
            end = Offset(p, size.height),
            strokeWidth = if (isBoxCol) thick else thin,
        )
    }
    drawRect(colors.thickLine, style = Stroke(width = thick))
}

private fun DrawScope.spOf(px: Float) = (px / density / fontScale).sp
