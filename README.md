# thereabout
Thereabout is a self-hosted replacement for Google Location History. It can import your existing Google Location History and visualise it as a heatmap on Google Maps.

![Thereabout UI](/documentation/img/v2.png)

## Features
+ Load existing Google Location History ("Records.json") when exported via [Google Takeout](https://takeout.google.com).
+ Visualise your location history as a heatmap on Google Maps
+ Search for locations on Google Maps (Geocoding)
+ Date picker to select range of data to show on map (from, to)

## Roadmap
+ Docker Image of combined backend & frontend
+ Docker Compose file to easily deploy application
+ DB to store imported location data
+ Easy date-range picker, e.g. year, last 3 years, last 5years, all
+ Day overview page
  + Show trip as connected lines instead of just heatmap
  + Direct link to Google Photos for that day
+ Endpoint to allow recording new location data via Home Assistant or similar
+ Statistics: number of countries visited, km travelled, ...
+ Security: User Login & secure endpoints
+ Manually add/remove location data
+ Export location history as json
+ Endpoint/Integration with OpenAI, e.g. ask "Where was I in November 2023?",
+ Add support to load other dated data into Thereabout, e.g. chatlogs, temperature for location, weather to make a more comprehensive view of a specific day

## Why
Google sadly has decided to discontinue Google Location History / Timeline for the web as you can read [here](https://support.google.com/maps/answer/14169818?visit_id=638499772171143198-2056154066&p=maps_odlh&rd=1). It's still available on device, but in my opinion it's fairly clunky to interact with it on a small screen and secondly it is also no longer possible to easily export the local location history. Especially being unable to export my location data is a no-go for me, as Google has been known to kill various projects/features.. see the [Google graveyard](https://killedbygoogle.com/). Since there is no good alternative that does what I want I've decided to develop my own self-hosted location history.

## export home assistant location data via influx

```influxdb
from(bucket: "homeassistant/autogen")
  |> range(start: 2022-01-01)
  |> filter(fn: (r) => r._measurement == "state" and r._field == "longitude" or r._field == "latitude" )
```
