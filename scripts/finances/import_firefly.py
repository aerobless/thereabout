#!/usr/bin/env python3
"""Local-only complete Firefly import and independent ledger reconciliation.
Requires PyMySQL. Source: restored database in the existing local Docker MariaDB.
Never connects to a remote server. Run while the backend is stopped.
"""
import argparse, collections, datetime, decimal, hashlib, json, os, pathlib, subprocess
import pymysql

decimal.getcontext().prec = 60
D = decimal.Decimal


def connection(database):
    info = json.loads(
        subprocess.check_output(["docker", "inspect", "thereabout-mariadb-1"])
    )[0]
    env = dict(e.split("=", 1) for e in info["Config"]["Env"] if "=" in e)
    return pymysql.connect(
        host="127.0.0.1",
        port=3306,
        user="root",
        password=env["MARIADB_ROOT_PASSWORD"],
        database=database,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def dumps(x):
    return json.dumps(x, ensure_ascii=False, default=str)


def fetch(c, sql, args=()):
    with c.cursor() as cur:
        cur.execute(sql, args)
        return cur.fetchall()


def run(c, sql, args=()):
    with c.cursor() as cur:
        cur.execute(sql, args)


def batch(c, table, columns, rows):
    if not rows:
        return
    with c.cursor() as cur:
        cur.executemany(
            "INSERT INTO "
            + table
            + " ("
            + ",".join(columns)
            + ") VALUES ("
            + ",".join(["%s"] * len(columns))
            + ")",
            rows,
        )


def decode(s):
    try:
        return json.loads(s)
    except (ValueError, TypeError):
        return s


def read_source(src):
    names = [
        r["TABLE_NAME"]
        for r in fetch(
            src,
            "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
        )
    ]
    tables = {name: fetch(src, "SELECT * FROM `" + name + "`") for name in names}
    return tables


def import_finances(dst, tables):
    clear_finances(dst)
    import_currencies(dst, tables)
    import_accounts(dst, tables)
    import_categories(dst, tables)
    warnings = import_ledger(dst, tables)
    import_rates(dst, tables)
    archive_source(dst, tables)
    return warnings


def clear_finances(dst):
    # Financial-domain tables only; source backup and unrelated Thereabout modules are untouched.
    for table in [
        "finance_request",
        "finance_audit",
        "finance_valuation",
        "finance_posting",
        "finance_transaction",
        "finance_category",
        "finance_account",
        "finance_currency",
        "finance_rate",
        "finance_archive",
    ]:
        run(dst, "DELETE FROM " + table)


def import_currencies(dst, tables):
    batch(
        dst,
        "finance_currency",
        ["code", "name", "symbol", "decimal_places", "enabled", "source_id"],
        [
            (
                r["code"],
                r["name"],
                r["symbol"],
                r["decimal_places"],
                r["enabled"],
                r["id"],
            )
            for r in tables["transaction_currencies"]
        ],
    )


def import_accounts(dst, tables):
    currencies = {row["id"]: row["code"] for row in tables["transaction_currencies"]}
    accounts = {row["id"]: row for row in tables["accounts"]}
    meta = collections.defaultdict(dict)
    for row in tables["account_meta"]:
        meta[row["account_id"]][row["name"]] = decode(row["data"])
    kindmap = {
        2: "CASH",
        3: "CASH",
        4: "EXPENSE",
        5: "REVENUE",
        6: "OPENING",
        10: "RECONCILIATION",
    }
    arows = []
    for aid, a in accounts.items():
        kind = kindmap[a["account_type_id"]]
        if aid in [281, 283, 284, 286, 624]:
            kind = "INVESTMENT"
        if aid == 1842:
            kind = "REAL_ESTATE"
        currency = currencies.get(int(meta[aid].get("currency_id", 26)))
        # Missing counterparty currency is informational only; postings retain their actual currencies.
        arows.append(
            (
                aid,
                aid,
                a["name"],
                kind,
                currency,
                a["active"],
                a["deleted_at"] is not None,
                str(meta[aid].get("include_net_worth", "0")) == "1",
                dumps({"account": a, "meta": meta[aid]}),
            )
        )
    batch(
        dst,
        "finance_account",
        [
            "id",
            "source_id",
            "name",
            "kind",
            "currency",
            "active",
            "deleted",
            "include_net_worth",
            "metadata",
        ],
        arows,
    )


def import_categories(dst, tables):
    batch(
        dst,
        "finance_category",
        ["id", "source_id", "name", "deleted", "metadata"],
        [
            (r["id"], r["id"], r["name"], r["deleted_at"] is not None, dumps(r))
            for r in tables["categories"]
        ],
    )


def import_ledger(dst, tables):
    currencies = {r["id"]: r["code"] for r in tables["transaction_currencies"]}
    journals = {r["id"]: r for r in tables["transaction_journals"]}
    groups = {r["id"]: r for r in tables["transaction_groups"]}
    legs = collections.defaultdict(list)
    meta = collections.defaultdict(dict)
    jm = collections.defaultdict(dict)
    notes = collections.defaultdict(list)
    categories = {}
    for r in tables["transactions"]:
        legs[r["transaction_journal_id"]].append(r)
    for r in tables["account_meta"]:
        meta[r["account_id"]][r["name"]] = decode(r["data"])
    for r in tables["journal_meta"]:
        jm[r["transaction_journal_id"]][r["name"]] = decode(r["data"])
    for r in tables["notes"]:
        if r["noteable_type"].endswith("TransactionJournal"):
            notes[r["noteable_id"]].append(r["text"])
    for r in tables["category_transaction_journal"]:
        if r["transaction_journal_id"] in categories:
            raise ValueError("Multiple categories; cannot silently flatten")
        categories[r["transaction_journal_id"]] = r["category_id"]
    assert all(len(x) == 2 for x in legs.values()) and len(legs) == len(
        journals
    ), "Unsupported split or missing postings"
    assert all(
        n == 1
        for n in collections.Counter(
            r["transaction_group_id"] for r in journals.values()
        ).values()
    ), "Split groups are not supported"
    warnings = []
    types = {
        1: "WITHDRAWAL",
        2: "DEPOSIT",
        3: "TRANSFER",
        4: "OPENING",
        5: "RECONCILIATION",
    }
    assert set(r["transaction_type_id"] for r in journals.values()) <= set(
        types
    ), "Unsupported transaction type"
    owned = {1, 4, 5, 6, 7, 281, 283, 284, 286, 288, 624, 787, 1132, 1842}
    txrows = []
    prows = []
    valuationrows = []
    balances = collections.defaultdict(lambda: D(0))
    for jid, j in sorted(journals.items(), key=lambda e: (e[1]["date"], e[0])):
        pair = sorted(legs[jid], key=lambda x: (x["amount"], x["id"]))
        assert pair[0]["amount"] <= 0 and pair[1]["amount"] >= 0, (
            "Invalid directions",
            jid,
        )
        assert pair[0]["account_id"] != pair[1]["account_id"], ("Self transfer", jid)
        if pair[0]["transaction_currency_id"] == pair[1]["transaction_currency_id"]:
            difference = pair[0]["amount"] + pair[1]["amount"]
            if difference:
                places = next(
                    c["decimal_places"]
                    for c in tables["transaction_currencies"]
                    if c["id"] == pair[0]["transaction_currency_id"]
                )
                assert abs(difference) < D(10) ** (-places) / 2, (
                    "Materially unbalanced journal",
                    jid,
                    difference,
                )
                warnings.append(
                    f'Journal {jid}: retained source precision discrepancy {difference} {currencies[pair[0]["transaction_currency_id"]]} without rounding either leg.'
                )
        effect = (
            "VALUATION"
            if categories.get(jid) == 19
            else (
                "OPENING"
                if j["transaction_type_id"] == 4
                else "RECONCILIATION" if j["transaction_type_id"] == 5 else "OPERATING"
            )
        )
        deleted = (
            j["deleted_at"] is not None
            or (groups.get(j["transaction_group_id"]) or {}).get("deleted_at")
            is not None
        )
        txrows.append(
            (
                jid,
                jid,
                types[j["transaction_type_id"]],
                effect,
                j["description"],
                j["date"],
                categories.get(jid),
                "\n\n".join(notes[jid]),
                deleted,
                str(jm[jid].get("external_id", "")),
                dumps(
                    {
                        "journal": j,
                        "group": groups.get(j["transaction_group_id"]),
                        "meta": jm[jid],
                    }
                ),
            )
        )
        for side, r in zip(["SOURCE", "DESTINATION"], pair):
            currency = currencies[
                r["transaction_currency_id"] or j["transaction_currency_id"]
            ]
            aid = r["account_id"]
            fc = currencies.get(r["foreign_currency_id"])
            if (
                aid in owned
                and currency != currencies[int(meta[aid].get("currency_id", 26))]
            ):
                if not deleted and r["deleted_at"] is None:
                    raise ValueError(("Active own posting currency mismatch", jid, aid))
                warnings.append(
                    f"Deleted journal {jid} retains historical {currency} posting on account {aid}; restoring requires matching the account currency."
                )
            prows.append(
                (
                    r["id"],
                    r["id"],
                    jid,
                    aid,
                    side,
                    r["amount"],
                    currency,
                    r["foreign_amount"],
                    fc,
                    r["deleted_at"] is not None,
                )
            )
            previous = balances[aid]
            if not deleted and r["deleted_at"] is None:
                balances[aid] += r["amount"]
            if (
                effect == "VALUATION"
                and aid in [281, 283, 284, 286, 624, 1842]
                and not deleted
                and r["deleted_at"] is None
            ):
                valuationrows.append(
                    (
                        aid,
                        j["date"],
                        balances[aid],
                        previous,
                        jid,
                        "firefly-journal-" + str(jid),
                        "FIREFLY_INFERRED",
                    )
                )
    batch(
        dst,
        "finance_transaction",
        [
            "id",
            "source_id",
            "type",
            "effect",
            "description",
            "occurred_at",
            "category_id",
            "notes",
            "deleted",
            "external_reference",
            "metadata",
        ],
        txrows,
    )
    batch(
        dst,
        "finance_posting",
        [
            "id",
            "source_id",
            "transaction_id",
            "account_id",
            "side",
            "amount",
            "currency",
            "foreign_amount",
            "foreign_currency",
            "deleted",
        ],
        prows,
    )
    batch(
        dst,
        "finance_valuation",
        [
            "account_id",
            "occurred_at",
            "reported_value",
            "previous_balance",
            "transaction_id",
            "reference",
            "origin",
        ],
        valuationrows,
    )
    warnings.append(
        "Imported valuation totals are inferred from the historical ledger, not an independently supplied statement; origin FIREFLY_INFERRED."
    )
    return warnings


def import_rates(dst, tables):
    currencies = {row["id"]: row["code"] for row in tables["transaction_currencies"]}
    batch(
        dst,
        "finance_rate",
        ["from_currency", "to_currency", "rate_date", "rate", "source"],
        [
            (
                currencies[r["from_currency_id"]],
                currencies[r["to_currency_id"]],
                r["date"],
                r["user_rate"] or r["rate"],
                "FIREFLY",
            )
            for r in tables["currency_exchange_rates"]
            if not r["deleted_at"]
        ],
    )


def archive_source(dst, tables):
    # Preserve all non-authentication source data, including original deleted rows and ignored financial features.
    excluded = {
        "2fa_tokens",
        "oauth_access_tokens",
        "oauth_auth_codes",
        "oauth_clients",
        "oauth_device_codes",
        "oauth_refresh_tokens",
        "personal_access_tokens",
        "password_resets",
        "sessions",
        "users",
        "configuration",
        "preferences",
        "jobs",
        "failed_jobs",
    }
    archive = []
    for table, rows in tables.items():
        if table in excluded:
            continue
        for i, r in enumerate(rows):
            archive.append((table, str(r.get("id", i)), dumps(r)))
    batch(dst, "finance_archive", ["source_table", "source_key", "payload"], archive)


def reconcile(src, dst, tables, warnings):
    currencies = {row["id"]: row["code"] for row in tables["transaction_currencies"]}
    # Independent SQL aggregation on source vs destination; opening/reconciliation entries included.
    checkpoints = [f"{year}-12-31 23:59:59" for year in range(2018, 2026)] + [
        "2026-08-30 23:59:59",
        "2026-09-26 23:59:59",
    ]
    checks = []
    for date in checkpoints:
        source = fetch(
            src,
            "SELECT t.account_id,t.transaction_currency_id,SUM(t.amount) balance FROM transactions t JOIN transaction_journals j ON j.id=t.transaction_journal_id JOIN transaction_groups g ON g.id=j.transaction_group_id WHERE t.deleted_at IS NULL AND j.deleted_at IS NULL AND g.deleted_at IS NULL AND j.date<=%s GROUP BY t.account_id,t.transaction_currency_id",
            (date,),
        )
        target = fetch(
            dst,
            "SELECT p.account_id,p.currency,SUM(p.amount) balance FROM finance_posting p JOIN finance_transaction t ON t.id=p.transaction_id WHERE p.deleted=0 AND t.deleted=0 AND t.occurred_at<=%s GROUP BY p.account_id,p.currency",
            (date,),
        )
        expected = {
            (r["account_id"], currencies[r["transaction_currency_id"]]): r["balance"]
            for r in source
        }
        actual = {(r["account_id"], r["currency"]): r["balance"] for r in target}
        assert expected == actual, (
            "Balance mismatch at",
            date,
            [
                (k, expected.get(k), actual.get(k))
                for k in expected.keys() | actual.keys()
                if expected.get(k) != actual.get(k)
            ][:10],
        )
        checks.append(
            {"at": date, "account_currency_balances": len(expected), "mismatches": 0}
        )
    for source, target in [
        ("accounts", "finance_account"),
        ("transaction_journals", "finance_transaction"),
        ("transactions", "finance_posting"),
        ("categories", "finance_category"),
    ]:
        assert (
            len(tables[source])
            == fetch(dst, "SELECT COUNT(*) n FROM " + target)[0]["n"]
        ), ("Count mismatch", source)
    exact = fetch(
        dst,
        "SELECT COUNT(*) n FROM finance_posting p JOIN firefly_local_source.transactions s ON s.id=p.source_id WHERE NOT(p.amount <=> s.amount) OR NOT(p.foreign_amount <=> s.foreign_amount)",
    )[0]["n"]
    assert exact == 0
    structural_checks = {
        "posting_relationship_currency_deletion": "SELECT COUNT(*) n FROM finance_posting p JOIN firefly_local_source.transactions s ON s.id=p.source_id JOIN firefly_local_source.transaction_currencies c ON c.id=s.transaction_currency_id LEFT JOIN firefly_local_source.transaction_currencies fc ON fc.id=s.foreign_currency_id WHERE p.account_id<>s.account_id OR p.transaction_id<>s.transaction_journal_id OR BINARY p.currency<>BINARY c.code OR NOT(BINARY p.foreign_currency <=> BINARY fc.code) OR p.deleted<>(s.deleted_at IS NOT NULL)",
        "journal_date_category_deletion": "SELECT COUNT(*) n FROM finance_transaction t JOIN firefly_local_source.transaction_journals j ON j.id=t.source_id JOIN firefly_local_source.transaction_groups g ON g.id=j.transaction_group_id LEFT JOIN firefly_local_source.category_transaction_journal c ON c.transaction_journal_id=j.id WHERE t.occurred_at<>j.date OR NOT(t.category_id <=> c.category_id) OR t.deleted<>(j.deleted_at IS NOT NULL OR g.deleted_at IS NOT NULL)",
        "account_status": "SELECT COUNT(*) n FROM finance_account a JOIN firefly_local_source.accounts s ON s.id=a.source_id WHERE a.active<>s.active OR a.deleted<>(s.deleted_at IS NOT NULL)",
        "category_status": "SELECT COUNT(*) n FROM finance_category c JOIN firefly_local_source.categories s ON s.id=c.source_id WHERE c.deleted<>(s.deleted_at IS NOT NULL)",
    }
    structural_results = {
        name: fetch(dst, sql)[0]["n"] for name, sql in structural_checks.items()
    }
    assert all(value == 0 for value in structural_results.values()), structural_results
    summary = {
        "source": "firefly_local_source (local restored copy)",
        "source_counts": {k: len(v) for k, v in tables.items()},
        "active_transactions": fetch(
            dst, "SELECT COUNT(*) n FROM finance_transaction WHERE deleted=0"
        )[0]["n"],
        "balance_checks": checks,
        "amount_mismatches": exact,
        "structural_mismatches": structural_results,
        "warnings": warnings,
        "verified_at": datetime.datetime.now().isoformat(),
    }
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replace-local-finances", action="store_true")
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument(
        "--target",
        choices=[
            "thereabout",
            "thereabout_finance_test",
            "thereabout_finance_rehearsal",
        ],
        default="thereabout",
    )
    parser.add_argument("--report", required=True)
    args = parser.parse_args()
    if not args.verify_only and not args.replace_local_finances:
        parser.error("Explicit --replace-local-finances required")
    src = connection("firefly_local_source")
    dst = connection(args.target)
    try:
        tables = read_source(src)
        warnings = [] if args.verify_only else import_finances(dst, tables)
        summary = reconcile(src, dst, tables, warnings)
        dst.commit()
        report = pathlib.Path(args.report)
        report.write_text(dumps(summary) + "\n")
        report.chmod(0o600)
        print(
            dumps(
                {key: value for key, value in summary.items() if key != "source_counts"}
            )
        )
    except BaseException:
        dst.rollback()
        raise
    finally:
        src.close()
        dst.close()


if __name__ == "__main__":
    main()
