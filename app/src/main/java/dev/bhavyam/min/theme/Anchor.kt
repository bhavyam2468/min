package dev.bhavyam.min.theme

/** Where a block sits on home: a column (left · centre · right) and a row (top · middle · bottom). */
enum class Anchor(val column: Int, val row: Int, val label: String) {
    TopLeft(0, 0, "Top left"), Top(1, 0, "Top"), TopRight(2, 0, "Top right"),
    Left(0, 1, "Left"), Center(1, 1, "Centre"), Right(2, 1, "Right"),
    BottomLeft(0, 2, "Bottom left"), Bottom(1, 2, "Bottom"), BottomRight(2, 2, "Bottom right"),
}
