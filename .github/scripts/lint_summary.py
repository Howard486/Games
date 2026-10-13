"""Prints Android Lint findings by severity and issue id, so they are visible in the CI log."""
import collections
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1] if len(sys.argv) > 1 else "app/build/reports/lint-results-debug.xml"
try:
    root = ET.parse(path).getroot()
except (FileNotFoundError, ET.ParseError) as error:
    print(f"No lint report at {path}: {error}")
    sys.exit(0)

counts = collections.Counter()
examples = {}
for issue in root.iter("issue"):
    key = (issue.get("severity"), issue.get("id"))
    counts[key] += 1
    location = issue.find("location")
    if key not in examples and location is not None:
        examples[key] = f"{location.get('file', '').split('/app/')[-1]}:{location.get('line', '?')}"

if not counts:
    print("Lint: no issues")
for (severity, issue_id), count in sorted(counts.items(), key=lambda item: (item[0][0] != "Error", -item[1])):
    print(f"{severity:<12} {issue_id:<40} x{count:<3} e.g. {examples.get((severity, issue_id), '-')}")
