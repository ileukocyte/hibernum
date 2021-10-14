package io.ileukocyte.hibernum.utils

import net.dv8tion.jda.api.interactions.commands.Command.Option
import net.dv8tion.jda.api.interactions.commands.build.OptionData

internal fun Option.toOptionData() = OptionData(type, name, description, isRequired)
    .let { if (type.canSupportChoices()) it.addChoices(choices) else it }

internal fun List<OptionData>.isEqualTo(another: List<OptionData>): Boolean {
    fun OptionData.isEqualTo(anotherData: OptionData) =
        name == anotherData.name
                && description == anotherData.description
                && type == anotherData.type
                && isRequired == anotherData.isRequired
                && choices == anotherData.choices

    if (size != another.size)
        return false

    for (i in indices) {
        if (!this[i].isEqualTo(another[i])) {
            return false
        }
    }

    return true
}