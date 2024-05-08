# thereabout
Thereabout is a self-hosted replacement for Google Location History. It can import your existing Google Location History and visualise it as a heatmap on Google Maps.

![Thereabout UI](/documentation/img/v5.png)

## Features
+ Load existing Google Location History ("Records.json") when exported via [Google Takeout](https://takeout.google.com).
  + Geolocate country for each entry locally.
  + Store the imported data in mariaDB.
+ Visualise your location history as a heatmap on Google Maps
  + Filter by date range: custom, YTD, 1 year, 5 years, full history
+ Visualise a specific day as a polygon line on Google Maps
  + Locate the first or any entry on the map
  + List all location entries for the given day and show the selected entry as a marker on the map
  + Open Google Photos on the selected day
+ Geocoding: Search for any location on Google Maps
+ Endpoint to add new location entries continuously , e.g. via Home Assistant
+ Deploy the application via Docker Compose
+ GitHub Build Pipeline

## Roadmap
+ Add detailed geolocation via Google Maps & persist results in DB (verify how many requests are free)
+ Day overview page
  + Integrate Google Photos API (low prio)
  + Weather
  + Major locations visited
  + Delete location entry (high prio)
  + Add location entry (low prio)
  + Update location entry (low prio)
+ Endpoint to allow recording new location data via Home Assistant or similar
+ Statistics: number of countries visited, km travelled, ...
+ Security: User Login & secure endpoints
+ Manually add/remove location data
+ Export location history as json
+ Endpoint/Integration with OpenAI, e.g. ask "Where was I in November 2023?",
+ Add support to load other dated data into Thereabout, e.g. chatlogs, temperature for location, weather to make a more comprehensive view of a specific day

## Bugs
+ It's possible to trigger multiple imports resulting in duplicated records
+ Timezones are weird & annoying as always... need to decide how to deal with viewing data from different time zones

## Why
Google sadly has decided to discontinue Google Location History / Timeline for the web as you can read [here](https://support.google.com/maps/answer/14169818?visit_id=638499772171143198-2056154066&p=maps_odlh&rd=1). It's still available on device, but in my opinion it's fairly clunky to interact with it on a small screen and secondly it is also no longer possible to easily export the local location history. Especially being unable to export my location data is a no-go for me, as Google has been known to kill various projects/features.. see the [Google graveyard](https://killedbygoogle.com/). Since there is no good alternative that does what I want I've decided to develop my own self-hosted location history.

## Security & privacy implications
Thereabout runs locally and does not upload your location data to the internet. It does however require the use of
a Google Maps API Key to load the Map. Google may be able to track your usage of Google Maps.

Please beware that Thereabout is not intended to be run on the public internet. Either run it fully locally or behind a 
authorization server such as [Authelia](https://www.authelia.com/). Thereabout itself does not have a login solution at this
time and is not intended as a multi-tenant application. Any user with access can see all data, including your Google Maps API key.

## How to continue adding data
See the Swagger UI for the API documentation under `http://localhost:9050/swagger-ui/index.html`. There is an endpoint to
add new location data. You can use this endpoint to add new location data from Home Assistant with Node RED or some other automation tool.

## Usage
1. Clone the repository or download the [docker-compose.yml](https://github.com/aerobless/thereabout/blob/main/docker-compose.yaml) file
2. Get a Google Maps API Key from the [Google Cloud Console](https://console.cloud.google.com/apis/library/maps-backend.googleapis.com)
3. Add the location of your `Records.json` file to the `docker-compose.yml` file. Records.json can be exported from [Google Takeout](https://takeout.google.com).
4. Add the Google Maps API Key to the `docker-compose.yml` file & run it.

## export home assistant location data via influx

```influxdb
from(bucket: "homeassistant/autogen")
  |> range(start: 2022-01-01)
  |> filter(fn: (r) => r._measurement == "state" and r._field == "longitude" or r._field == "latitude" )
```
