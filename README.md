# KARNATOUR AI — Karnataka Tourism Discovery App

An Android app (Kotlin + Jetpack Compose) with a Python FastAPI backend. Search a Karnataka city/town or use GPS to find nearby mapped tourist attractions.

## Important data note
Place data comes from OpenStreetMap via Nominatim and Overpass. Coverage varies by area; missing places do not mean a destination has no attractions. Opening hours and descriptions are shown only when available in map data. Respect OpenStreetMap/Nominatim/Overpass usage policies, provide attribution, cache responsibly, and do not run bulk scraping.

## 1. Run backend locally (VS Code)
```powershell
cd backend
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```
Test:
- http://127.0.0.1:8000/health
- http://127.0.0.1:8000/docs
- http://127.0.0.1:8000/geocode?location=Hampi
- http://127.0.0.1:8000/places?lat=15.335&lon=76.46&radius=20000

If PowerShell blocks activation, run `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` then activate again.

## 2. Open Android project
Open the `android` folder in Android Studio. Allow Gradle sync. Install Android SDK 35 if prompted. Run on an emulator or Android phone.

The default API URL `http://10.0.2.2:8000/` is for the Android emulator connecting to the backend on the same PC. Keep the backend running.

For a real Android phone on the same Wi-Fi, change `API_BASE_URL` in `android/app/build.gradle.kts` to `http://YOUR_PC_LAN_IP:8000/`, then rebuild. For a deployed backend, use its HTTPS URL, ending in `/`.

## 3. Deploy backend to Render
1. Push the `backend` folder contents to a GitHub repository (or deploy the repository with the backend as root).
2. In Render, create a new Web Service from the repository.
3. Set Root Directory to `backend` if the backend is in a subfolder.
4. Choose Docker runtime / use `Dockerfile`.
5. Deploy and test `https://YOUR-SERVICE.onrender.com/health`.
6. Copy the deployed URL.

## 4. Connect Android to deployed backend
Set `API_BASE_URL` in `android/app/build.gradle.kts`:
```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://YOUR-SERVICE.onrender.com/\"")
```
Sync Gradle and rebuild.

## 5. Build APK
Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s).
The debug APK is normally at:
`android/app/build/outputs/apk/debug/app-debug.apk`

For release: Build > Generate Signed Bundle / APK, create and securely store a signing key, choose APK, and complete the wizard.

## API endpoints
- `GET /health`
- `GET /geocode?location=Belagavi`
- `GET /places?lat=15.85&lon=74.50&radius=15000&limit=50&category=all`
Categories: `all`, `attraction`, `museum`, `viewpoint`, `monument`, `temple`.

## Troubleshooting
- `CLEARTEXT communication not permitted`: local HTTP is enabled in this academic starter. Use HTTPS for deployment.
- `Connection refused`: start FastAPI and verify the base URL. Emulator uses `10.0.2.2`, not `localhost`.
- No GPS result: enable device location and grant location permission.
- No attractions: increase radius, choose All, or try another town. OSM data completeness varies.
- Overpass 429/504: service may be busy; wait and retry. For a public production app, use a suitable hosted data provider or your own cache/backend infrastructure.

## Production improvements
Add request caching, rate limiting, API monitoring, privacy policy, proper error analytics, HTTPS-only networking, tests, and a maintained map SDK. Ask permission before accessing location and do not store precise location unless needed.
