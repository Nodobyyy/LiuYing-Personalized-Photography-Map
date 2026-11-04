package cn.photomap.core

import org.junit.Assert.*
import org.junit.Test

class MapViewportTest {
    @Test fun photoCoverageRemainsSeparateFromWiderMapExploration() {
        assertTrue(MapViewport.inCoverage(39.9,116.4))
        assertTrue(MapViewport.inCoverage(25.03,121.56))
        assertFalse(MapViewport.inCoverage(58.0,68.0))
        assertFalse(MapViewport.inCoverage(Double.NaN,116.4))
    }
    @Test fun zoomAndPhotoSizeAdaptWithoutOverlappingClusterAnchors() {
        assertTrue(MapViewport.minimumZoom(411.0,683.0) in 2.0..2.3)
        assertTrue(MapViewport.markerDp(3.0)<MapViewport.markerDp(14.0))
        for(z in 2..17) {
            val width=MapViewport.markerDp(z.toDouble());val height=width*144/128
            assertTrue(MapViewport.clusterRadiusDp(z.toDouble())>kotlin.math.hypot(width,height))
        }
    }
}
