"""Summarize generated Gradle XML; never substitute for Gradle/test job conclusions."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path('apps/android/app/build')
files = list((root / 'test-results/testDebugUnitTest').glob('TEST-*.xml'))
totals = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
for report in files:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, '0'))
print('JVM_UNIT_XML', 'suites=' + str(len(files)), totals if files else 'REPORT_MISSING')
lint = root / 'reports/lint-results-debug.xml'
if lint.is_file():
    counts = {}
    for issue in ET.parse(lint).getroot().findall('issue'):
        level = issue.get('severity', 'Unknown')
        counts[level] = counts.get(level, 0) + 1
    print('LINT_XML', counts)
    ids = {}
    for issue in ET.parse(lint).getroot().findall('issue'):
        key = issue.get('id', 'Unknown')
        ids[key] = ids.get(key, 0) + 1
    print('LINT_ISSUE_IDS', ids)
else:
    print('LINT_XML REPORT_MISSING')
