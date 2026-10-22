"""Run the SQL file against the SQLite database (executescript commits statement by statement)."""
import sqlite3
import sys

database, path = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as file:
    statement = file.read()
connection = sqlite3.connect(database)
with connection:
    connection.executescript(statement)
print(f"applied {path} to {database}")
