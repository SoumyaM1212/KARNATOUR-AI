package com.example.karnatour

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private val forest = Color(0xFF145A43)
private val pale = Color(0xFFF2F7F3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KarnatourScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KarnatourScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationHelper = remember { LocationHelper(context) }
    var locationText by remember { mutableStateOf("Mysuru") }
    var radius by remember { mutableStateOf(15000f) }
    var category by remember { mutableStateOf("all") }
    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var message by remember { mutableStateOf("Search a Karnataka destination to discover places nearby.") }
    var busy by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    fun search(lat: Double, lon: Double) {
        scope.launch {
            busy = true
            message = "Finding attractions near you..."
            try {
                val response = ApiService.places(lat, lon, radius.toInt(), category)
                places = response.results
                hasSearched = true
                message = if (places.isEmpty()) "No mapped attractions found. Try a larger radius or another location."
                          else "${places.size} places found nearby"
            } catch (e: Exception) {
                message = e.message ?: "Unable to connect. Check your backend URL and internet."
            } finally { busy = false }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                     result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                busy = true
                try {
                    val coords = locationHelper.currentLocation()
                    if (coords != null) search(coords.first, coords.second)
                    else message = "Location unavailable. Turn on GPS and try again."
                } finally { busy = false }
            }
        } else message = "Location permission was not granted. You can search by city instead."
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = forest, background = pale)) {
        Scaffold(containerColor = pale) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(
                    Modifier.fillMaxWidth().background(forest).padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Text("KARNATOUR", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Discover Karnataka, one place at a time", color = Color(0xFFD8F0E4))
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Enter city or place", color = Color.White) },
                        placeholder = { Text("e.g. Belagavi, Hampi, Coorg") },
                        trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White, unfocusedBorderColor = Color.LightGray,
                            focusedLabelColor = Color.White, unfocusedLabelColor = Color.White
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (locationText.isBlank()) { message = "Enter a location first"; return@Button }
                                scope.launch {
                                    busy = true
                                    try {
                                        val point = ApiService.geocode(locationText)
                                        search(point.latitude, point.longitude)
                                    } catch (e: Exception) { message = e.message ?: "Location search failed" }
                                    finally { busy = false }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = forest)
                        ) { Text("Find places") }
                        OutlinedButton(
                            onClick = {
                                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (fine || coarse) {
                                    scope.launch {
                                        val point = locationHelper.currentLocation()
                                        if (point != null) search(point.first, point.second)
                                        else message = "Location unavailable. Enable GPS."
                                    }
                                } else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(Modifier.width(4.dp)); Text("Use my GPS")
                        }
                    }
                }

                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Search radius: ${(radius / 1000).toInt()} km", fontWeight = FontWeight.SemiBold)
                    Slider(value = radius, onValueChange = { radius = it }, valueRange = 1000f..50000f)
                    Text("Category", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("all" to "All", "attraction" to "Attractions", "museum" to "Museums", "viewpoint" to "Viewpoints").forEach { (key, label) ->
                            FilterChip(selected = category == key, onClick = { category = key }, label = { Text(label) })
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(message, color = forest, modifier = Modifier.padding(vertical = 8.dp), fontWeight = FontWeight.Medium)
                }

                if (!hasSearched) {
                    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("🏞️", style = MaterialTheme.typography.displayLarge)
                        Text("Your Karnataka adventure starts here", fontWeight = FontWeight.Bold)
                        Text("Try Hampi, Mysuru, Gokarna or Coorg", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(places, key = { it.id }) { place ->
                            PlaceCard(place) {
                                val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${place.latitude},${place.longitude}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }
                    }
                }
                Text(
                    "Place data © OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun PlaceCard(place: Place, onDirections: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${place.category}  •  ${place.distance_km} km away", color = forest, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Text(place.description, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
            place.opening_hours?.let { Text("Opening hours: $it", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onDirections, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(Modifier.width(6.dp)); Text("Get directions")
            }
        }
    }
}
