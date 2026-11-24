
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

import httpx
from math import radians, sin, cos, sqrt, atan2
from typing import Optional


# --------------------------------------------------
# APPLICATION CONFIGURATION
# --------------------------------------------------

app = FastAPI(
    title="KARNATOUR AI API",
    description="Karnataka Tourist Place Discovery and Recommendation API",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"]
)

HEADERS = {
    "User-Agent": "KarnatourAI/2.0 (tourism discovery application)"
}

NOMINATIM = "https://nominatim.openstreetmap.org/search"

# Multiple Overpass servers for automatic fallback
OVERPASS_SERVERS = [
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter"
]


# --------------------------------------------------
# DISTANCE CALCULATION
# --------------------------------------------------

def distance_km(lat1, lon1, lat2, lon2):
    earth_radius = 6371.0

    dlat = radians(lat2 - lat1)
    dlon = radians(lon2 - lon1)

    a = (
        sin(dlat / 2) ** 2
        + cos(radians(lat1))
        * cos(radians(lat2))
        * sin(dlon / 2) ** 2
    )

    return 2 * earth_radius * atan2(
        sqrt(a),
        sqrt(1 - a)
    )


# --------------------------------------------------
# OVERPASS QUERY
# --------------------------------------------------

def create_overpass_query(lat, lon, radius, category):

    category = (category or "all").lower()

    category_filters = {
        "attraction": ['["tourism"="attraction"]'],
        "museum": ['["tourism"="museum"]'],
        "viewpoint": ['["tourism"="viewpoint"]'],
        "monument": [
            '["historic"~"monument|memorial|castle|ruins|fort"]'
        ],
        "temple": ['["amenity"="place_of_worship"]']
    }

    if category == "all":
        selectors = [
            '["tourism"~"attraction|museum|viewpoint|zoo|theme_park|aquarium|gallery"]',
            '["historic"~"monument|memorial|castle|ruins|fort"]',
            '["amenity"="place_of_worship"]'
        ]
    else:
        selectors = category_filters.get(
            category,
            category_filters.get("attraction")
        )

    query_parts = []

    for selector in selectors:
        query_parts.append(
            f"nwr(around:{radius},{lat},{lon}){selector};"
        )

    query = f"""
    [out:json][timeout:25];
    (
        {"".join(query_parts)}
    );
    out center tags;
    """

    return query


# --------------------------------------------------
# FETCH PLACES WITH AUTOMATIC SERVER FALLBACK
# --------------------------------------------------

async def fetch_overpass_data(query):

    errors = []

    timeout = httpx.Timeout(
        connect=10.0,
        read=30.0,
        write=20.0,
        pool=10.0
    )

    async with httpx.AsyncClient(
        timeout=timeout,
        headers=HEADERS,
        follow_redirects=True
    ) as client:

        for server in OVERPASS_SERVERS:

            try:
                print(f"Trying Overpass server: {server}")

                response = await client.post(
                    server,
                    data={"data": query}
                )

                response.raise_for_status()

                payload = response.json()

                if not isinstance(payload, dict):
                    raise ValueError("Invalid Overpass response format")

                if "elements" not in payload:
                    raise ValueError("Overpass response has no elements")

                print(f"Successfully connected: {server}")

                return payload

            except Exception as exc:

                error_message = (
                    f"{server}: {type(exc).__name__}: {repr(exc)}"
                )

                errors.append(error_message)

                print("Overpass server failed:", error_message)

    # All servers failed
    raise HTTPException(
        status_code=503,
        detail={
            "message": "All OpenStreetMap Overpass servers are currently unavailable.",
            "server_errors": errors,
            "suggestion": "Please try again after a short time."
        }
    )


# --------------------------------------------------
# HOME
# --------------------------------------------------

@app.get("/")
def home():

    return {
        "message": "Welcome to KARNATOUR AI API",
        "version": "2.0.0",
        "docs": "/docs",
        "health": "/health"
    }


# --------------------------------------------------
# HEALTH CHECK
# --------------------------------------------------

@app.get("/health")
def health():

    return {
        "status": "healthy",
        "application": "KARNATOUR AI"
    }


# --------------------------------------------------
# GEOCODING
# --------------------------------------------------

@app.get("/geocode")
async def geocode(
    location: str = Query(..., min_length=2)
):

    params = {
        "q": f"{location}, Karnataka, India",
        "format": "jsonv2",
        "limit": 1
    }

    try:

        async with httpx.AsyncClient(
            timeout=20,
            headers=HEADERS
        ) as client:

            response = await client.get(
                NOMINATIM,
                params=params
            )

            response.raise_for_status()

            data = response.json()

    except (httpx.HTTPError, ValueError) as exc:

        print("Geocoding error:", repr(exc))

        raise HTTPException(
            status_code=502,
            detail=f"Geocoding service error: {type(exc).__name__}: {repr(exc)}"
        )

    if not data:

        raise HTTPException(
            status_code=404,
            detail="Location not found. Try a Karnataka city or town."
        )

    item = data[0]

    return {
        "name": item.get("display_name", location),
        "latitude": float(item["lat"]),
        "longitude": float(item["lon"])
    }


