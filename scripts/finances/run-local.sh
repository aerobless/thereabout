#!/bin/sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$TASK_ROOT/backend"
exec mvn spring-boot:run -Dspring-boot.run.profiles=development
