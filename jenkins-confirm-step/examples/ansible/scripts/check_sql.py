"""Exit 1 when the SQL does not run on an empty in-memory SQLite database."""
import sqlite3
import sys

with open(sys.argv[1], encoding="utf-8") as file:
    statement = file.read()
try:
    sqlite3.connect(":memory:").executescript(statement)
except sqlite3.Error as error:
    print(f"SQL check failed: {error}")
    sys.exit(1)
print("SQL check passed")
