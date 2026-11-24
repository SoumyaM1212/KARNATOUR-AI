package com.example.karnatour

data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class Place(
    val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val distance_km: Double,
    val description: String,
    val address: String? = null,
    val website: String? = null,
    val opening_hours: String? = null,
    val osm_url: String? = null
)

data class PlacesResponse(
    val count: Int,
    val search_radius_m: Int,
    val results: List<Place>,
    val attribution: String
)
