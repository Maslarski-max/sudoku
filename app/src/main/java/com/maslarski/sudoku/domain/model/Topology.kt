package com.maslarski.sudoku.domain.model

enum class UnitKind { ROW, COLUMN, BOX }

data class BoxOutline(
    val row: Int,
    val col: Int,
    val width: Int,
    val height: Int,
    val isSubgrid: Boolean = false,
)

/** Cell membership and all Sudoku constraints for a board. Cell indices are row-major. */
class Topology private constructor(
    val width: Int,
    val height: Int,
    val active: BooleanArray,
    val units: List<IntArray>,
    val unitKinds: List<UnitKind>,
    val boxOutlines: List<BoxOutline>,
) {
    val cellCount: Int get() = width * height
    val activeIndices: IntArray = active.indices.filter { active[it] }.toIntArray()

    private val cellUnits: Array<IntArray> = Array(cellCount) { index ->
        units.indices.filter { index in units[it] }.toIntArray()
    }
    private val peersByCell: Array<IntArray> = Array(cellCount) { index ->
        if (!active[index]) {
            intArrayOf()
        } else {
            cellUnits[index].flatMap { units[it].asIterable() }.asSequence()
                .filter { it != index }
                .distinct()
                .toList()
                .toIntArray()
        }
    }

    fun peers(index: Int): IntArray = peersByCell[index]

    fun unitsFor(index: Int): IntArray = cellUnits[index]

    companion object {
        fun classic(gridSize: GridSize): Topology {
            val n = gridSize.size
            val rows = (0 until n).map { row -> IntArray(n) { col -> row * n + col } }
            val columns = (0 until n).map { col -> IntArray(n) { row -> row * n + col } }
            val boxes = buildList {
                for (row in 0 until n step gridSize.boxRows) {
                    for (col in 0 until n step gridSize.boxCols) {
                        add(IntArray(gridSize.boxRows * gridSize.boxCols) { i ->
                            (row + i / gridSize.boxCols) * n + col + i % gridSize.boxCols
                        })
                    }
                }
            }
            val outlines = buildList {
                for (row in 0 until n step gridSize.boxRows) {
                    for (col in 0 until n step gridSize.boxCols) {
                        add(BoxOutline(row, col, gridSize.boxCols, gridSize.boxRows))
                    }
                }
            }
            val allUnits = rows + columns + boxes
            return Topology(
                width = n,
                height = n,
                active = BooleanArray(n * n) { true },
                units = allUnits,
                unitKinds = List(rows.size) { UnitKind.ROW } +
                    List(columns.size) { UnitKind.COLUMN } +
                    List(boxes.size) { UnitKind.BOX },
                boxOutlines = outlines,
            )
        }

        fun multi(width: Int, height: Int, offsets: List<Pair<Int, Int>>): Topology {
            require(width > 0 && height > 0)
            require(offsets.isNotEmpty())
            val active = BooleanArray(width * height)
            val rows = mutableListOf<IntArray>()
            val columns = mutableListOf<IntArray>()
            val boxes = mutableListOf<IntArray>()
            val outlines = linkedMapOf<Triple<Int, Int, Boolean>, BoxOutline>()

            offsets.forEach { (top, left) ->
                require(top >= 0 && left >= 0 && top + 9 <= height && left + 9 <= width)
                for (row in top until top + 9) {
                    for (col in left until left + 9) active[row * width + col] = true
                }
                for (row in top until top + 9) {
                    rows += IntArray(9) { col -> row * width + left + col }
                }
                for (col in left until left + 9) {
                    columns += IntArray(9) { row -> (top + row) * width + col }
                }
                for (row in top until top + 9 step 3) {
                    for (col in left until left + 9 step 3) {
                        val cells = IntArray(9) { i -> (row + i / 3) * width + col + i % 3 }
                        boxes += cells
                        outlines.putIfAbsent(
                            Triple(row, col, false),
                            BoxOutline(row, col, width = 3, height = 3),
                        )
                    }
                }
                outlines.putIfAbsent(
                    Triple(top, left, true),
                    BoxOutline(top, left, width = 9, height = 9, isSubgrid = true),
                )
            }

            fun distinctUnits(source: List<IntArray>): List<IntArray> {
                val seen = mutableSetOf<String>()
                return source.filter { unit ->
                    seen.add(unit.sorted().joinToString(","))
                }
            }

            val uniqueRows = distinctUnits(rows)
            val uniqueColumns = distinctUnits(columns)
            val uniqueBoxes = distinctUnits(boxes)
            val units = uniqueRows + uniqueColumns + uniqueBoxes
            return Topology(
                width = width,
                height = height,
                active = active,
                units = units,
                unitKinds = List(uniqueRows.size) { UnitKind.ROW } +
                    List(uniqueColumns.size) { UnitKind.COLUMN } +
                    List(uniqueBoxes.size) { UnitKind.BOX },
                boxOutlines = outlines.values.toList(),
            )
        }

        fun forVariant(variant: Variant?, gridSize: GridSize): Topology = when (variant) {
            Variant.SAMURAI -> multi(
                width = 21,
                height = 21,
                offsets = listOf(0 to 0, 0 to 12, 6 to 6, 12 to 0, 12 to 12),
            )
            Variant.FLOWER -> multi(
                width = 15,
                height = 15,
                offsets = listOf(0 to 3, 3 to 0, 3 to 3, 3 to 6, 6 to 3),
            )
            Variant.OUTSIDE, null -> classic(gridSize)
        }
    }
}
