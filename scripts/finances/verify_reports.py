#!/usr/bin/env python3
"""Independently compare 2026 REST reports to the restored Firefly ledger (read-only)."""
import collections
import decimal
import json
import pathlib
import urllib.request
import urllib.parse
from import_firefly import connection, fetch, dumps

decimal.getcontext().prec = 60
D = decimal.Decimal


def api(args):
    url = (
        "http://127.0.0.1:9050/api/finances/reports/income-expenses?"
        + urllib.parse.urlencode(args)
    )
    with urllib.request.urlopen(url, timeout=30) as response:
        return json.load(response)


def reconcile_reports(source):
    rows = fetch(
        source,
        """SELECT t.account_id,t.amount,c.code,j.transaction_type_id,j.date,k.category_id
     FROM transactions t JOIN accounts a ON a.id=t.account_id
     JOIN transaction_journals j ON j.id=t.transaction_journal_id
     JOIN transaction_groups g ON g.id=j.transaction_group_id
     JOIN transaction_currencies c ON c.id=t.transaction_currency_id
     LEFT JOIN category_transaction_journal k ON k.transaction_journal_id=j.id
     WHERE a.account_type_id IN (2,3) AND a.deleted_at IS NULL
     AND t.deleted_at IS NULL AND j.deleted_at IS NULL AND g.deleted_at IS NULL
     AND j.date>='2026-01-01' AND j.date<'2027-01-01'""",
    )
    categories = collections.defaultdict(lambda: [D(0), D(0)])
    months = collections.defaultdict(lambda: [D(0), D(0)])
    valuation = collections.defaultdict(lambda: D(0))
    for row in rows:
        # The 2026 source ledger has CHF own-account activity only; original foreign amounts are metadata.
        assert (
            row["code"] == "CHF"
        ), "Extend this independent check with foreign-currency quotes for a different data set"
        amount = row["amount"]
        if row["category_id"] == 19:
            valuation[row["account_id"]] += amount
        elif row["transaction_type_id"] in (1, 2):
            side = 0 if amount >= 0 else 1
            categories[row["category_id"]][side] += abs(amount)
            months[row["date"].strftime("%Y-%m")][side] += abs(amount)
    report = api({"from": "2026-01-01", "to": "2026-12-31"})
    for actual in report["categories"]:
        expected = categories.pop(actual["categoryId"])
        assert (
            D(actual["income"]) == expected[0] and D(actual["expenses"]) == expected[1]
        )
    assert not categories
    for actual in report["months"]:
        expected = months.pop(actual["name"])
        assert (
            D(actual["income"]) == expected[0] and D(actual["expenses"]) == expected[1]
        )
    assert not months
    for account in report["investments"]:
        assert D(account["valuationChange"]) == valuation[account["id"]]
        assert D(account["start"]) + D(account["inflows"]) - D(account["outflows"]) + D(
            account["valuationChange"]
        ) + D(account["adjustments"]) == D(account["end"])
    summary = {
        "category_groups": len(report["categories"]),
        "monthly_groups": len(report["months"]),
        "asset_reconciliations": len(report["investments"]),
        "mismatches": 0,
        "income": report["income"],
        "expenses": report["expenses"],
        "net": report["net"],
        "investment_valuation_change": str(
            sum(
                D(a["valuationChange"])
                for a in report["investments"]
                if a["kind"] == "INVESTMENT"
            )
        ),
    }
    return summary


def main():
    source = connection("firefly_local_source")
    try:
        summary = reconcile_reports(source)
        private = (
            pathlib.Path.home() / "Library/Application Support/thereabout-finances"
        )
        report = private / "report-reconciliation.json"
        report.write_text(dumps(summary) + "\n")
        report.chmod(0o600)
        print(dumps(summary))
    finally:
        source.close()


if __name__ == "__main__":
    main()
