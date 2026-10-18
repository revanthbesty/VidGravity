"""Inventory resolved coordinates and cached Maven license declarations, without network requests.
Unknown/inherited licenses are explicit follow-up items, not an assertion of compliance.
"""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def inventory(coordinates, cache):
    rows = []
    for coordinate in sorted(set(coordinates.splitlines())):
        if not coordinate.strip():
            continue
        group, name, version = coordinate.split(':', 2)
        poms = sorted((cache / group / name / version).glob('*/*.pom'))
        licenses = []
        for pom in poms:
            raw = pom.read_bytes()
            if b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():
                continue
            doc = ET.fromstring(raw)
            for node in doc.findall('{*}licenses/{*}license'):
                licenses.append({'name': node.findtext('{*}name'), 'url': node.findtext('{*}url')})
        rows.append({'coordinate': coordinate, 'licenses': licenses,
                     'license_status': 'declared_in_cached_pom' if licenses else 'unknown_or_inherited',
                     'scope': 'debugRuntimeClasspath'})
    return {'schema': 1, 'dependencies': rows,
            'limitations': ['POM declarations are not a legal review or full transitive license-text bundle',
                            'Inherited/missing licenses require owner review before distribution',
                            'No vulnerability advisory scan or vulnerability-free claim']}


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--coordinates', type=Path, default=Path('apps/android/app/build/reports/dependencies/runtime-coordinates.txt'))
    p.add_argument('--cache', type=Path, default=Path('apps/android/app/build/reports/dependencies/poms'))
    p.add_argument('--output', type=Path, default=Path('apps/android/app/build/reports/dependencies/licenses.json'))
    a = p.parse_args()
    report = inventory(a.coordinates.read_text(), a.cache)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(report, indent=2) + '\n')
    print('RESOLVED_DEPENDENCIES', len(report['dependencies']), 'UNKNOWN_LICENSES', sum(not row['licenses'] for row in report['dependencies']))
    for row in report['dependencies']:
        if not row['licenses']:
            print('LICENSE_FOLLOW_UP', row['coordinate'])
