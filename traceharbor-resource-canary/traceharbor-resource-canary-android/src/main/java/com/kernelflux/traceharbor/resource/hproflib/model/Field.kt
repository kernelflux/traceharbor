package com.kernelflux.traceharbor.resource.hproflib.model

class Field(
    @JvmField val typeId: Int,
    @JvmField val nameId: ID,
    @JvmField val staticValue: Any?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Field) {
            return false
        }
        if (typeId != other.typeId) {
            return false
        }
        if (nameId != other.nameId) {
            return false
        }
        return (staticValue == null || staticValue == other.staticValue) &&
            (other.staticValue == null || other.staticValue == staticValue)
    }

    override fun hashCode(): Int {
        return (nameId.hashCode() shl 31) + typeId
    }
}

