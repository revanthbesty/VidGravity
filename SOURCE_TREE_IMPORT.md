# Import the P172 Source Tree

The repository has been bootstrapped using the GitHub connector, but that connector only supports text-file writes and cannot extract the local ZIP directly into GitHub. To import every original file without changing content, use the P172 ZIP as the source bundle.

## Windows PowerShell

1. Download `VidGravity_P172_Production_Deployment_Bundle_v1.zip` from the project Library.
2. Extract it. The actual project files are inside `VidGravity_P172_Production_Deployment_Bundle_v1\`.
3. Open PowerShell and run these commands, adjusting the ZIP/extraction path if needed:

```powershell
git clone https://github.com/revanthbesty/VidGravity.git
cd VidGravity
# Copy the contents inside the extracted VidGravity_P172_Production_Deployment_Bundle_v1 folder
# into this repository folder. Do not copy the outer wrapper folder itself.
# Review changes and confirm no secrets or user data are included:
git status --short
git diff --stat
git add .
git commit -m "Import VidGravity P172 source bundle"
git push origin main
```

If Git says a file would overwrite a locally modified file, stop and inspect the difference instead of using force.

## Before pushing

- Confirm `.env`, production credentials, payment secrets, signing keys, databases, caches, and downloaded media are excluded.
- Preserve original folders such as `android-web/`, `tests/`, and `.github/workflows/`.
- Make sure the import adds the actual files (`server.py`, `app.js`, `index.html`, test modules, Android wrapper, deployment configs), not only the ZIP.
- CI on this bootstrap commit checks repository metadata only. The complete regression workflow should be enabled and tested after the full source tree is present.

## Verification

After push, verify that these paths appear at the root of the GitHub repository: `server.py`, `app.js`, `index.html`, `requirements.txt`, `tests/`, `android-web/`, and `.github/workflows/vidgravity-ci.yml`.

This repository bootstrap alone is not a deployment and is not a production-readiness claim.
