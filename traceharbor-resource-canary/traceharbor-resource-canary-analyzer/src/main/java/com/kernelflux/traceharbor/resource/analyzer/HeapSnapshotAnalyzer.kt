package com.kernelflux.traceharbor.resource.analyzer

import com.kernelflux.traceharbor.resource.analyzer.model.AnalyzeResult
import com.kernelflux.traceharbor.resource.analyzer.model.HeapSnapshot

interface HeapSnapshotAnalyzer<T : AnalyzeResult> {
    fun analyze(heapSnapshot: HeapSnapshot): T
}

