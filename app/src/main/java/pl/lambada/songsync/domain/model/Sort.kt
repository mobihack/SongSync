package pl.lambada.songsync.domain.model

import androidx.annotation.StringRes
import pl.lambada.songsync.R

/**
 * Enum class representing sort orders.
 * @param queryName The query name for the sort order.
 * @param displayName The string resource ID for the display name.
 */
enum class SortOrders(val queryName: String, @StringRes val displayName: Int) {
    ASCENDING("ASC", R.string.ascending),
    DESCENDING("DESC", R.string.descending);

    companion object {
        /** The order saved as [queryName], or ascending for one no longer known. */
        fun fromQueryName(queryName: String?) = entries.find { it.queryName == queryName } ?: ASCENDING
    }
}

/**
 * Enum class representing sort values.
 * @param displayName The string resource ID for the display name.
 */
enum class SortValues(@StringRes val displayName: Int) {
    TITLE(R.string.title),
    ARTIST(R.string.artist),
    ALBUM(R.string.album),
    YEAR(R.string.year),
    DURATION(R.string.duration),
    DATE_ADDED(R.string.date_added),
    DATE_MODIFIED(R.string.date_modified);

    companion object {
        /** The value saved as [name], or title for one no longer known. */
        fun fromName(name: String?) = entries.find { it.name == name } ?: TITLE
    }
}
