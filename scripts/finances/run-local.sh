#!/bin/sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$TASK_ROOT/backend"
exec mvn spring-boot:run -Dspring-boot.run.profiles=development,finance-local -Dspring-boot.run.arguments=--thereabout.launcher.fetch-icons=false
