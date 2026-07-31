package tv.withaibuild.customiuizer.mods.utils

/**
 * Typed identity for a feature.
 *
 * A feature is identified by its [FeatureId] value, not by a plain string.  Implementations are
 * usually `object` or `data object` declarations so equality is identity-based, but any stable
 * implementation is allowed.
 */
interface FeatureId {
    /** Human-readable name for diagnostics. */
    val name: String
}
