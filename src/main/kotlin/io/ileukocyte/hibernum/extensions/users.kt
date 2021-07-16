@file:JvmName("UserExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.Immutable

import net.dv8tion.jda.api.entities.User

val User.isDeveloper: Boolean get() = idLong in Immutable.DEVELOPERS