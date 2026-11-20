package com.tobi.app.core.runtime

import com.tobi.app.core.protocol.CatalogPayload
import com.tobi.app.core.protocol.CatalogRequest
import com.tobi.app.core.protocol.ConnectorJson
import com.tobi.app.core.protocol.DeferredSourcePayload
import com.tobi.app.core.protocol.Fault
import com.tobi.app.core.protocol.FaultReasons
import com.tobi.app.core.protocol.FeedPayload
import com.tobi.app.core.protocol.MediaBundle
import com.tobi.app.core.protocol.MmcpEnvelope
import com.tobi.app.core.protocol.NetPolicy
import com.tobi.app.core.protocol.ProfilePayload
import com.tobi.app.core.protocol.RecoveryActions
import com.tobi.app.core.protocol.UnitsPayload
import com.tobi.app.core.protocol.normalizeFaultReason
import com.tobi.app.core.protocol.normalizeRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

sealed interface ConnectorError {
    data class FaultError(val fault: Fault) : ConnectorError
    data class ScriptError(val message: String) : ConnectorError
    data class ParseError(val message: String) : ConnectorError
    data object NotSupported : ConnectorError
}

sealed interface OpResult<out T> {
    data class Ok<T>(val envelope: MmcpEnvelope<T>) : OpResult<T>
    data class Err(val error: ConnectorError) : OpResult<Nothing>
}

class ConnectorRuntime(
    private val connectorId: String,
    private val scriptSource: String,
    private val manifest: com.tobi.app.core.protocol.ConnectorManifest,
    private val bridge: NativeBridge,
    private val challengeCoordinator: ChallengeCoordinator
) {
    private val mutex = Mutex()
    private var engine: JsEngine? = null

    suspend fun fetchFeed(page: Int, token: String?): OpResult<FeedPayload> {
        if (!manifest.features.feed) return OpResult.Err(ConnectorError.NotSupported)
        val args = buildJsonArray {
            add(JsonPrimitive(page))
            add(token?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        return call("fetchFeed", args, FeedPayload.serializer())
    }

    suspend fun queryCatalog(request: CatalogRequest): OpResult<CatalogPayload> {
        if (!manifest.features.catalogSearch) return OpResult.Err(ConnectorError.NotSupported)
        val args = buildJsonArray {
            add(ConnectorJson.encodeToJsonElement(CatalogRequest.serializer(), request))
        }
        return call("queryCatalog", args, CatalogPayload.serializer())
    }

    suspend fun fetchProfile(uid: String, carry: Map<String, String>, net: NetPolicy?): OpResult<ProfilePayload> {
        val args = buildJsonArray {
            add(JsonPrimitive(uid))
            add(carryObject(carry))
            add(net?.let { ConnectorJson.encodeToJsonElement(NetPolicy.serializer(), it) } ?: JsonNull)
        }
        return call("fetchProfile", args, ProfilePayload.serializer())
    }

    suspend fun fetchUnits(groupId: String, page: Int, token: String?, carry: Map<String, String>): OpResult<UnitsPayload> {
        val args = buildJsonArray {
            add(JsonPrimitive(groupId))
            add(JsonPrimitive(page))
            add(token?.let { JsonPrimitive(it) } ?: JsonNull)
            add(carryObject(carry))
        }
        return call("fetchUnits", args, UnitsPayload.serializer())
    }

    suspend fun resolveMedia(targetId: String, carry: Map<String, String>, net: NetPolicy?): OpResult<MediaBundle> {
        val args = buildJsonArray {
            add(JsonPrimitive(targetId))
            add(carryObject(carry))
            add(net?.let { ConnectorJson.encodeToJsonElement(NetPolicy.serializer(), it) } ?: JsonNull)
        }
        return call("resolveMedia", args, MediaBundle.serializer())
    }

    suspend fun resolveDeferredSource(sourceId: String, resolverCarry: Map<String, String>): OpResult<DeferredSourcePayload> {
        val args = buildJsonArray {
            add(JsonPrimitive(sourceId))
            add(carryObject(resolverCarry))
        }
        return call("resolveDeferredSource", args, DeferredSourcePayload.serializer())
    }

    private suspend fun <T> call(functionName: String, args: JsonArray, payloadSerializer: KSerializer<T>): OpResult<T> {
        return callInternal(functionName, args, payloadSerializer, retried = false)
    }

    private suspend fun <T> callInternal(functionName: String, args: JsonArray, payloadSerializer: KSerializer<T>, retried: Boolean): OpResult<T> {
        val json: String = try {
            withContext(Dispatchers.Default) {
                mutex.withLock {
                    val active = engine ?: JsEngine(bridge).also {
                        it.loadScript(scriptSource)
                        engine = it
                    }
                    val argsJson = ConnectorJson.encodeToString(JsonElement.serializer(), args)
                    active.invoke(functionName, argsJson)
                }
            }
        } catch (e: Exception) {
            RuntimeLog.log("error", "[$connectorId] $functionName script error: ${e.message}", connectorId)
            return OpResult.Err(ConnectorError.ScriptError(e.message ?: "script error"))
        }
        val envelope: MmcpEnvelope<T> = try {
            ConnectorJson.decodeFromString(MmcpEnvelope.serializer(payloadSerializer), json)
        } catch (e: Exception) {
            RuntimeLog.log("error", "[$connectorId] $functionName parse error: ${e.message}", connectorId)
            return OpResult.Err(ConnectorError.ParseError(e.message ?: "parse error"))
        }
        if (!envelope.ok && envelope.fault != null && !retried) {
            val reason = normalizeFaultReason(envelope.fault.reason)
            val recovery = normalizeRecovery(envelope.fault.recovery)
            if (reason == FaultReasons.CHALLENGE_GATE && recovery == RecoveryActions.SOLVE_IN_WEBVIEW) {
                val solved = challengeCoordinator.solve(envelope.fault)
                if (solved) {
                    return callInternal(functionName, args, payloadSerializer, retried = true)
                }
            }
        }
        return OpResult.Ok(envelope)
    }

    private fun carryObject(carry: Map<String, String>): JsonObject {
        return buildJsonObject {
            carry.forEach { (key, value) -> put(key, value) }
        }
    }
}
