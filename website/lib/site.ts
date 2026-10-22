export const site = {
  name: "Perform",
  url: process.env.NEXT_PUBLIC_SITE_URL ?? "https://perform-mininoob.vercel.app",
  repo: "https://github.com/Mininoob/Perform",
  releases: "https://github.com/Mininoob/Perform/releases",
  description:
    "A free ebook reader for foldable Android phones, for when you want the cover to be seen. Close the phone and the outside screen shows your book's cover. Open it and you're back on your page. Your own computer keeps your library and shares it privately.",
};

/** Sign-ups only show when there's somewhere to send them. */
export const waitlistOpen = Boolean(process.env.WAITLIST_WEBHOOK_URL);

export interface Release {
  tag: string;
  url: string;
  apk?: string;
  windows?: string;
  mac?: string;
}

/**
 * The newest GitHub release and the download each platform needs, if one has been
 * published. Checked at most once an hour. A download button only appears for a file
 * that exists; until then the page says when it will.
 */
export async function latestRelease(): Promise<Release | null> {
  try {
    const response = await fetch("https://api.github.com/repos/Mininoob/Perform/releases/latest", {
      headers: { Accept: "application/vnd.github+json" },
      next: { revalidate: 3600 },
    });
    if (!response.ok) return null;
    const json = (await response.json()) as {
      tag_name: string;
      html_url: string;
      assets?: { name: string; browser_download_url: string }[];
    };
    const assets = json.assets ?? [];
    const find = (pattern: RegExp) => assets.find((a) => pattern.test(a.name))?.browser_download_url;
    return {
      tag: json.tag_name,
      url: json.html_url,
      apk: find(/\.apk$/i),
      windows: find(/\.(msi|exe)$/i),
      mac: find(/\.dmg$/i),
    };
  } catch {
    return null;
  }
}
