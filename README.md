# thereabout 📍
Thereabout is a self-hosted replacement for Google Location History. Use it to visualise your location history as
heatmap or day-by-day view. You can import your existing Google Location history and add new data via the [Overland](https://overland.p3k.app/) app or by
over the REST API.

![Thereabout Location History](/documentation/img/v7_main.png)

# Features

### Import your existing data
Thereabout supports importing your existing location history data from Google Maps. Export your data via
[Google Takeout](https://takeout.google.com) and upload the `Records.json` file in the configuration page.

![Thereabout Configuration](/documentation/img/v7_config.png)

### Keep adding new location data
Thereabout supports the free [Overland](https://overland.p3k.app/) android/iOS app. The configuration page features a
one-click configuration button to set up Overland on your mobile device. 

For other data sources / client you can also talk to the GeoJSON REST API directly.

### Visualise your data
Thereabout can visualise your data in two ways: as a heatmap or as a day-by-day view. The heatmap shows can show a range
of dates, e.g. the last year, the last 5 years or your full history. Beware that the full history heatmap can take a while to load
and may need be a bit laggy if you have a lot of data.

The day-by-day view shows a polygon line for each day you have data for. 

### Edit your location history
In the day-by-day view you can also edit your data points.
You can drag existing data points on the map to update their location or change their timestamp & other details from the table.
If needed you can also delete one or multiple data points to clean up your history. If your missing some data you can also
add new location entries straight from the UI.

### Remember your favorite trips
Thereabout can remember your favorite trips. On the trip page you can create a new trip simply by providing a name, description, start and end date.
Thereabout will then calculate the distance travelled, countries visited and show you a line on the map for the trip.

![Thereabout Trips](/documentation/img/v7_trips.png)

### Statistics
See how many countries you've visited, how many days you've spent abroad and a detailed list of countries visited with first/last visit and days spent.

![Thereabout Statistics](/documentation/img/v7_statistics.png)

# Installation

> [!IMPORTANT]  
> Thereabout has no login system. You should run it fully locally, via a VPN (e.g. [Tailscale](https://tailscale.com/)) 
> or with a login system like [Authelia](https://www.authelia.com/).
> 
> If you want to continuously add data you'll need to be able to securely expose the GeoJSON endpoint to the internet.
> This endpoint only is secured with an API key.

1. Download the [docker-compose.yml](https://github.com/aerobless/thereabout/blob/main/docker-compose.yaml) file. *(There is also an [example](https://github.com/aerobless/thereabout/blob/main/docker-compose-authelia.yaml) for usage with Authelia.)*
2. Get a Google Maps API Key from the [Google Cloud Console](https://console.cloud.google.com/apis/library/maps-backend.googleapis.com)
and enter it in  the docker-compose.yaml file  where it says `REPLACE_WITH_YOUR_GOOGLE_API_KEY`.
3. Run `docker-compose up -d` in the same directory as the `docker-compose.yml` file.
4. You can now access Thereabout on port 9050: http://localhost:9050

# FAQ

## Why did you build this?
Google sadly has decided to discontinue Google Location History / Timeline for the web as you can read [here](https://support.google.com/maps/answer/14169818?visit_id=638499772171143198-2056154066&p=maps_odlh&rd=1). 
It's still available on device, but in my opinion it's fairly clunky to interact with it on a small screen, 
and secondly it is also no longer possible to easily export the local location history. Especially being unable to 
export my location data is a no-go for me, as Google has been known to kill various projects/features, see the [Google graveyard](https://killedbygoogle.com/). 
Since there is no good alternative that does what I want I've decided to develop my own self-hosted location history.

## Where can I find the REST API?
Thereabout has an integrated Swagger UI. You can find it under `http://localhost:9050/swagger-ui/index.html`.
Alternatively you can look at the Open API spec located [here](https://github.com/aerobless/thereabout/blob/main/backend/src/main/resources/thereabout.openapi.yaml).

## My historic data has a different format - how can I import it?
There are several options available for you:
- Convert your data into a json object that resembles the Google Location History Records.json format.
- Or connect to the database and insert the data directly. The database schema is fairly simple.
- Alternatively you can also use the REST API to insert data.
- And as a final option you could always code your own importer and contribute it back to the project.

## What's next?
These features may or may not get realised depending on my time and motivation.
+ Reverse Geolocation
  + Ability to reverse geolocate points with place & POI information
  + Persist geolocated POIs so that they can be re-used
  + Tag days based on locations visited, e.g. home, work etc.
+ Integration tests
  + InMemory TestDB for integration tests
+ Export location history as .json file
+ Simple single user login system
+ Configurable db passwords
+ Trips
  + search/filter trips
  + additional calculated information
+ Location history improvements
  + mode of travel: car, walking, running, bike etc.
    + jump to google calendar directly from day view
+ Day overview page
  + Weather
  + Major locations visited
  + calculate distance travelled in a day
+ Statistics:
  + km travelled
  + yearly: total km travelled, countries visited, ...
+ Endpoint/Integration with OpenAI, e.g. ask "Where was I in November 2023?",
