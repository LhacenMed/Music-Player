package org.fossify.musicplayer.extensions

import kotlin.reflect.KClass

/**
 * Lazily set up a reflected field, or null if it is not there. Automatically handles visibility
 * changes.
 *
 * Every one of these looks a member up by name, which R8 is free to rename or strip outright in a
 * minified build. Callers must therefore treat a miss as "skip this refinement" rather than as an
 * error, so that a release build degrades instead of crashing.
 */
fun lazyReflectedFieldOrNull(clazz: KClass<*>, field: String) = lazy {
    runCatching { clazz.java.getDeclaredField(field).also { it.isAccessible = true } }.getOrNull()
}

/** Lazily set up a reflected method, or null if it is not there. @see lazyReflectedFieldOrNull */
fun lazyReflectedMethodOrNull(clazz: KClass<*>, method: String, vararg params: KClass<*>) = lazy {
    runCatching {
        clazz.java.getDeclaredMethod(method, *params.map { it.java }.toTypedArray()).also {
            it.isAccessible = true
        }
    }.getOrNull()
}
