package com.maslarski.sudoku.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.maslarski.sudoku.R

val HolidayIcons: List<ImageVector> = listOf(
    ImageVector.Builder("Santa", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFFD83A43)), stroke = SolidColor(Color(0xFF8D2631)), strokeLineWidth = 0.7f) {
            moveTo(4f, 10f)
            curveTo(4f, 5.7f, 7.2f, 3f, 12f, 3f)
            curveTo(16.3f, 3f, 19f, 5.6f, 19.5f, 10f)
            close()
        }
        path(fill = SolidColor(Color(0xFFF8F3E7))) {
            moveTo(4f, 9f)
            lineTo(19f, 9f)
            curveTo(21f, 9f, 21f, 12f, 19f, 12f)
            lineTo(5f, 12f)
            curveTo(3f, 12f, 2.7f, 9f, 4f, 9f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFD66B))) {
            moveTo(7f, 11f)
            curveTo(7f, 9f, 9f, 8f, 12f, 8f)
            curveTo(15f, 8f, 17f, 10f, 17f, 13f)
            lineTo(17f, 16f)
            curveTo(16f, 19f, 14f, 21f, 12f, 21f)
            curveTo(9f, 21f, 7f, 18f, 7f, 15f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFFFFF))) {
            moveTo(7f, 15f)
            curveTo(8f, 16f, 9f, 16f, 10f, 15f)
            curveTo(11f, 17f, 13f, 17f, 14f, 15f)
            curveTo(15f, 16f, 16f, 16f, 17f, 15f)
            curveTo(17f, 19f, 15f, 22f, 12f, 22f)
            curveTo(9f, 22f, 7f, 19f, 7f, 15f)
            close()
        }
        path(fill = SolidColor(Color(0xFF3A2A2A))) {
            moveTo(9f, 12f)
            curveTo(9f, 11f, 10.5f, 11f, 10.5f, 12f)
            curveTo(10.5f, 13f, 9f, 13f, 9f, 12f)
            close()
            moveTo(13.5f, 12f)
            curveTo(13.5f, 11f, 15f, 11f, 15f, 12f)
            curveTo(15f, 13f, 13.5f, 13f, 13.5f, 12f)
            close()
        }
        path(fill = SolidColor(Color(0xFFD83A43))) {
            moveTo(11f, 13f)
            lineTo(13f, 13f)
            lineTo(12f, 15f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFFFFF))) {
            moveTo(17f, 4f)
            curveTo(19f, 3f, 21f, 5f, 20f, 7f)
            curveTo(19f, 9f, 16f, 8f, 16f, 6f)
            curveTo(16f, 5f, 16f, 4f, 17f, 4f)
            close()
        }
    }.build(),
    ImageVector.Builder("Christmas tree", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFF795548))) {
            moveTo(10f, 18f)
            lineTo(14f, 18f)
            lineTo(14f, 22f)
            lineTo(10f, 22f)
            close()
        }
        path(fill = SolidColor(Color(0xFF168A50))) {
            moveTo(12f, 3f)
            lineTo(7f, 9f)
            lineTo(10f, 9f)
            lineTo(5f, 15f)
            lineTo(9f, 15f)
            lineTo(3f, 20f)
            lineTo(21f, 20f)
            lineTo(15f, 15f)
            lineTo(19f, 15f)
            lineTo(14f, 9f)
            lineTo(17f, 9f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFC928))) {
            moveTo(12f, 1f)
            lineTo(13.2f, 3.2f)
            lineTo(15.7f, 3.6f)
            lineTo(13.9f, 5.3f)
            lineTo(14.3f, 7.8f)
            lineTo(12f, 6.6f)
            lineTo(9.7f, 7.8f)
            lineTo(10.1f, 5.3f)
            lineTo(8.3f, 3.6f)
            lineTo(10.8f, 3.2f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFD84D))) {
            moveTo(7f, 11f)
            curveTo(7f, 10f, 9f, 10f, 9f, 11f)
            curveTo(9f, 12f, 7f, 12f, 7f, 11f)
            close()
            moveTo(15f, 14f)
            curveTo(15f, 13f, 17f, 13f, 17f, 14f)
            curveTo(17f, 15f, 15f, 15f, 15f, 14f)
            close()
        }
    }.build(),
    ImageVector.Builder("Snowflake", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color(0xFF1E3A5F)),
            strokeLineWidth = 3f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3f)
            lineTo(12f, 21f)
            moveTo(4.2f, 7.5f)
            lineTo(19.8f, 16.5f)
            moveTo(19.8f, 7.5f)
            lineTo(4.2f, 16.5f)
            moveTo(12f, 3f)
            lineTo(10f, 5f)
            moveTo(12f, 3f)
            lineTo(14f, 5f)
            moveTo(12f, 21f)
            lineTo(10f, 19f)
            moveTo(12f, 21f)
            lineTo(14f, 19f)
        }
        path(
            stroke = SolidColor(Color(0xFF8DE5FF)),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3f)
            lineTo(12f, 21f)
            moveTo(4.2f, 7.5f)
            lineTo(19.8f, 16.5f)
            moveTo(19.8f, 7.5f)
            lineTo(4.2f, 16.5f)
            moveTo(12f, 3f)
            lineTo(10f, 5f)
            moveTo(12f, 3f)
            lineTo(14f, 5f)
            moveTo(12f, 21f)
            lineTo(10f, 19f)
            moveTo(12f, 21f)
            lineTo(14f, 19f)
        }
    }.build(),
    ImageVector.Builder("Gift", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFFD9424E)), stroke = SolidColor(Color(0xFF8F2632)), strokeLineWidth = 0.8f) {
            moveTo(4f, 9f)
            lineTo(20f, 9f)
            lineTo(20f, 21f)
            lineTo(4f, 21f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFC928))) {
            moveTo(10f, 9f)
            lineTo(14f, 9f)
            lineTo(14f, 21f)
            lineTo(10f, 21f)
            close()
            moveTo(3f, 6f)
            lineTo(21f, 6f)
            lineTo(21f, 10f)
            lineTo(3f, 10f)
            close()
        }
        path(fill = SolidColor(Color(0xFFED5360))) {
            moveTo(12f, 6f)
            curveTo(9f, 2f, 5f, 3f, 7f, 6f)
            lineTo(12f, 7f)
            close()
        }
        path(fill = SolidColor(Color(0xFFED5360))) {
            moveTo(12f, 6f)
            curveTo(15f, 2f, 19f, 3f, 17f, 6f)
            lineTo(12f, 7f)
            close()
        }
        path(fill = SolidColor(Color(0xFF8F2632))) {
            moveTo(11f, 6f)
            lineTo(13f, 6f)
            lineTo(13f, 9f)
            lineTo(11f, 9f)
            close()
        }
    }.build(),
    ImageVector.Builder("Snowman", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color(0xFFEAF7FF)),
            stroke = SolidColor(Color(0xFF1E3A5F)),
            strokeLineWidth = 1.2f,
        ) {
            moveTo(12f, 2f)
            curveTo(16f, 2f, 18f, 5f, 18f, 9f)
            curveTo(18f, 10f, 17.5f, 11f, 17f, 12f)
            curveTo(20f, 14f, 21f, 17f, 20f, 20f)
            curveTo(19f, 23f, 5f, 23f, 4f, 20f)
            curveTo(3f, 17f, 4f, 14f, 7f, 12f)
            curveTo(6.5f, 11f, 6f, 10f, 6f, 9f)
            curveTo(6f, 5f, 8f, 2f, 12f, 2f)
            close()
        }
        path(fill = SolidColor(Color(0xFF25344A))) {
            moveTo(9f, 7f)
            curveTo(9f, 6f, 10.5f, 6f, 10.5f, 7f)
            curveTo(10.5f, 8f, 9f, 8f, 9f, 7f)
            close()
            moveTo(13.5f, 7f)
            curveTo(13.5f, 6f, 15f, 6f, 15f, 7f)
            curveTo(15f, 8f, 13.5f, 8f, 13.5f, 7f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFF8A24))) {
            moveTo(11f, 8f)
            lineTo(16f, 9f)
            lineTo(11f, 10f)
            close()
        }
        path(fill = SolidColor(Color(0xFF25344A))) {
            moveTo(11f, 14f)
            curveTo(11f, 13f, 13f, 13f, 13f, 14f)
            curveTo(13f, 15f, 11f, 15f, 11f, 14f)
            close()
            moveTo(11f, 18f)
            curveTo(11f, 17f, 13f, 17f, 13f, 18f)
            curveTo(13f, 19f, 11f, 19f, 11f, 18f)
            close()
        }
        path(fill = SolidColor(Color(0xFFB5283C))) {
            moveTo(7f, 12f)
            lineTo(18f, 12f)
            lineTo(17f, 14f)
            lineTo(8f, 14f)
            close()
        }
    }.build(),
    ImageVector.Builder("Bell", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color(0xFFFFC928)),
            stroke = SolidColor(Color(0xFF7A4A08)),
            strokeLineWidth = 1f,
        ) {
            moveTo(12f, 3f)
            curveTo(8f, 3f, 7f, 6f, 7f, 10f)
            curveTo(7f, 10f, 6f, 15f, 4f, 17f)
            curveTo(3f, 19f, 5f, 20f, 7f, 20f)
            lineTo(17f, 20f)
            curveTo(19f, 20f, 21f, 19f, 20f, 17f)
            curveTo(18f, 15f, 17f, 10f, 17f, 10f)
            curveTo(17f, 6f, 16f, 3f, 12f, 3f)
            close()
        }
        path(fill = SolidColor(Color(0xFFB5283C))) {
            moveTo(9f, 20f)
            curveTo(9f, 23f, 15f, 23f, 15f, 20f)
            close()
        }
        path(fill = SolidColor(Color(0xFF7A4A08))) {
            moveTo(11f, 2f)
            curveTo(11f, 1f, 13f, 1f, 13f, 2f)
            lineTo(13f, 4f)
            lineTo(11f, 4f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFE28A))) {
            moveTo(9f, 7f)
            curveTo(9f, 5f, 11f, 4f, 12f, 5f)
            curveTo(10f, 7f, 10f, 11f, 9f, 14f)
            curveTo(8f, 12f, 8f, 9f, 9f, 7f)
            close()
        }
    }.build(),
    ImageVector.Builder("Gingerbread man", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFF9B552E)), stroke = SolidColor(Color(0xFF5E321F)), strokeLineWidth = 0.8f) {
            moveTo(12f, 3f)
            curveTo(15f, 3f, 16f, 6f, 14f, 8f)
            lineTo(18f, 11f)
            curveTo(20f, 12f, 19f, 15f, 17f, 15f)
            lineTo(15f, 14f)
            lineTo(16f, 20f)
            curveTo(16f, 22f, 13f, 23f, 12f, 20f)
            curveTo(11f, 23f, 8f, 22f, 8f, 20f)
            lineTo(9f, 14f)
            lineTo(7f, 15f)
            curveTo(5f, 15f, 4f, 12f, 6f, 11f)
            lineTo(10f, 8f)
            curveTo(8f, 6f, 9f, 3f, 12f, 3f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFFFFF))) {
            moveTo(9f, 8f)
            curveTo(10f, 9f, 11f, 9f, 12f, 8f)
            curveTo(13f, 9f, 14f, 9f, 15f, 8f)
            lineTo(14f, 10f)
            lineTo(10f, 10f)
            close()
            moveTo(9f, 16f)
            lineTo(11f, 16f)
            lineTo(10f, 18f)
            close()
            moveTo(13f, 16f)
            lineTo(15f, 16f)
            lineTo(14f, 18f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFD24A))) {
            moveTo(11f, 12f)
            curveTo(11f, 11f, 13f, 11f, 13f, 12f)
            curveTo(13f, 13f, 11f, 13f, 11f, 12f)
            close()
            moveTo(11f, 14f)
            curveTo(11f, 13f, 13f, 13f, 13f, 14f)
            curveTo(13f, 15f, 11f, 15f, 11f, 14f)
            close()
        }
        path(fill = SolidColor(Color(0xFF36251E))) {
            moveTo(10f, 6f)
            curveTo(10f, 5f, 11f, 5f, 11f, 6f)
            curveTo(11f, 7f, 10f, 7f, 10f, 6f)
            close()
            moveTo(13f, 6f)
            curveTo(13f, 5f, 14f, 5f, 14f, 6f)
            curveTo(14f, 7f, 13f, 7f, 13f, 6f)
            close()
        }
    }.build(),
    ImageVector.Builder("Candy cane", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color(0xFFFFFFFF)),
            strokeLineWidth = 7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 20f)
            lineTo(8f, 9f)
            curveTo(8f, 2f, 18f, 2f, 18f, 8f)
            curveTo(18f, 12f, 15f, 13f, 11f, 13f)
        }
        path(
            stroke = SolidColor(Color(0xFFD83A43)),
            strokeLineWidth = 2.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 20f)
            lineTo(8f, 9f)
            curveTo(8f, 2f, 18f, 2f, 18f, 8f)
            curveTo(18f, 12f, 15f, 13f, 11f, 13f)
        }
        path(
            stroke = SolidColor(Color(0xFFFFFFFF)),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(6.9f, 17f)
            lineTo(9.1f, 15.5f)
            moveTo(6.9f, 12.5f)
            lineTo(9.1f, 11f)
            moveTo(7f, 8f)
            lineTo(9.5f, 7f)
        }
    }.build(),
    ImageVector.Builder("Holly", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFF167342)), stroke = SolidColor(Color(0xFF0B4A2A)), strokeLineWidth = 0.7f) {
            moveTo(11f, 13f)
            curveTo(6f, 14f, 4f, 11f, 4f, 7f)
            lineTo(7f, 8f)
            lineTo(8f, 4f)
            lineTo(11f, 7f)
            lineTo(14f, 4f)
            lineTo(14f, 8f)
            lineTo(18f, 6f)
            curveTo(18f, 11f, 15f, 14f, 11f, 13f)
            close()
        }
        path(fill = SolidColor(Color(0xFF238C50)), stroke = SolidColor(Color(0xFF0B4A2A)), strokeLineWidth = 0.7f) {
            moveTo(13f, 13f)
            curveTo(17f, 10f, 20f, 12f, 20f, 16f)
            lineTo(17f, 15f)
            lineTo(17f, 20f)
            lineTo(14f, 17f)
            lineTo(11f, 20f)
            lineTo(11f, 16f)
            lineTo(7f, 18f)
            curveTo(7f, 14f, 9f, 12f, 13f, 13f)
            close()
        }
        path(
            stroke = SolidColor(Color(0xFF9DE082)),
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(9f, 8f)
            lineTo(12f, 11f)
            moveTo(16f, 15f)
            lineTo(13f, 17f)
        }
        path(fill = SolidColor(Color(0xFFE33647))) {
            moveTo(8f, 12f)
            curveTo(8f, 10f, 11f, 10f, 11f, 12f)
            curveTo(11f, 14f, 8f, 14f, 8f, 12f)
            close()
            moveTo(12f, 14f)
            curveTo(12f, 12f, 15f, 12f, 15f, 14f)
            curveTo(15f, 16f, 12f, 16f, 12f, 14f)
            close()
            moveTo(16f, 11f)
            curveTo(16f, 9f, 19f, 9f, 19f, 11f)
            curveTo(19f, 13f, 16f, 13f, 16f, 11f)
            close()
        }
    }.build(),
    ImageVector.Builder("Reindeer", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color(0xFF5A3523)),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 9f)
            lineTo(5f, 3f)
            lineTo(3f, 4f)
            moveTo(5f, 3f)
            lineTo(8f, 4f)
            moveTo(16f, 9f)
            lineTo(19f, 3f)
            lineTo(21f, 4f)
            moveTo(19f, 3f)
            lineTo(16f, 4f)
        }
        path(fill = SolidColor(Color(0xFF9A603C)), stroke = SolidColor(Color(0xFF5A3523)), strokeLineWidth = 0.8f) {
            moveTo(12f, 5f)
            curveTo(17f, 5f, 19f, 9f, 18f, 14f)
            curveTo(18f, 18f, 15f, 21f, 12f, 21f)
            curveTo(9f, 21f, 6f, 18f, 6f, 14f)
            curveTo(5f, 9f, 7f, 5f, 12f, 5f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFE0B2))) {
            moveTo(8f, 15f)
            curveTo(8f, 12f, 16f, 12f, 16f, 15f)
            curveTo(16f, 19f, 8f, 19f, 8f, 15f)
            close()
        }
        path(fill = SolidColor(Color(0xFF30231F))) {
            moveTo(8f, 10f)
            curveTo(8f, 9f, 10f, 9f, 10f, 10f)
            curveTo(10f, 11f, 8f, 11f, 8f, 10f)
            close()
            moveTo(14f, 10f)
            curveTo(14f, 9f, 16f, 9f, 16f, 10f)
            curveTo(16f, 11f, 14f, 11f, 14f, 10f)
            close()
        }
        path(fill = SolidColor(Color(0xFFC8263A))) {
            moveTo(10f, 14f)
            curveTo(10f, 12f, 14f, 12f, 14f, 14f)
            curveTo(14f, 16f, 10f, 16f, 10f, 14f)
            close()
        }
    }.build(),
    ImageVector.Builder("Winter hat", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color(0xFF3478C8)), stroke = SolidColor(Color(0xFF1E3A5F)), strokeLineWidth = 0.9f) {
            moveTo(5f, 15f)
            curveTo(5f, 8f, 8f, 4f, 13f, 4f)
            curveTo(17f, 4f, 19f, 8f, 19f, 15f)
            close()
        }
        path(fill = SolidColor(Color(0xFFE8F2F8)), stroke = SolidColor(Color(0xFF1E3A5F)), strokeLineWidth = 0.8f) {
            moveTo(4f, 14f)
            curveTo(4f, 13f, 20f, 13f, 20f, 14f)
            lineTo(20f, 18f)
            curveTo(20f, 20f, 4f, 20f, 4f, 18f)
            close()
        }
        path(fill = SolidColor(Color(0xFFEDF5FA)), stroke = SolidColor(Color(0xFF1E3A5F)), strokeLineWidth = 0.7f) {
            moveTo(11f, 2f)
            curveTo(11f, 0f, 14f, 0f, 14f, 2f)
            curveTo(14f, 4f, 11f, 4f, 11f, 2f)
            close()
        }
        path(
            stroke = SolidColor(Color(0xFFFFD166)),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(7f, 10f)
            lineTo(17f, 10f)
        }
    }.build(),
    ImageVector.Builder("Star", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color(0xFFFFD12E)),
            stroke = SolidColor(Color(0xFFB35E08)),
            strokeLineWidth = 1f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 2f)
            lineTo(14.8f, 8.2f)
            lineTo(21.5f, 8.8f)
            lineTo(16.5f, 13.2f)
            lineTo(18f, 20f)
            lineTo(12f, 16.5f)
            lineTo(6f, 20f)
            lineTo(7.5f, 13.2f)
            lineTo(2.5f, 8.8f)
            lineTo(9.2f, 8.2f)
            close()
        }
        path(fill = SolidColor(Color(0xFFFFF1A8))) {
            moveTo(12f, 5f)
            lineTo(13.5f, 9f)
            lineTo(11f, 11f)
            close()
        }
    }.build(),
)

val holidayIconNames: List<Int> = listOf(
    R.string.icon_santa,
    R.string.icon_christmas_tree,
    R.string.icon_snowflake,
    R.string.icon_gift,
    R.string.icon_snowman,
    R.string.icon_bell,
    R.string.icon_gingerbread_man,
    R.string.icon_candy_cane,
    R.string.icon_holly,
    R.string.icon_reindeer,
    R.string.icon_winter_hat,
    R.string.icon_star,
)
