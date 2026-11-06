package com.githunt.android.ui.components

/**
 * Outline icons copied from the web app's components/Icons.js (24 x 24 viewport).
 * Draw them with [PathIcon] and strokeWidth = 1.8f so they match the website.
 * Circles are written as two arcs because path data has no circle command.
 */
object CardIcons {
    const val Star =
        "M12 2.5L15.09 8.76L22 9.77L17 14.64L18.18 21.52L12 18.2L5.82 21.52L7 14.64L2 9.77L8.91 8.76Z"

    const val Fork =
        "M3.8 6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0 " +
            "M15.8 6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0 " +
            "M9.8 18a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0 " +
            "M6 8.2v2c0 2 1.5 3.3 3.3 3.3h5.4c1.8 0 3.3-1.3 3.3-3.3v-2 " +
            "M12 13.5v2.3"

    const val Share =
        "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 " +
            "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 " +
            "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 " +
            "M8.6 10.5L15.4 6.5 " +
            "M8.6 13.5L15.4 17.5"

    /** The web's "clip" bookmark. Pass filled = true when the repo is clipped. */
    const val Clip = "M6 3.5h12a1 1 0 0 1 1 1V21l-7-4.2L5 21V4.5a1 1 0 0 1 1-1z"

    /** Chevron on the Show README / Show less button. Rotate it 180 degrees when open. */
    const val ChevronDown = "M6 9L12 15L18 9"

    /** The AI mark on the Ask AI action (Material "auto awesome"). */
    const val Sparkles =
        "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9z " +
            "M11.5 9.5L9 4L6.5 9.5L1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5z " +
            "M19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z"

    const val Search = "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0 M21 21l-4.35-4.35"

    const val Close = "M6 6L18 18 M18 6L6 18"

    const val ArrowUp = "M12 19V5 M5 12l7-7 7 7"
}
