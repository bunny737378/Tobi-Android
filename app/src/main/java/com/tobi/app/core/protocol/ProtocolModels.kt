package com.tobi.app.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val ConnectorJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

@Serializable
data class MmcpEnvelope<T>(
    val ok: Boolean = false,
    val payload: T? = null,
    val paging: Paging? = null,
    val fault: Fault? = null
)

@Serializable
data class Paging(
    val current: Int = 1,
    val hasMore: Boolean = false,
    val token: String? = null,
    val totalItems: Int? = null
)

@Serializable
data class Fault(
    val reason: String = "UNKNOWN",
    val summary: String? = null,
    val recovery: String = "notify_only",
    val targetUri: String? = null,
    val retryAfterSec: Int = 0
)

@Serializable
data class NetPolicy(
    val headers: Map<String, String> = emptyMap(),
    val cookieScope: String? = null,
    val attachCookies: Boolean = true,
    val lockUserAgent: Boolean = false,
    val expiresAtSec: Long? = null
)

@Serializable
data class Visuals(
    val cover: String? = null,
    val banner: String? = null,
    val wordmark: String? = null,
    val thumb: String? = null,
    val net: NetPolicy? = null
)

@Serializable
data class EntryCard(
    val uid: String = "",
    val name: String = "",
    val kind: String? = null,
    val visuals: Visuals? = null,
    val badges: List<String> = emptyList(),
    val score: Double? = null,
    val releaseYear: Int? = null,
    val liveNow: Boolean = false,
    val carry: Map<String, String> = emptyMap()
)

@Serializable
data class Shortcut(
    val shelfId: String = "",
    val label: String = "",
    val carry: Map<String, String> = emptyMap()
)

@Serializable
data class Shelf(
    val shelfId: String = "",
    val heading: String = "",
    val cardStyle: String = "portrait",
    val expandable: Boolean = false,
    val carry: Map<String, String> = emptyMap(),
    val entries: List<EntryCard> = emptyList()
)

@Serializable
data class FeedPayload(
    val spotlight: List<EntryCard> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
    val shelves: List<Shelf> = emptyList()
)

@Serializable
data class FacetChoice(
    val value: String = "",
    val label: String = ""
)

@Serializable
data class Facet(
    val facetId: String = "",
    val label: String = "",
    val selection: String = "single",
    val choices: List<FacetChoice> = emptyList()
)

@Serializable
data class CatalogRequest(
    val keyword: String? = null,
    val shelfId: String? = null,
    val filters: Map<String, List<String>> = emptyMap(),
    val page: Int = 1,
    val token: String? = null,
    val carry: Map<String, String> = emptyMap()
)

@Serializable
data class CatalogPayload(
    val entries: List<EntryCard> = emptyList(),
    val facets: List<Facet> = emptyList()
)

@Serializable
data class ProfileFacts(
    val releaseYear: Int? = null,
    val releaseDate: String? = null,
    val state: String? = null,
    val maturity: String? = null,
    val score: Double? = null,
    val scoreVotes: Int? = null,
    val runtimeMin: Int? = null,
    val country: String? = null,
    val creator: String? = null,
    val languages: List<String> = emptyList()
)

@Serializable
data class Credit(
    val person: String = "",
    val character: String? = null,
    val photo: String? = null
)

@Serializable
data class PlayTarget(
    val targetId: String = "",
    val carry: Map<String, String> = emptyMap(),
    val net: NetPolicy? = null
)

@Serializable
data class LiveSchedule(
    val currentProgram: String? = null,
    val startsAtIso: String? = null,
    val endsAtIso: String? = null,
    val nextProgram: String? = null
)

@Serializable
data class UnitFlags(
    val upcoming: Boolean = false,
    val filler: Boolean = false,
    val recap: Boolean = false,
    val special: Boolean = false
)

@Serializable
data class UnitItem(
    val unitId: String = "",
    val index: Int? = null,
    val absoluteIndex: Int? = null,
    val name: String? = null,
    val visuals: Visuals? = null,
    val synopsis: String? = null,
    val runtimeMin: Int? = null,
    val airDate: String? = null,
    val flags: UnitFlags? = null,
    val badges: List<String> = emptyList(),
    val carry: Map<String, String> = emptyMap(),
    val net: NetPolicy? = null
)

@Serializable
data class MediaGroup(
    val groupId: String = "",
    val index: Int? = null,
    val label: String? = null,
    val unitCount: Int? = null,
    val deferredUnits: Boolean = false,
    val carry: Map<String, String> = emptyMap(),
    val units: List<UnitItem> = emptyList()
)

