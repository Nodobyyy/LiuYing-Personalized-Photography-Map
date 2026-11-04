package cn.photomap.core

import org.junit.Assert.*
import org.junit.Test

class PhotoClusteringTest {
    @Test fun overlappingLocationsRetainEveryPhotoAndId() {
        val points = (0..999).map { PhotoPoint("p$it", 5.0, 5.0, 10) }
        val clusters = PhotoClustering.cluster(points, 80.0, 7)
        assertEquals(1, clusters.size)
        assertEquals(10000L, clusters.single().photoCount)
        assertEquals(1000, clusters.single().members.size)
    }
    @Test fun coverAndMembershipSurvivePanAndInputReordering() {
        val points = (0..99).map { PhotoPoint("p$it", it * 17.0, it * 8.0, it + 1) }
        val a = PhotoClustering.cluster(points, 80.0, 7)
        val b = PhotoClustering.cluster(points.reversed().map { it.copy(x = it.x + 131.0, y = it.y - 58.0) }, 80.0, 7)
        assertEquals(a.map { it.key to it.coverId }, b.map { it.key to it.coverId })
        assertEquals(points.sumOf { it.photoCount.toLong() }, a.sumOf { it.photoCount })
    }
    @Test fun zoomSeparatesAndDenseChainsDoNotCollapse() {
        val points = (0..9).map { PhotoPoint("p$it", it * 50.0, 0.0, 2) }
        assertTrue(PhotoClustering.cluster(points, 80.0, 7).size > 1)
        assertEquals(10, PhotoClustering.cluster(points.map { it.copy(x = it.x * 10) }, 80.0, 7).size)
    }
    @Test fun countsDoNotOverflowInt() {
        val p = listOf(PhotoPoint("a", 0.0, 0.0, Int.MAX_VALUE), PhotoPoint("b", 0.0, 0.0, 1))
        assertEquals(2147483648L, PhotoClustering.cluster(p, 10.0, 0).single().photoCount)
    }
    @Test fun stressScenariosRetainAllLocationsAndPhotoCounts() {
        listOf(100, 500, 1000).forEach { size ->
            val random = kotlin.random.Random(42)
            val points = (0 until size).map { PhotoPoint("p$it", random.nextDouble()*1080, random.nextDouble()*1920, 10) }
            val clusters = PhotoClustering.cluster(points, 88.0, 7)
            assertEquals(size, clusters.sumOf { it.members.size })
            assertEquals(size*10L, clusters.sumOf { it.photoCount })
            assertEquals(points.map { it.id }.toSet(), clusters.flatMap { it.members }.map { it.id }.toSet())
        }
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsRejected() {
        PhotoClustering.cluster(List(2) { PhotoPoint("same", 0.0, 0.0, 1) }, 80.0, 7)
    }
}
