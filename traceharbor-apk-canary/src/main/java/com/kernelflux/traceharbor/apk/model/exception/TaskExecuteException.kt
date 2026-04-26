package com.kernelflux.traceharbor.apk.model.exception

class TaskExecuteException : RuntimeException {
    constructor(message: String) : super(message)

    constructor(message: String, throwable: Throwable) : super(message, throwable)
}

