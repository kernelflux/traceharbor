package com.kernelflux.traceharbor.resource.analyzer.model

import java.io.Serializable

class Exclusion(
    builder: ExcludedRefs.ParamsBuilder,
) : Serializable {
    @JvmField val name: String? = builder.name
    @JvmField val reason: String? = builder.reason
    @JvmField val alwaysExclude: Boolean = builder.alwaysExclude
    @JvmField val matching: String = builder.matching
}

