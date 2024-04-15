# thereabout
self-hosted replacement for location-history

## export home assistant location data via influx

```influxdb
from(bucket: "homeassistant/autogen")
  |> range(start: 2022-01-01)
  |> filter(fn: (r) => r._measurement == "state" and r._field == "longitude" or r._field == "latitude" )
```