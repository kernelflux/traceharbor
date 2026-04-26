package com.kernelflux.traceharbor.apk.model.exception

class TaskInitException : RuntimeException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}

