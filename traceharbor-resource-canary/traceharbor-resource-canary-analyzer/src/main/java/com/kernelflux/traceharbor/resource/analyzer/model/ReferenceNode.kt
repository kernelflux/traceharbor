package com.kernelflux.traceharbor.resource.analyzer.model

import com.squareup.haha.perflib.Instance

class ReferenceNode(
    @JvmField val exclusion: Exclusion?,
    @JvmField val instance: Instance?,
    @JvmField val parent: ReferenceNode?,
    @JvmField val referenceName: String?,
    @JvmField val referenceType: ReferenceTraceElement.Type?,
)

