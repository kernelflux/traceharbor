package com.kernelflux.traceharbor.resource.analyzer.model

import java.util.Collections.unmodifiableSet
import java.util.HashSet
import java.util.regex.Pattern

class ExcludedBmps(
    builder: BuilderWithParams,
) : ExcludedRefs(builder) {
    @JvmField val mClassNamePatterns: Set<PatternInfo> = unmodifiableSet(builder.mClassNamePatterns)

    class PatternInfo(
        @JvmField val mPattern: Pattern,
        @JvmField val mForGCRootOnly: Boolean,
    )

    interface Builder : ExcludedRefs.Builder {
        fun addClassNamePattern(regex: String, forGCRootOnly: Boolean): Builder

        override fun build(): ExcludedBmps
    }

    class BuilderWithParams : ExcludedRefs.BuilderWithParams(), Builder {
        val mClassNamePatterns: MutableSet<PatternInfo> = HashSet()

        override fun addClassNamePattern(regex: String, forGCRootOnly: Boolean): Builder {
            if (regex.isEmpty()) {
                throw IllegalArgumentException("bad regex: $regex")
            }
            mClassNamePatterns.add(PatternInfo(Pattern.compile(regex), forGCRootOnly))
            return this
        }

        override fun build(): ExcludedBmps = ExcludedBmps(this)
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = BuilderWithParams()
    }
}

