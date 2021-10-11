package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.openweather.Forecast
import io.ileukocyte.openweather.Units
import io.ileukocyte.openweather.entities.Temperature.TemperatureUnit
import io.ileukocyte.openweather.extensions.convertUnitsTo
import io.ileukocyte.openweather.openWeatherApi

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import java.text.DecimalFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class WeatherCommand : Command {
    override val name = "weather"
    override val description = "Sends the weather in the specified location"
    override val cooldown = 3L
    override val usages = setOf(setOf("location"))
    override val options = setOf(
        OptionData(OptionType.STRING, "location", "A location to get the weather for", true)
    )

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val api = openWeatherApi(Immutable.WEATHER_API_KEY, Units.METRIC)

        val forecast = api.fromNameOrNull(args ?: throw NoArgumentsException)
            ?: throw CommandException("No location has been found by the query!")

        event.channel.sendMessageEmbeds(weatherEmbed(event.jda, forecast)).queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val api = openWeatherApi(Immutable.WEATHER_API_KEY, Units.METRIC)

        val forecast = api.fromNameOrNull(event.getOption("location")?.asString ?: return)
            ?: throw CommandException("No location has been found by the query!")

        event.replyEmbeds(weatherEmbed(event.jda, forecast)).queue()
    }

    private fun weatherEmbed(jda: JDA, forecast: Forecast) = buildEmbed {
        val location = forecast.location.let { "${it.name}, ${it.countryCode}" }
        val link = "https://openweathermap.org/city/${forecast.location.id}"

        color = Immutable.SUCCESS

        field {
            title = "Condition"
            description = forecast.weather.main
            isInline = true
        }

        field {
            val celsius = forecast.temperature.temperature.toInt()
            val fahrenheit = forecast.temperature.convertUnitsTo(TemperatureUnit.FAHRENHEIT_DEGREES).temperature.toInt()

            title = "Temperature"
            description = "$celsius${TemperatureUnit.CELSIUS_DEGREES.symbol}/$fahrenheit${TemperatureUnit.FAHRENHEIT_DEGREES.symbol}"
            isInline = true
        }

        field {
            title = "Wind"
            description = forecast.wind.let { "${it.speed.toInt()} ${it.unit.asString}" + it.directionName?.let { dn -> ", $dn" } }
            isInline = true
        }

        field {
            title = "Humidity"
            description = forecast.humidity.humidity?.let { "$it%" } ?: "N/A"
            isInline = true
        }

        field {
            title = "Cloudiness"
            description = "${forecast.cloudiness.cloudiness}%"
            isInline = true
        }

        field {
            title = "Pressure"
            description = "${DecimalFormat("#,###").format(forecast.pressure.pressure.toInt())} mbar"
            isInline = true
        }

        field {
            title = "Sunrise"
            description = OffsetDateTime.ofInstant(forecast.time.sunrise.toInstant(), forecast.time.timeZone.toZoneId())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            isInline = true
        }

        field {
            title = "Sunset"
            description = OffsetDateTime.ofInstant(forecast.time.sunset.toInstant(), forecast.time.timeZone.toZoneId())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            isInline = true
        }

        field {
            title = "Current Date"
            description = OffsetDateTime.now(forecast.time.timeZone.toZoneId())
                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)) +
                    " (${forecast.time.timeZone.displayName})"
        }

        author {
            name = "Weather \u2022 $location"
            url = link
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        footer { text = "Provided by OpenWeather" }
    }
}