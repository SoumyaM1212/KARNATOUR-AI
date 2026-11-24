package com.example.karnatour

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object ApiService {
    // For a physical phone, replace this with your deployed HTTPS backend URL.
    // Android emulator uses 10.0.2.2 to reach the computer's localhost.
    var baseUrl: String = BuildConfig.API_BASE_URL

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
        connection.connectTimeout = 20000
        connection.readTimeout = 40000
        connection.requestMethod = "GET"
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("detail") }.getOrDefault(body)
                throw Exception(message.ifBlank { "Server error: $code" })
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    suspend fun geocode(location: String): GeoResult {
        val q = URLEncoder.encode(location, "UTF-8")
        val json = JSONObject(get("/geocode?location=$q"))
        return GeoResult(json.getString("name"), json.getDouble("latitude"), json.getDouble("longitude"))
    }

    suspend fun places(lat: Double, lon: Double, radius: Int, category: String): PlacesResponse {
        val json = JSONObject(get("/places?lat=$lat&lon=$lon&radius=$radius&limit=60&category=$category"))
        val array = json.getJSONArray("results")
        val list = (0 until array.length()).map { i ->
            val p = array.getJSONObject(i)
            Place(
                id = p.getString("id"),
                name = p.getString("name"),
                category = p.getString("category"),
                latitude = p.getDouble("latitude"),
                longitude = p.getDouble("longitude"),
                distance_km = p.getDouble("distance_km"),
                description = p.optString("description", "Tourist attraction"),
                address = p.optString("address").takeIf { it.isNotBlank() },
                website = p.optString("website").takeIf { it.isNotBlank() },
                opening_hours = p.optString("opening_hours").takeIf { it.isNotBlank() },
                osm_url = p.optString("osm_url").takeIf { it.isNotBlank() }
            )
        }
        PlacesResponse(json.getInt("count"), json.getInt("search_radius_m"), list, json.getString("attribution"))
    }
}
