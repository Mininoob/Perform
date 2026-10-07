package com.preform.reader.spike

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The single source of posture truth for the whole app.
 *
 * Everything that needs to know how the device is folded observes this and nothing
 * else, so there is exactly one place where Samsung's quirks get normalised.
 */
sealed interface FoldPosture {

    /**
     * The device is shut and we are painting the cover screen (Flex Window on a Flip,
     * outer display on a Fold). This is where Book Mode shows the cover.
     */
    data class Closed(val displayId: Int) : FoldPosture

    /** Propped half open. The hinge splits the window and we lay out around it. */
    data class HalfOpen(
        val orientation: FoldingFeature.Orientation,
        val isSeparating: Boolean,
        val hingeTopPx: Int,
        val hingeBottomPx: Int,
    ) : FoldPosture

    /** Fully open on the inner display: the two-page spread lives here. */
    data class Open(val displayId: Int) : FoldPosture
}

/** Everything the probe wants to show on screen, in one immutable snapshot. */
data class PostureSnapshot(
    val posture: FoldPosture,
    val displayId: Int,
    val widthPx: Int,
    val heightPx: Int,
    val rawFeatureCount: Int,
    val describedFeatures: List<String>,
    val activityInstanceId: Int,
    val createCount: Int,
    /** Emissions WindowManager sent us, including sensor noise. */
    val rawEmissions: Int,
    /** Emissions that actually changed the layout. This is what should drive UI. */
    val meaningfulUpdates: Int,
)

/**
 * Turns [WindowLayoutInfo] plus the activity's current display into a [FoldPosture].
 *
 * The important subtlety: once the device is shut, WindowManager reports *no* folding
 * feature at all. That absence - not the display id, which is inverted between a Flip
 * and a Fold - is how we know we are painting the cover screen.
 */
class FoldStateRepository(private val activity: Activity) {

    private var rawEmissions = 0
    private var meaningfulUpdates = 0

    /**
     * Filters the stream down to changes that actually matter.
     *
     * Measured on hardware: with the Flow hoisted behind remember() a stationary
     * device emits once. Build the Flow inside a composable body instead and
     * collection restarts on every recomposition, which emits, which recomposes -
     * ~3747 emissions in 30s. That looks exactly like a flaky hinge sensor and is
     * not. Consumers must hoist.
     */
    fun snapshots(): Flow<PostureSnapshot> =
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                rawEmissions++
                val k = LayoutKey(
                    posture = postureOf(layoutInfo),
                    displayId = currentDisplayId(),
                    bounds = activity.windowManager.currentWindowMetrics.bounds.flattenToString(),
                    features = layoutInfo.displayFeatures
                        .filterIsInstance<FoldingFeature>()
                        .map { it.describe() },
                )
                debugLog("FoldProbe", "emit#$rawEmissions $k")
                k
            }
            .distinctUntilChanged()
            .map { key ->
                meaningfulUpdates++
                toSnapshot(key)
            }

    /** The subset of state that, when it changes, genuinely requires a relayout. */
    private data class LayoutKey(
        val posture: FoldPosture,
        val displayId: Int,
        val bounds: String,
        val features: List<String>,
    )

    private fun postureOf(layoutInfo: WindowLayoutInfo): FoldPosture {
        val displayId = currentDisplayId()
        val fold = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()

        // Which display id is the inner one is NOT stable across devices: on a Z Flip 5
        // the inner screen is display 0, on a Z Fold 8 it is display 1 and the cover is
        // display 0. So never key off the id. The hinge is the device-agnostic signal -
        // WindowManager reports a folding feature only while we are on the inner
        // display, and none at all once we are on the cover screen.
        if (fold == null) return FoldPosture.Closed(displayId)

        return when (fold.state) {
            FoldingFeature.State.HALF_OPENED -> FoldPosture.HalfOpen(
                orientation = fold.orientation,
                isSeparating = fold.isSeparating,
                hingeTopPx = fold.bounds.top,
                hingeBottomPx = fold.bounds.bottom,
            )
            else -> FoldPosture.Open(displayId)
        }
    }

    private fun toSnapshot(key: LayoutKey): PostureSnapshot {
        val metrics = activity.windowManager.currentWindowMetrics.bounds
        return PostureSnapshot(
            posture = key.posture,
            displayId = key.displayId,
            widthPx = metrics.width(),
            heightPx = metrics.height(),
            rawFeatureCount = key.features.size,
            describedFeatures = key.features,
            activityInstanceId = System.identityHashCode(activity),
            createCount = FoldProbeActivity.createCount,
            rawEmissions = rawEmissions,
            meaningfulUpdates = meaningfulUpdates,
        )
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayId(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.display?.displayId ?: 0
        } else {
            activity.windowManager.defaultDisplay.displayId
        }

    private fun FoldingFeature.describe(): String = buildString {
        append("state=").append(state)
        append(" orientation=").append(orientation)
        append(" separating=").append(isSeparating)
        append(" occlusion=").append(occlusionType)
        append(" bounds=").append(bounds.flattenToString())
    }
}