@Serializable
data class Relation(
    val relationship: String? = null,
    val entry: EntryCard = EntryCard()
)

@Serializable
data class ProfilePayload(
    val uid: String = "",
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val kind: String? = null,
    val availability: String? = null,
    val visuals: Visuals? = null,
    val tagline: String? = null,
    val synopsis: String? = null,
    val facts: ProfileFacts? = null,
    val genres: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
    val previewVideoUri: String? = null,
    val crossRefs: Map<String, String> = emptyMap(),
    val credits: List<Credit> = emptyList(),
    val directTarget: PlayTarget? = null,
    val liveSchedule: LiveSchedule? = null,
    val groups: List<MediaGroup> = emptyList(),
    val relations: List<Relation> = emptyList(),
    val similar: List<EntryCard> = emptyList()
)

@Serializable
data class UnitsPayload(
    val groupId: String = "",
    val units: List<UnitItem> = emptyList()
)

@Serializable
data class VideoInfo(
    val height: Int? = null,
    val codec: String? = null,
    val hdr: String? = null,
    val fps: Int? = null,
    val bitrateKbps: Int? = null
)

@Serializable
data class SidecarAudio(
    val uri: String = "",
    val langCode: String? = null,
    val label: String? = null,
    val codec: String? = null,
    val defaultTrack: Boolean = false,
    val net: NetPolicy? = null
)

@Serializable
data class CaptionTrack(
    val uri: String = "",
    val langCode: String? = null,
    val label: String? = null,
    val syntax: String = "vtt",
    val defaultTrack: Boolean = false,
    val forced: Boolean = false,
    val net: NetPolicy? = null
)

@Serializable
data class Encryption(
    val scheme: String? = null,
    val licenseUri: String? = null,
    val licenseHeaders: Map<String, String> = emptyMap(),
    val clearKeys: Map<String, String>? = null
)

@Serializable
data class StreamSource(
    val sourceId: String = "",
    val hostName: String? = null,
    val displayLabel: String? = null,
    val resolutionState: String = "ready",
    val uri: String? = null,
    val container: String = "auto",
    val qualityTag: String? = null,
    val video: VideoInfo? = null,
    val audioTag: String? = null,
    val variant: String? = null,
    val priority: Int = 0,
    val canDirectSave: Boolean = false,
    val net: NetPolicy? = null,
    val sidecarAudio: List<SidecarAudio> = emptyList(),
    val captions: List<CaptionTrack> = emptyList(),
    val encryption: Encryption? = null,
    val decoderHint: String = "auto",
    val resolverCarry: Map<String, String> = emptyMap()
)

@Serializable
data class Acquisition(
    val acqId: String = "",
    val hostName: String? = null,
    val displayLabel: String? = null,
    val fileName: String? = null,
    val uri: String? = null,
    val delivery: String = "managed_http",
    val qualityTag: String? = null,
    val format: String? = null,
    val sizeBytes: Long? = null,
    val isBatchPack: Boolean = false,
    val net: NetPolicy? = null
)

@Serializable
data class Marker(
    val kind: String = "",
    val startMs: Long = 0,
    val endMs: Long = 0
)

@Serializable
data class Chapter(
    val label: String = "",
    val startMs: Long = 0
)

@Serializable
data class Storyboard(
    val vttUri: String = "",
    val net: NetPolicy? = null
)

@Serializable
data class Timeline(
    val markers: List<Marker> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val storyboard: Storyboard? = null
)

@Serializable
data class PlaybackHints(
    val isLive: Boolean = false,
    val dvrWindowSec: Int? = null,
    val resumeOffsetMs: Long? = null
)

@Serializable
data class MediaBundle(
    val sources: List<StreamSource> = emptyList(),
    val acquisitions: List<Acquisition> = emptyList(),
    val sharedCaptions: List<CaptionTrack> = emptyList(),
    val timeline: Timeline? = null,
    val playbackHints: PlaybackHints? = null
)

@Serializable
data class DeferredSourcePayload(
    val source: StreamSource = StreamSource()
)

@Serializable
data class ConnectorFeatures(
    val feed: Boolean = false,
    val catalogSearch: Boolean = false,
    val dynamicFilters: Boolean = false,
    val deferredSources: Boolean = false,
    val downloads: Boolean = false,
    val needsWebViewBridge: Boolean = false
)

@Serializable
data class UserConfigField(
    val key: String = "",
    val label: String = "",
    val inputType: String = "text",
    val required: Boolean = false,
    val defaultValue: String? = null
)

