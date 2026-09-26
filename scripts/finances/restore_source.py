#!/usr/bin/env python3
"""Copy the approved Firefly backup and restore only a local source schema.
Usage: python3 scripts/finances/restore_source.py /path/to/Firefly-III_2026-09-26_10-18-00.zip
The original archive is read-only. Never uses a remote database connection.
"""
import hashlib
import pathlib
import shutil
import subprocess
import sys
import zipfile


def restore(source):
    source = pathlib.Path(source).expanduser().resolve()
    private = pathlib.Path.home() / "Library/Application Support/thereabout-finances"
    private.mkdir(parents=True, exist_ok=True, mode=0o700)
    copy = private / source.name
    if source != copy:
        shutil.copy2(source, copy)
    assert (
        hashlib.sha256(source.read_bytes()).digest()
        == hashlib.sha256(copy.read_bytes()).digest()
    )
    with zipfile.ZipFile(copy) as archive:
        names = archive.namelist()
        dump_name = next(n for n in names if n.endswith("database/application.sql"))
        data = archive.read(dump_name)
        expected = "57c049aa3efed1559d458cc818a24022ce58a60f441e42d55c0538e6f473bc34"
        assert (
            hashlib.sha256(data).hexdigest() == expected
        ), "Unexpected dump: verify the backup before proceeding"
        dump_path = private / "database/application.sql"
        dump_path.parent.mkdir(exist_ok=True)
        dump_path.write_bytes(data)
    dump_path.chmod(0o600)
    copy.chmod(0o600)
    # Names are fixed locally, not taken from user-entered SQL identifiers.
    local_sql = data.replace(b"`firefly`", b"`firefly_local_source`")
    subprocess.run(
        [
            "docker",
            "exec",
            "-i",
            "thereabout-mariadb-1",
            "sh",
            "-c",
            'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"',
        ],
        input=local_sql,
        check=True,
    )
    print("Verified dump restored to firefly_local_source in local Docker MariaDB.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    restore(sys.argv[1])
