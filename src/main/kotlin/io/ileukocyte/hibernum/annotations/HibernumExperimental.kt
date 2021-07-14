package io.ileukocyte.hibernum.annotations

import kotlin.annotation.AnnotationTarget.*

@Target(
    CLASS,
    TYPEALIAS,
    FUNCTION,
    PROPERTY,
    FIELD,
    CONSTRUCTOR,
    PROPERTY_GETTER,
    PROPERTY_SETTER
)
annotation class HibernumExperimental