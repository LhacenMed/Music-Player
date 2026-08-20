package org.fossify.musicplayer.extensions

fun <T> MutableList<T>.sortSafely(comparator: Comparator<T>) {
    try {
        sortWith(comparator)
    } catch (ignored: Exception) {
    }
}

fun <T> MutableList<T>.move(currentIndex: Int, newIndex: Int) {
    val itemToMove = removeAt(currentIndex)
    if (currentIndex > newIndex) {
        add(newIndex, itemToMove)
    } else {
        add(newIndex - 1, itemToMove)
    }
}

inline fun <T> Collection<T>.indexOfFirstOrNull(predicate: (T) -> Boolean): Int? {
    for ((index, item) in this.withIndex()) {
        if (predicate(item))
            return index
    }
    return null
}
