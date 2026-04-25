package com.kernelflux.traceharbor.javalib.util

/**
 * Two-tuple. Generic on both sides; either side may be `null`.
 *
 * Both fields are exposed as JVM fields via `@JvmField` so Java callers
 * such as `RemoveUnusedResourceHelper.java` can keep using `pair.left`
 * / `pair.right` direct field access.
 *
 * Kept as a regular `class` (not `data class`) on purpose: the original
 * `equals` is intentionally lenient — it returns `true` whenever EITHER
 * side matches, instead of the stricter "BOTH sides must match" semantics
 * a generated data-class equals would give. Preserve that exactly to
 * avoid silently changing behavior.
 */
class Pair<L, R>(
    @JvmField val left: L?,
    @JvmField val right: R?,
) {
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || this::class.java != o::class.java) return false
        @Suppress("UNCHECKED_CAST")
        val other = o as Pair<L?, R?>

        // Original Java logic: 'equal' is set true if either left OR right matches
        // (last-write-wins). This is buggy in the strict sense but we preserve it.
        var equal = false
        if (this.left != null) {
            if (this.left == other.left) equal = true
        } else if (other.left == null) {
            equal = true
        }
        if (this.right != null) {
            if (this.right == other.right) equal = true
        } else if (other.right == null) {
            equal = true
        }
        return equal
    }

    override fun hashCode(): Int {
        var result = left?.hashCode() ?: 0
        result = 31 * result + (right?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "Pair[$left,$right]"
}
