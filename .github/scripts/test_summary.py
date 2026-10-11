"""Prints JUnit XML totals per module and fails if a module ran zero tests."""
import glob
import sys
import xml.etree.ElementTree as ET

MODULES = {
    "core/domain": "core/domain/build/test-results/test/*.xml",
    "app": "app/build/test-results/testDebugUnitTest/*.xml",
}

ok = True
for module, pattern in MODULES.items():
    tests = failures = skipped = 0
    for path in glob.glob(pattern):
        root = ET.parse(path).getroot()
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0)) + int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
    print(f"{module}: {tests} tests, {failures} failed, {skipped} skipped")
    if tests == 0 or failures:
        ok = False

sys.exit(0 if ok else 1)
