package com.amazon.kitchen.customers

public data class Customer(
    val firstName: com.amazon.kitchen.customers.Customer.FirstName,
    val middleName: com.amazon.kitchen.customers.Customer.MiddleName?,
    val lastName: com.amazon.kitchen.customers.Customer.LastName,
) {
    init {
        firstName?.let {
            val regex = kotlin.text.Regex("[-A-Za-z ]+", setOf( ))
            require(regex.matches(it)) { "Value '$fieldName' does not match regular expression: [-A-Za-z ]+" }
        }
        middleName?.let {
            val regex = kotlin.text.Regex("[-A-Za-z ]+", setOf( ))
            require(regex.matches(it)) { "Value '$fieldName' does not match regular expression: [-A-Za-z ]+" }
        }
        lastName?.let {
            val regex = kotlin.text.Regex("[-A-Za-z ]+", setOf( ))
            require(regex.matches(it)) { "Value '$fieldName' does not match regular expression: [-A-Za-z ]+" }
        }
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
        } finally {
            ionWriter.stepOut()
        }
    }
    class Builder {
    var firstName: com.amazon.kitchen.customers.Customer.FirstName? = null
    fun withFirstName(firstName: com.amazon.kitchen.customers.Customer.FirstName) = apply {
        this.firstName = firstName
    }
    var middleName: com.amazon.kitchen.customers.Customer.MiddleName? = null
    fun withMiddleName(middleName: com.amazon.kitchen.customers.Customer.MiddleName?) = apply {
        this.middleName = middleName
    }
    var lastName: com.amazon.kitchen.customers.Customer.LastName? = null
    fun withLastName(lastName: com.amazon.kitchen.customers.Customer.LastName) = apply {
        this.lastName = lastName
    }
    fun build() = Customer(
    firstName = this.firstName ?: throw IllegalArgumentException("firstName cannot be null"),
    middleName = this.middleName,
    lastName = this.lastName ?: throw IllegalArgumentException("lastName cannot be null"),
    )
}
}
