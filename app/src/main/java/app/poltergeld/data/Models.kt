package app.poltergeld.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class AuthResponse(
    val authToken: String? = null,
)

@Serializable
data class HoldingsResponse(
    val holdings: List<Holding> = emptyList(),
)

/** GET /api/v1/user – only the base currency is needed for display. */
@Serializable
data class UserResponse(
    val settings: UserSettings? = null,
)

@Serializable
data class UserSettings(
    val baseCurrency: String? = null,
)

/** Symbol / name / currency live in the nested assetProfile, not on the holding. */
@Serializable
data class AssetProfile(
    val name: String? = null,
    val symbol: String? = null,
    val currency: String? = null,
    val assetClass: String? = null,
    val dataSource: String? = null,
)

/** GET /api/v1/portfolio/performance – overall portfolio performance for a range. */
@Serializable
data class PerformanceResponse(
    val performance: PerformanceNumbers? = null,
)

@Serializable
data class PerformanceNumbers(
    @Serializable(with = LenientDoubleSerializer::class)
    val netPerformancePercentageWithCurrencyEffect: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val netPerformancePercentage: Double? = null,
) {
    val percent: Double?
        get() = netPerformancePercentageWithCurrencyEffect ?: netPerformancePercentage
}

/**
 * A single position as returned by GET /api/v1/portfolio/holdings.
 * Numeric fields are decoded leniently because Ghostfolio occasionally
 * emits them as strings and drops nulls depending on version.
 */
@Serializable
data class Holding(
    val symbol: String? = null,
    val name: String? = null,
    val currency: String? = null,
    val assetProfile: AssetProfile? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val valueInBaseCurrency: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val value: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val allocationInPercentage: Double? = null,
    @SerialName("netPerformancePercentWithCurrencyEffect")
    @Serializable(with = LenientDoubleSerializer::class)
    val netPerformancePercentWithCurrencyEffect: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val netPerformancePercent: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val quantity: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val marketPrice: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val investment: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val dividend: Double? = null,
) {
    val displayName: String
        get() = assetProfile?.name?.takeIf { it.isNotBlank() }
            ?: assetProfile?.symbol?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: symbol
            ?: "?"
    val displayValue: Double get() = valueInBaseCurrency ?: value ?: 0.0
    val performance: Double? get() = netPerformancePercentWithCurrencyEffect ?: netPerformancePercent
}

/**
 * One point of a position's price history, as returned inside
 * GET /api/v1/portfolio/holding/{dataSource}/{symbol}.
 */
@Serializable
data class HistoricalDataItem(
    val date: String,
    @Serializable(with = LenientDoubleSerializer::class)
    val marketPrice: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val value: Double? = null,
) {
    /** What the chart plots: the traded price, or the holding's value if unavailable (e.g. cash). */
    val chartValue: Double? get() = marketPrice ?: value
}

/** GET /api/v1/portfolio/holding/{dataSource}/{symbol} – single-position detail incl. price history. */
@Serializable
data class HoldingDetailResponse(
    @Serializable(with = LenientDoubleSerializer::class)
    val averagePrice: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val marketPrice: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val quantity: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val value: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val netPerformance: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val netPerformancePercent: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val netPerformancePercentWithCurrencyEffect: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val dividendInBaseCurrency: Double? = null,
    val historicalData: List<HistoricalDataItem> = emptyList(),
)

/** One buy/sell/dividend/fee entry, as returned by GET /api/v1/activities. */
@Serializable
data class Activity(
    val id: String? = null,
    val type: String? = null,
    val date: String? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val quantity: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val unitPrice: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val fee: Double? = null,
    val currency: String? = null,
)

@Serializable
data class ActivitiesResponse(
    val activities: List<Activity> = emptyList(),
    val count: Int = 0,
)

/** Accepts numbers encoded either as JSON numbers or JSON strings. */
object LenientDoubleSerializer : kotlinx.serialization.KSerializer<Double?> {
    override val descriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
            "LenientDouble", kotlinx.serialization.descriptors.PrimitiveKind.DOUBLE
        )

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Double? {
        val input = decoder as? kotlinx.serialization.json.JsonDecoder ?: return decoder.decodeDouble()
        val element: JsonElement = input.decodeJsonElement()
        val prim = element as? JsonPrimitive ?: return null
        return prim.doubleOrNull ?: prim.content.toDoubleOrNull()
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Double?) {
        encoder.encodeDouble(value ?: 0.0)
    }
}
