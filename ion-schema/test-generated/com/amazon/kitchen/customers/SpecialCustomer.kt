package com.amazon.kitchen.customers

public data class SpecialCustomer(
    val firstName: kotlin.String,
    val middleName: kotlin.String?,
    val lastName: kotlin.String,
    val specialData: com.amazon.ion.IonValue?,
) {
    init {
    }
    fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
        ionWriter.stepIn(com.amazon.ion.IonType.STRUCT)
        try {
            ionWriter.setFieldName("firstName")
if (firstName == null) {
    ionWriter.writeNull()
} else {
    // TODO: handle primitives
    firstName.writeTo(ionWriter)
}
ionWriter.setFieldName("middleName")
if (middleName == null) {
    ionWriter.writeNull()
} else {
    // TODO: handle primitives
    middleName.writeTo(ionWriter)
}
ionWriter.setFieldName("lastName")
if (lastName == null) {
    ionWriter.writeNull()
} else {
    // TODO: handle primitives
    lastName.writeTo(ionWriter)
}
ionWriter.setFieldName("specialData")
if (specialData == null) {
    ionWriter.writeNull()
} else {
    // TODO: handle primitives
    specialData.writeTo(ionWriter)
}
        } finally {
            ionWriter.stepOut()
        }
    }
    class Builder {
    var firstName: kotlin.String? = null
    fun withFirstName(firstName: kotlin.String) = apply {
        this.firstName = firstName
    }
    var middleName: kotlin.String? = null
    fun withMiddleName(middleName: kotlin.String?) = apply {
        this.middleName = middleName
    }
    var lastName: kotlin.String? = null
    fun withLastName(lastName: kotlin.String) = apply {
        this.lastName = lastName
    }
    var specialData: com.amazon.ion.IonValue? = null
    fun withSpecialData(specialData: com.amazon.ion.IonValue?) = apply {
        this.specialData = specialData
    }
    fun build() = SpecialCustomer(
    firstName = this.firstName ?: throw IllegalArgumentException("firstName cannot be null"),
    middleName = this.middleName,
    lastName = this.lastName ?: throw IllegalArgumentException("lastName cannot be null"),
    specialData = this.specialData,
    )
}
}
