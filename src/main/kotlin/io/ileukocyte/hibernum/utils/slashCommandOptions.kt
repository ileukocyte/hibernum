package io.ileukocyte.hibernum.utils

import net.dv8tion.jda.api.interactions.commands.Command.Option
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

fun Option.toOptionData() = OptionData(type, name, description, isRequired)
    .let {
        if (type == OptionType.CHANNEL) {
            it.setChannelTypes(channelTypes)
        } else {
            it
        }
    }.let {
         if (type.canSupportChoices()) {
             it.addChoices(choices)
         } else {
             it
         }
    }.let {
        when (type) {
            OptionType.NUMBER -> {
                minValue?.toDouble()?.let { min -> it.setMinValue(min) }
                maxValue?.toDouble()?.let { max -> it.setMaxValue(max) }
            }
            OptionType.INTEGER -> {
                minValue?.toLong()?.let { min -> it.setMinValue(min) }
                maxValue?.toLong()?.let { max -> it.setMaxValue(max) }
            }
            else -> {}
        }

        it
    }

fun List<OptionData>.isEqualTo(another: List<OptionData>): Boolean {
    fun OptionData.isEqualTo(anotherData: OptionData) =
        name == anotherData.name
                && description == anotherData.description
                && type == anotherData.type
                && isRequired == anotherData.isRequired
                && choices == anotherData.choices
                && minValue == anotherData.minValue
                && maxValue == anotherData.maxValue
                && channelTypes == anotherData.channelTypes
                && isAutoComplete == anotherData.isAutoComplete
                && nameLocalizations == anotherData.nameLocalizations
                && descriptionLocalizations == anotherData.nameLocalizations

    if (size != another.size) {
        return false
    }

    for (i in indices) {
        if (!this[i].isEqualTo(another[i])) {
            return false
        }
    }

    return true
}