# --------------------------------------------------
# NEARBY TOURIST PLACES
# --------------------------------------------------

@app.get("/places")
async def nearby_places(

    lat: float = Query(..., ge=-90, le=90),

    lon: float = Query(..., ge=-180, le=180),

    radius: int = Query(15000, ge=500, le=50000),

    limit: int = Query(50, ge=1, le=100),

    category: Optional[str] = Query("all")

):

    query = create_overpass_query(
        lat,
        lon,
        radius,
        category
    )

    # Get data using automatic Overpass fallback
    payload = await fetch_overpass_data(query)

    results = []
    seen = set()

    for item in payload.get("elements", []):

        item_tags = item.get("tags", {})

        name = (
            item_tags.get("name")
            or item_tags.get("name:en")
        )

        if not name:
            continue

        if name.lower() in seen:
            continue

        center = item.get("center", {})

        place_lat = item.get("lat", center.get("lat"))
        place_lon = item.get("lon", center.get("lon"))

        if place_lat is None or place_lon is None:
            continue

        seen.add(name.lower())

        tourism = item_tags.get("tourism")
        historic = item_tags.get("historic")
        amenity = item_tags.get("amenity")

        kind = (
            tourism
            or historic
            or (
                "place of worship"
                if amenity == "place_of_worship"
                else "attraction"
            )
        )

        km = distance_km(
            lat,
            lon,
            float(place_lat),
            float(place_lon)
        )

        results.append({

            "id": f'{item.get("type")}-{item.get("id")}',

            "name": name,

            "category": str(kind).replace("_", " ").title(),

            "latitude": float(place_lat),

            "longitude": float(place_lon),

            "distance_km": round(km, 2),

            "description": (
                item_tags.get("description")
                or item_tags.get("wikipedia")
                or "Tourist place listed in OpenStreetMap."
            ),

            "address": ", ".join(
                x for x in [
                    item_tags.get("addr:suburb"),
                    item_tags.get("addr:city"),
                    item_tags.get("addr:state")
                ] if x
            ),

            "website": item_tags.get("website"),

            "opening_hours": item_tags.get("opening_hours"),

            "osm_url": (
                f'https://www.openstreetmap.org/'
                f'{item.get("type")}/{item.get("id")}'
            )
        })

    results.sort(
        key=lambda x: x["distance_km"]
    )

    return {

        "count": min(len(results), limit),

        "search_radius_m": radius,

        "results": results[:limit],

        "attribution": "Map and place data © OpenStreetMap contributors"
    }


# --------------------------------------------------
# TOURIST RECOMMENDATIONS
# --------------------------------------------------

@app.get("/recommendations")
async def recommendations(

    lat: float = Query(..., ge=-90, le=90),

    lon: float = Query(..., ge=-180, le=180),

    radius: int = Query(10000, ge=500, le=50000),

    interests: str = "history,nature"

):

    # Get nearby places from the existing endpoint function
    places_data = await nearby_places(
        lat=lat,
        lon=lon,
        radius=radius,
        limit=100,
        category="all"
    )

    places = places_data.get("results", [])

    keywords = {

        "history": [
            "temple", "monument", "palace",
            "fort", "historical", "hampi", "king",
            "ruins", "castle"
        ],

        "nature": [
            "hill", "lake", "river", "forest",
            "waterfall", "nature", "viewpoint"
        ],

        "waterfalls": [
            "waterfall", "falls"
        ],

        "beaches": [
            "beach", "coast"
        ],

        "temples": [
            "temple", "shrine", "worship"
        ],

        "museums": [
            "museum", "gallery"
        ],

        "adventure": [
            "climbing", "trek", "adventure", "cave"
        ],

        "wildlife": [
            "wildlife", "zoo", "sanctuary", "aquarium"
        ]
    }

    selected_interests = [
        item.strip().lower()
        for item in interests.split(",")
        if item.strip()
    ]

    # "all" means consider every supported interest
    if "all" in selected_interests:
        selected_interests = list(keywords.keys())

    recommendations_list = []

    radius_km = radius / 1000

    for place in places:

        name = place.get("name", "").lower()

        category = place.get("category", "").lower()

        description = place.get("description", "").lower()

        text_data = name + " " + category + " " + description

        score = 0

        matched_interests = []

        for interest in selected_interests:

            matches = keywords.get(
                interest,
                [interest]
            )

            if any(word in text_data for word in matches):

                score += 70

                matched_interests.append(interest)

        distance = float(
            place.get("distance_km", 0)
        )

        distance_score = max(
            0,
            30 * (1 - distance / radius_km)
        )

        score += distance_score

        place["recommendation_score"] = round(
            min(score, 100),
            2
        )

        place["matched_interests"] = matched_interests

        recommendations_list.append(place)

    recommendations_list.sort(
        key=lambda item: item["recommendation_score"],
        reverse=True
    )

    return {

        "total_recommendations": len(recommendations_list),

        "selected_interests": selected_interests,

        "recommendations": recommendations_list[:20],

        "algorithm": "Keyword matching and distance-based ranking"
    }
