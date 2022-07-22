@file:JvmName("SlashCommandExtensions")
package io.ileukocyte.hibernum.extensions

import net.dv8tion.jda.api.interactions.commands.Command.Option
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

fun Option.toOptionData() = OptionData(type, name, description, isRequired)
    .setAutoComplete(isAutoComplete)
    .applyIf(type == OptionType.CHANNEL) { setChannelTypes(this@toOptionData.channelTypes) }
    .applyIf(type.canSupportChoices()) { addChoices(this@toOptionData.choices) }
    .let {
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

fun Subcommand.toSubcommandData() = SubcommandData(name, description)
    .addOptions(options.map { it.toOptionData() })

fun List<SubcommandData>.subcommandsEqual(another: List<SubcommandData>): Boolean {
    fun SubcommandData.isEqualTo(another: SubcommandData) = name == another.name
            && description == another.description
            && options.optionsEqual(another.options)

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

fun List<OptionData>.optionsEqual(another: List<OptionData>): Boolean {
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