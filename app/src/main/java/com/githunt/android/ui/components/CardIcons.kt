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

    const val ChevronRight = "M9 6l6 6-6 6"

    const val Folder = "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"

    const val User = "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0"

    const val Tag = "M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8z M7.5 7.5h.01"

    /** The GitHub mark (filled). Draw without a strokeWidth. */
    const val GitHub =
        "M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 " +
            "0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7" +
            "c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998" +
            ".108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22" +
            "-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405" +
            " 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92" +
            ".42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297" +
            "c0-6.627-5.373-12-12-12z"
}