@Serializable
data class ConnectorManifest(
    val connectorId: String = "",
    val displayName: String = "",
    val version: String = "1.0.0",
    val protocolVersion: Int = 1,
    val maintainer: String? = null,
    val summary: String? = null,
    val iconUri: String? = null,
    val originUrl: String? = null,
    val locales: List<String> = emptyList(),
    val matureContent: Boolean = false,
    val features: ConnectorFeatures = ConnectorFeatures(),
    val defaultNet: NetPolicy? = null,
    val userConfigSchema: List<UserConfigField> = emptyList()
)

@Serializable
data class RepositoryEntry(
    val connectorId: String = "",
    val displayName: String = "",
    val jsUrl: String = "",
    val manifestUrl: String? = null,
    val iconUri: String? = null,
    val version: String? = null
)

object FaultReasons {
    const val UNREACHABLE = "UNREACHABLE"
    const val CHALLENGE_GATE = "CHALLENGE_GATE"
    const val REGION_LOCKED = "REGION_LOCKED"
    const val CREDENTIALS_NEEDED = "CREDENTIALS_NEEDED"
    const val EXPIRED_ROUTE = "EXPIRED_ROUTE"
    const val NOTHING_RESOLVABLE = "NOTHING_RESOLVABLE"
    const val SCHEMA_DRIFT = "SCHEMA_DRIFT"
    const val UNKNOWN = "UNKNOWN"
}

object RecoveryActions {
    const val RETRY = "retry"
    const val SOLVE_IN_WEBVIEW = "solve_in_webview"
    const val NOTIFY_ONLY = "notify_only"
    const val OPEN_CONNECTOR_SETTINGS = "open_connector_settings"
    const val SWITCH_CONNECTOR = "switch_connector"
}

object CardStyles {
    const val PORTRAIT = "portrait"
    const val WIDE = "wide"
    const val SQUARE = "square"
    const val GRID = "grid"
    const val UNKNOWN = "portrait"
}

object ContainerKinds {
    const val HLS = "hls"
    const val DASH = "dash"
    const val MP4 = "mp4"
    const val MKV = "mkv"
    const val WEBM = "webm"
    const val RTMP = "rtmp"
    const val RTSP = "rtsp"
    const val TS = "ts"
    const val WEBVIEW_EMBED = "webview_embed"
    const val AUTO = "auto"
}

object AvailabilityKinds {
    const val STREAM_AND_DOWNLOAD = "stream_and_download"
    const val STREAM_ONLY = "stream_only"
    const val DOWNLOAD_ONLY = "download_only"
    const val UNKNOWN = "stream_and_download"
}

object ResolutionStates {
    const val READY = "ready"
    const val DEFERRED = "deferred"
    const val UNKNOWN = "ready"
}

object DeliveryKinds {
    const val MANAGED_HTTP = "managed_http"
    const val BROWSER_HANDOFF = "browser_handoff"
    const val MAGNET = "magnet"
    const val UNKNOWN = "managed_http"
}

object DecoderHints {
    const val AUTO = "auto"
    const val HW = "hw"
    const val SW = "sw"
}

object CaptionSyntax {
    const val VTT = "vtt"
    const val SRT = "srt"
    const val ASS = "ass"
    const val SSA = "ssa"
    const val TTML = "ttml"
}

object MarkerKinds {
    const val OPENING = "opening"
    const val RECAP = "recap"
    const val ENDING = "ending"
    const val INTRO = "intro"
    const val OUTRO = "outro"
}

fun normalizeFaultReason(value: String?): String = when (value) {
    FaultReasons.UNREACHABLE -> FaultReasons.UNREACHABLE
    FaultReasons.CHALLENGE_GATE -> FaultReasons.CHALLENGE_GATE
    FaultReasons.REGION_LOCKED -> FaultReasons.REGION_LOCKED
    FaultReasons.CREDENTIALS_NEEDED -> FaultReasons.CREDENTIALS_NEEDED
    FaultReasons.EXPIRED_ROUTE -> FaultReasons.EXPIRED_ROUTE
    FaultReasons.NOTHING_RESOLVABLE -> FaultReasons.NOTHING_RESOLVABLE
    FaultReasons.SCHEMA_DRIFT -> FaultReasons.SCHEMA_DRIFT
    else -> FaultReasons.UNKNOWN
}

fun normalizeRecovery(value: String?): String = when (value) {
    RecoveryActions.RETRY -> RecoveryActions.RETRY
    RecoveryActions.SOLVE_IN_WEBVIEW -> RecoveryActions.SOLVE_IN_WEBVIEW
    RecoveryActions.OPEN_CONNECTOR_SETTINGS -> RecoveryActions.OPEN_CONNECTOR_SETTINGS
    RecoveryActions.SWITCH_CONNECTOR -> RecoveryActions.SWITCH_CONNECTOR
    else -> RecoveryActions.NOTIFY_ONLY
}

