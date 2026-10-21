import { NextResponse } from "next/server";

/**
 * Waitlist sign-ups. The address is forwarded as JSON to WAITLIST_WEBHOOK_URL (any
 * service that accepts a webhook: a mailing list, a form backend, a spreadsheet). With no
 * webhook configured, the form says so honestly instead of pretending it worked.
 */
export async function POST(request: Request) {
  let body: { email?: unknown; company?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "That didn't look like a sign-up." }, { status: 400 });
  }

  // A filled hidden field means a bot; answer as if it worked.
  if (typeof body.company === "string" && body.company.trim() !== "") {
    return NextResponse.json({ ok: true });
  }

  const email = typeof body.email === "string" ? body.email.trim() : "";
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
    return NextResponse.json({ error: "That email address doesn't look right." }, { status: 422 });
  }

  const webhook = process.env.WAITLIST_WEBHOOK_URL;
  if (!webhook) {
    return NextResponse.json(
      { error: "The waitlist isn't switched on yet. Watch the GitHub repo for releases in the meantime." },
      { status: 503 },
    );
  }

  try {
    const response = await fetch(webhook, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, source: "perform-website", at: new Date().toISOString() }),
    });
    if (!response.ok) throw new Error(String(response.status));
  } catch {
    return NextResponse.json({ error: "That didn't go through. Try again in a moment." }, { status: 502 });
  }

  return NextResponse.json({ ok: true });
}
