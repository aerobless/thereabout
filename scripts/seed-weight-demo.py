#!/usr/bin/env python3
"""Add deterministic local weight history without replacing any existing weigh-ins.

Usage: python3 scripts/seed-weight-demo.py [YYYY-MM-DD]
Uses the development MariaDB container's own credentials; never changes preferences.
Demo rows have source 'thereabout-weight-demo (synthetic preview data)'.
"""
from datetime import date, timedelta
from decimal import Decimal
import subprocess
import sys

end = date.fromisoformat(sys.argv[1]) if len(sys.argv) > 1 else date.today()
statements = ['START TRANSACTION;']
for offset in range(60):
    day = end - timedelta(days=59 - offset)
    if offset in (6, 19, 31, 47):
        continue
    weight = Decimal('85.2') - Decimal(offset) * Decimal('0.09') + Decimal(['0.0', '0.2', '-0.1', '0.1', '-0.2', '0.0', '0.1'][offset % 7])
    statements.append(f"""INSERT INTO health_metric (metric_name, metric_date, timestamp, units, qty, source)
SELECT 'weight_body_mass', '{day}', '{day} 08:00:00', 'kg', {weight}, 'thereabout-weight-demo (synthetic preview data)'
WHERE NOT EXISTS (SELECT 1 FROM health_metric WHERE metric_date = '{day}' AND metric_name IN ('weight_body_mass', 'weight', 'body_mass'));""")
statements.extend(['COMMIT;', "SELECT COUNT(*) AS demo_weight_days FROM health_metric WHERE source = 'thereabout-weight-demo (synthetic preview data)';"])
subprocess.run(['docker', 'exec', '-i', 'thereabout-mariadb-1', 'sh', '-c',
    'MYSQL_PWD="$MARIADB_PASSWORD" mariadb -u "$MARIADB_USER" "$MARIADB_DATABASE"'],
    input='\n'.join(statements), text=True, check=True)