fun normalizeCardStyle(value: String?): String = when (value) {
    CardStyles.PORTRAIT -> CardStyles.PORTRAIT
    CardStyles.WIDE -> CardStyles.WIDE
    CardStyles.SQUARE -> CardStyles.SQUARE
    CardStyles.GRID -> CardStyles.GRID
    else -> CardStyles.UNKNOWN
}

fun normalizeContainer(value: String?): String = when (value?.lowercase()) {
    ContainerKinds.HLS -> ContainerKinds.HLS
    ContainerKinds.DASH -> ContainerKinds.DASH
    ContainerKinds.MP4 -> ContainerKinds.MP4
    ContainerKinds.MKV -> ContainerKinds.MKV
    ContainerKinds.WEBM -> ContainerKinds.WEBM
    ContainerKinds.RTMP -> ContainerKinds.RTMP
    ContainerKinds.RTSP -> ContainerKinds.RTSP
    ContainerKinds.TS -> ContainerKinds.TS
    ContainerKinds.WEBVIEW_EMBED -> ContainerKinds.WEBVIEW_EMBED
    else -> ContainerKinds.AUTO
}

fun normalizeAvailability(value: String?): String = when (value) {
    AvailabilityKinds.STREAM_AND_DOWNLOAD -> AvailabilityKinds.STREAM_AND_DOWNLOAD
    AvailabilityKinds.STREAM_ONLY -> AvailabilityKinds.STREAM_ONLY
    AvailabilityKinds.DOWNLOAD_ONLY -> AvailabilityKinds.DOWNLOAD_ONLY
    else -> AvailabilityKinds.UNKNOWN
}

fun normalizeResolutionState(value: String?): String = when (value) {
    ResolutionStates.READY -> ResolutionStates.READY
    ResolutionStates.DEFERRED -> ResolutionStates.DEFERRED
    else -> ResolutionStates.UNKNOWN
}

fun normalizeDelivery(value: String?): String = when (value) {
    DeliveryKinds.MANAGED_HTTP -> DeliveryKinds.MANAGED_HTTP
    DeliveryKinds.BROWSER_HANDOFF -> DeliveryKinds.BROWSER_HANDOFF
    DeliveryKinds.MAGNET -> DeliveryKinds.MAGNET
    else -> DeliveryKinds.UNKNOWN
}

fun normalizeDecoderHint(value: String?): String = when (value) {
    DecoderHints.HW -> DecoderHints.HW
    DecoderHints.SW -> DecoderHints.SW
    else -> DecoderHints.AUTO
}

fun normalizeCaptionSyntax(value: String?): String = when (value?.lowercase()) {
    CaptionSyntax.VTT -> CaptionSyntax.VTT
    CaptionSyntax.SRT -> CaptionSyntax.SRT
    CaptionSyntax.ASS -> CaptionSyntax.ASS
    CaptionSyntax.SSA -> CaptionSyntax.SSA
    CaptionSyntax.TTML -> CaptionSyntax.TTML
    else -> CaptionSyntax.VTT
}

fun normalizeMarkerKind(value: String?): String = when (value?.lowercase()) {
    MarkerKinds.OPENING, MarkerKinds.INTRO -> MarkerKinds.OPENING
    MarkerKinds.RECAP -> MarkerKinds.RECAP
    MarkerKinds.ENDING, MarkerKinds.OUTRO -> MarkerKinds.ENDING
    else -> ""
}

fun UnitItem.displayName(): String {
    val resolved = name?.takeIf { it.isNotBlank() }
    if (resolved != null) return resolved
    val idx = index ?: absoluteIndex
    return if (idx != null) "Episode $idx" else "Episode"
}

fun StreamSource.isReady(): Boolean = normalizeResolutionState(resolutionState) == ResolutionStates.READY && !uri.isNullOrBlank()

fun StreamSource.isDeferred(): Boolean = normalizeResolutionState(resolutionState) == ResolutionStates.DEFERRED

fun StreamSource.isExpired(nowSec: Long = System.currentTimeMillis() / 1000): Boolean {
    val expires = net?.expiresAtSec ?: return false
    return expires <= nowSec
}

fun ProfilePayload.isDownloadOnly(): Boolean = normalizeAvailability(availability) == AvailabilityKinds.DOWNLOAD_ONLY

fun ProfilePayload.hasPlayableStructure(): Boolean = directTarget != null || groups.any { it.units.isNotEmpty() || it.deferredUnits }
