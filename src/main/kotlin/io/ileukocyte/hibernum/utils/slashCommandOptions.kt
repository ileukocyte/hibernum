package io.ileukocyte.hibernum.utils

import net.dv8tion.jda.api.interactions.commands.Command.Option
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

internal fun Option.toOptionData() = OptionData(type, name, description, isRequired)
    .let { if (type == OptionType.CHANNEL) it.setChannelTypes(channelTypes) else it }
    .let { if (type.canSupportChoices()) it.addChoices(choices) else it }
    .let {
        if (type == OptionType.NUMBER || type == OptionType.INTEGER) {
            minValue?.let { min -> it.setMinValue(min) }
            maxValue?.let { min -> it.setMaxValue(min) }
        }

        it
    }

internal fun List<OptionData>.isEqualTo(another: List<OptionData>): Boolean {
    fun OptionData.isEqualTo(anotherData: OptionData) =
        name == anotherData.name
                && description == anotherData.description
                && type == anotherData.type
                && isRequired == anotherData.isRequired
                && choices == anotherData.choices
                && minValue == anotherData.minValue
                && maxValue == anotherData.maxValue
                && channelTypes == anotherData.channelTypes

    if (size != another.size)
        return false

    for (i in indices) {
        if (!this[i].isEqualTo(another[i])) {
            return false
        }
    }

    return true
}

private fun OptionData.setMinValue(min: Number) = when (min) {
    is Double -> setMinValue(min)
    is Long -> setMinValue(min)
    else -> throw UnsupportedOperationException()
}

private fun OptionData.setMaxValue(max: Number) = when (max) {
    is Double -> setMaxValue(max)
    is Long -> setMaxValue(max)
    else -> throw UnsupportedOperationException()
}