# Security policy

## Reporting a problem

Please **don't open a public issue** for a security problem. Use GitHub's private report
instead: open the **Security** tab of this repository and choose **Report a vulnerability**.
Only the people who look after this project can see it.

Helpful to include: what the problem is and where (a screen, a file, a line), how to make it
happen step by step, and what someone could do with it.

## What is covered

- The Android app (`app/`) and the iPhone version (the `apple` branch).
- The friends server (`server/`) behind https://jasonagibson.zapto.org/game/api/.
- The release and deploy scripts.

## How it is protected

- The app checks every self-update against the SHA-256 the release published before Android
  is asked to install it, and Android still asks the person first.
- The friends server keeps no names, emails or locations: only garden codes, pets and hearts,
  with a random secret token per player.
- Every change is scanned for leaked secrets (gitleaks) and checked by semgrep, trivy,
  osv-scanner, detekt (Kotlin), clippy (Rust) and others; see `.github/workflows/`. Dependabot
  opens update pull requests every week.
