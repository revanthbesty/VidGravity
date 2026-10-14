# VidGravity Feature Documentation Standard

## Mandatory definition of done

A feature is not complete until implementation, tests, user-facing guidance, FAQ/troubleshooting, and release notes are updated together.

For every new feature or material behavior change, commit:

1. **Feature guide:** purpose, prerequisites, where to find it, steps, settings, expected result, examples, platform differences.
2. **FAQ:** common questions, compatibility, limits, data/storage behavior, permissions, and relevant account/pricing details.
3. **Troubleshooting:** observable symptom, possible/confirmed causes, resolution steps, verification, escalation, known workaround.
4. **Release notes:** changes, affected users, UI/workflow changes, requirements, limitations, bug fixes.
5. **In-product help:** link to the relevant article where practical; add Help Center search/category entry.
6. **Tests:** feature regressions plus documentation link/completeness checks.

## Accuracy rules

- Document only behavior that exists in the named build, or label it planned/not available.
- CI success does not prove physical-device behavior.
- Do not promise unsupported resolutions, universal source compatibility, cloud sync, or platform parity.
- Separate confirmed causes from possible causes.
- Use plain language and actionable numbered steps.
- Never ask users to share secrets or tokens.
- State platform/build scope for platform-specific instructions.
- Update screenshots when UI/navigation changes and keep them aligned with the current build.
- Remove obsolete instructions instead of accumulating contradictions.

## Release checklist

- [ ] Implementation and UI reviewed.
- [ ] Automated tests pass.
- [ ] Affected platforms verified.
- [ ] User guide updated.
- [ ] FAQ updated.
- [ ] Troubleshooting updated.
- [ ] Release notes updated.
- [ ] Help Center and in-product links updated.
- [ ] Documentation links checked.
- [ ] Documentation committed with the feature.
