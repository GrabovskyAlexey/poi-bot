package ru.grabovsky.poibot.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_008.8
    private const val METERS_PER_DEGREE_LAT = EARTH_RADIUS_METERS * Math.PI / 180.0
    private const val BOX_SAFETY_MARGIN = 1.01
    private const val MIN_COS_LAT = 0.01

    /** Расстояние по большой окружности (haversine), метры. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Прямоугольник, гарантированно содержащий круг заданного радиуса (предфильтр для SQL). */
    fun boundingBox(lat: Double, lon: Double, radiusMeters: Double): BoundingBox {
        val dLat = radiusMeters * BOX_SAFETY_MARGIN / METERS_PER_DEGREE_LAT
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(MIN_COS_LAT)
        val dLon = radiusMeters * BOX_SAFETY_MARGIN / (METERS_PER_DEGREE_LAT * cosLat)
        return BoundingBox(lat - dLat, lat + dLat, lon - dLon, lon + dLon)
    }
}
