@file:JvmName("CommandExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.commands.ContextCommand
import io.ileukocyte.hibernum.commands.GenericCommand
import io.ileukocyte.hibernum.commands.TextCommand

/**
 * @param isContextOnly
 * Whether the context name should be only returned in case
 * the command is not a text one (default is `true`)
 *
 * @return Either the command's context name if it is a context-menu command
 * or its generic name in any other cases
 */
fun GenericCommand.getEffectiveContextName(isContextOnly: Boolean = true) =
    if (this is ContextCommand) {
        if (isContextOnly) {
            if (this !is TextCommand) {
                contextName
            } else {
                name
            }
        } else {
            contextName
        }
    } else {
        name
    }