package com.riderai.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaFallbacksParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import java.time.Duration

/** Live ride data handed to the assistant with every question. */
data class RideContext(
    val speedKmh: Double,
    val speedLimitKmh: Int,
    val lat: Double?,
    val lon: Double?,
    val headingDeg: Float?,
    val leanDeg: Double,
    val recording: Boolean,
    val tripKm: Double,
    val rideMinutes: Long,
    val localTime: String,
)

/**
 * Conversational riding co-pilot backed by Claude.
 *
 * Answers are spoken through the helmet, so the system prompt asks for one or two short
 * sentences and the request uses low effort for quick replies. Blocking: call from a
 * background dispatcher.
 */
class RiderAssistant(
    apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val maxTurnsKept: Int = 8,
) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(Duration.ofSeconds(30))
        .build()

    private data class Turn(val user: String, val assistant: String)

    private val history = ArrayDeque<Turn>()

    fun clearHistory() = history.clear()

    fun ask(question: String, ride: RideContext): String {
        val userText = "${describe(ride)}\n\nRider says: $question"
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(1024L) // replies are deliberately short: they are read aloud
            .system(SYSTEM_PROMPT)
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
            // If a safety classifier declines on the primary model, the server retries on a
            // fallback model instead of returning a refusal.
            .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
            .fallbacks(BetaFallbacksParam.ofDefault())
        for (t in history) {
            builder.addUserMessage(t.user)
            builder.addAssistantMessage(t.assistant)
        }
        builder.addUserMessage(userText)

        val reply = try {
            val msg = client.beta().messages().create(builder.build())
            if (msg.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
                return "Sorry, I can't help with that one."
            }
            msg.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString(" ")
                .trim()
        } catch (e: UnauthorizedException) {
            return "My API key was rejected. Check it in settings."
        } catch (e: RateLimitException) {
            return "I'm being rate limited. Try again in a moment."
        } catch (e: AnthropicServiceException) {
            return "The assistant service had a problem. Try again shortly."
        } catch (e: AnthropicIoException) {
            return "I can't reach the assistant. Check your mobile data."
        }

        if (reply.isEmpty()) return "Sorry, I didn't get an answer."
        history.addLast(Turn(userText, reply))
        while (history.size > maxTurnsKept) history.removeFirst()
        return reply
    }

    private fun describe(r: RideContext): String = buildString {
        append("[Ride data] time ${r.localTime}; speed ${"%.0f".format(r.speedKmh)} km/h")
        if (r.speedLimitKmh > 0) append(" (rider's set limit ${r.speedLimitKmh})")
        append("; lean ${"%.0f".format(r.leanDeg)}°")
        if (r.lat != null && r.lon != null) append("; GPS ${"%.5f".format(r.lat)}, ${"%.5f".format(r.lon)}")
        r.headingDeg?.let { append("; heading ${"%.0f".format(it)}°") }
        append("; trip ${"%.1f".format(r.tripKm)} km in ${r.rideMinutes} min")
        append("; dashcam ${if (r.recording) "recording" else "off"}.")
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"

        private val SYSTEM_PROMPT = """
            You are Rider AI, a voice co-pilot for a motorcycle rider. Your replies are spoken
            through a helmet headset while the rider is moving, so:
            - Answer in one or two short, plain sentences. No lists, markdown, emoji or URLs.
            - Put safety first. If a question would need the rider to look at the screen or
              handle the phone, suggest they pull over first.
            - Each message starts with live ride data in brackets. Use it when it helps
              (speed, time, trip distance, coordinates), but do not read it back unprompted.
            - You cannot see the map or call anyone. If asked for directions or places, give
              the general direction or what to look for, and say you can't navigate.
            - If the rider sounds hurt, tell them to say "rider, SOS" to start the emergency
              countdown, or to call local emergency services.
        """.trimIndent()
    }
}
