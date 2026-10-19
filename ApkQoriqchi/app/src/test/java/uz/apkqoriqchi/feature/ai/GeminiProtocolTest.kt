package uz.apkqoriqchi.feature.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Test
import uz.apkqoriqchi.core.model.ProviderResult
import uz.apkqoriqchi.core.model.RiskLevel

class GeminiProtocolTest {

    private val validAnalysis = """
        {"risk_level":"high","confidence":"medium","verdict":"Bir nechta antivirus troyan aniqladi.",
         "confirmed_detections":["5/70 engines: Trojan.AndroidOS.Agent"],
         "suspicious_indicators":["READ_SMS permission"],
         "benign_or_explanatory_findings":[],
         "engine_disagreements":["Only 5 of 70 engines flag it"],
         "evidence":["VirusTotal 5/70"],
         "limitations":["No behavioural analysis"],
         "recommended_actions":["Do not install","Delete the file","Install from Google Play"],
         "localized_summary":"Fayl xavfli bo'lishi mumkin."}
    """.trimIndent()

    private fun response(text: String, finish: String = "STOP", withThought: Boolean = true) = buildJsonObject {
        put("candidates", buildJsonArray {
            add(buildJsonObject {
                putJsonObject("content") {
                    put("role", "model")
                    putJsonArray("parts") {
                        if (withThought) add(buildJsonObject { put("text", "thinking summary"); put("thought", true) })
                        add(buildJsonObject { put("text", text); put("thoughtSignature", "abc") })
                    }
                }
                put("finishReason", finish)
            })
        })
    }.toString()

    @Test fun `request has schema, system instruction and wrapped facts`() {
        val req = GeminiProtocol.buildRequest("Verdict: HIGH", AiLanguage.RUSSIAN)
        val cfg = req["generationConfig"]!!.jsonObject
        assertThat(cfg["responseMimeType"]!!.jsonPrimitive.content).isEqualTo("application/json")
        val schema = cfg["responseSchema"]!!.jsonObject
        assertThat(schema["type"]!!.jsonPrimitive.content).isEqualTo("OBJECT")
        assertThat(schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsAtLeast("risk_level", "confidence", "verdict", "recommended_actions", "localized_summary")
        val riskEnum = schema["properties"]!!.jsonObject["risk_level"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertThat(riskEnum).containsExactly("no_detections", "low", "medium", "high", "critical", "unknown")
        val system = req["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
        assertThat(system).contains("Russian")
        assertThat(system).contains("UNTRUSTED")
        val msg = req["contents"]!!.jsonArray.single().jsonObject
        assertThat(msg["role"]!!.jsonPrimitive.content).isEqualTo("user")
        assertThat(msg["parts"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content).contains("<evidence>\nVerdict: HIGH")
        assertThat(Json.parseToJsonElement(req.toString())).isInstanceOf(JsonObject::class.java)
    }

    @Test fun `key goes in header, never in URL`() {
        val r = GeminiProtocol.httpRequest("SECRET", buildJsonObject {})
        assertThat(r.url.toString()).doesNotContain("SECRET")
        assertThat(r.header("x-goog-api-key")).isEqualTo("SECRET")
        assertThat(r.url.toString()).endsWith("/models/gemini-2.5-flash:generateContent")
    }

    @Test fun `valid structured output parses and thought parts are skipped`() {
        val r = GeminiProtocol.parseResponse(response(validAnalysis)) as ProviderResult.Success
        assertThat(r.data.riskLevel.level).isEqualTo(RiskLevel.HIGH)
        assertThat(r.data.recommendedActions).hasSize(3)
    }

    @Test fun `schema violations are rejected`() {
        assertThat(GeminiProtocol.parseAnalysis(validAnalysis.replace("\"limitations\":[\"No behavioural analysis\"],", ""))).isNull()
        assertThat(GeminiProtocol.parseAnalysis(validAnalysis.replace("\"high\"", "\"safe\""))).isNull()
        assertThat(GeminiProtocol.parseAnalysis(validAnalysis.replace("\"medium\"", "\"certain\""))).isNull()
        assertThat(GeminiProtocol.parseAnalysis(validAnalysis.replace(
            "[\"Do not install\",\"Delete the file\",\"Install from Google Play\"]", "[]"))).isNull()
        assertThat(GeminiProtocol.parseAnalysis("The file is safe.")).isNull()
    }

    @Test fun `blocked, safety-stopped and truncated answers are errors`() {
        val blocked = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        assertThat((GeminiProtocol.parseResponse(blocked) as ProviderResult.Failed).reason).isEqualTo("refusal")
        assertThat((GeminiProtocol.parseResponse(response("", finish = "SAFETY")) as ProviderResult.Failed).reason).isEqualTo("refusal")
        assertThat(GeminiProtocol.parseResponse(response(validAnalysis.take(50), finish = "MAX_TOKENS")))
            .isInstanceOf(ProviderResult.Failed::class.java)
        assertThat(GeminiProtocol.parseResponse("not json")).isInstanceOf(ProviderResult.Failed::class.java)
    }

    @Test fun `http errors map to reasons`() {
        val badKey = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        assertThat(GeminiProtocol.parseError(400, badKey).reason).isEqualTo("auth")
        assertThat(GeminiProtocol.parseError(403, "{}").reason).isEqualTo("auth")
        assertThat(GeminiProtocol.parseError(429, "{}").retryable).isTrue()
        assertThat(GeminiProtocol.parseError(503, "{}").reason).isEqualTo("overloaded")
    }

    @Test fun `language follows app locale`() {
        assertThat(GeminiExplainer.languageFor("ru")).isEqualTo(AiLanguage.RUSSIAN)
        assertThat(GeminiExplainer.languageFor("en")).isEqualTo(AiLanguage.ENGLISH)
        assertThat(GeminiExplainer.languageFor("uz")).isEqualTo(AiLanguage.UZBEK)
        assertThat(GeminiExplainer.languageFor("de")).isEqualTo(AiLanguage.UZBEK)
    }
}
