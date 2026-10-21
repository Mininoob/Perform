# Perform website

The landing page for Perform, built with Next.js and meant to be deployed on Vercel.

## Run it locally

```bash
npm install
npm run dev
```

Then open http://localhost:3000.

## Deploy on Vercel

1. Import the `Mininoob/Perform` repository in Vercel.
2. Set **Root Directory** to `website`. Vercel picks up Next.js from there.
3. Optionally set these environment variables:
   - `NEXT_PUBLIC_SITE_URL`: the site's public address, used for link previews (for example `https://perform.app`).
   - `WAITLIST_WEBHOOK_URL`: where waitlist sign-ups are sent. Each sign-up is a JSON `POST` with `email`, `source` and `at`. Anything that accepts a webhook works: a mailing-list service, a form backend, or a spreadsheet script. Without it, the form tells visitors the waitlist isn't on yet.

## Downloads

The download buttons read the latest GitHub release of `Mininoob/Perform` (checked at most once an hour). Attach these to a release and the buttons switch on by themselves:

- an `.apk` for the phone app,
- an `.msi` or `.exe` for Perform Library on Windows,
- a `.dmg` for Perform Library on macOS.

Until a release exists, the buttons point at the Releases page and the source instead.

## What's on the page

The books shown are public-domain classics in Perform's own typeset covers, and the phone and desktop screens are drawn in code from the apps' design (`../DESIGN.md`). The type is Literata (SIL Open Font License, see `app/fonts/OFL.txt`), subset to Latin and self-hosted.
