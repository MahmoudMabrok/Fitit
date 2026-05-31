package tools.mo3ta.fitit.ui.mediamerger

/** Kind of media the user is merging. Only items of the same type can be combined. */
enum class MediaType { VIDEO, AUDIO }

const val MEDIA_MERGER_MIN_ITEMS = 2
const val MEDIA_MERGER_MAX_ITEMS = 10

/** Whether the current selection can be merged into a single file. */
fun canMerge(itemCount: Int, isProcessing: Boolean): Boolean =
    itemCount >= MEDIA_MERGER_MIN_ITEMS && !isProcessing

/** Returns a copy of the list with the element at [index] moved one step toward the start. */
fun <T> List<T>.movedUp(index: Int): List<T> {
    if (index <= 0 || index >= size) return this
    return toMutableList().apply {
        val tmp = this[index - 1]
        this[index - 1] = this[index]
        this[index] = tmp
    }
}

/** Returns a copy of the list with the element at [index] moved one step toward the end. */
fun <T> List<T>.movedDown(index: Int): List<T> {
    if (index < 0 || index >= size - 1) return this
    return toMutableList().apply {
        val tmp = this[index + 1]
        this[index + 1] = this[index]
        this[index] = tmp
    }
}

/** Returns a copy of the list without the element at [index]. */
fun <T> List<T>.removedAt(index: Int): List<T> {
    if (index < 0 || index >= size) return this
    return toMutableList().apply { removeAt(index) }
}
