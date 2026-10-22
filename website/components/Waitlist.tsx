"use client";

import { useState } from "react";
import styles from "./Waitlist.module.css";

type State = { kind: "idle" } | { kind: "sending" } | { kind: "done" } | { kind: "error"; message: string };

/** One field: an email address, for a note when Perform reaches Google Play. */
export function Waitlist() {
  const [state, setState] = useState<State>({ kind: "idle" });

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setState({ kind: "sending" });
    try {
      const response = await fetch("/api/waitlist", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: form.get("email"), company: form.get("company") }),
      });
      const json = (await response.json().catch(() => ({}))) as { error?: string };
      if (!response.ok) {
        setState({ kind: "error", message: json.error ?? "That didn't go through. Try again in a moment." });
        return;
      }
      setState({ kind: "done" });
    } catch {
      setState({ kind: "error", message: "Couldn't reach the server. Check your connection and try again." });
    }
  }

  if (state.kind === "done") {
    return (
      <p className={styles.done} role="status">
        You&rsquo;re on the list. You&rsquo;ll get one email when <em>Perform</em> lands on Google Play. Nothing else. I
        respect your inbox.
      </p>
    );
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate={false}>
      <label htmlFor="waitlist-email" className={styles.label}>
        Get one email when <em>Perform</em> lands on Google Play. I&rsquo;m not like other apps.
      </label>
      <div className={styles.row}>
        <input
          id="waitlist-email"
          name="email"
          type="email"
          required
          autoComplete="email"
          inputMode="email"
          placeholder="you@example.com"
          className={styles.input}
          aria-invalid={state.kind === "error"}
          aria-describedby={state.kind === "error" ? "waitlist-error" : undefined}
        />
        {/* Left empty by people; filled by bots. */}
        <input type="text" name="company" tabIndex={-1} autoComplete="off" className={styles.trap} aria-hidden="true" />
        <button type="submit" className="button button-primary" disabled={state.kind === "sending"}>
          {state.kind === "sending" ? "Adding…" : "Add me"}
        </button>
      </div>
      {state.kind === "error" ? (
        <p id="waitlist-error" className={styles.error} role="alert">
          {state.message}
        </p>
      ) : null}
    </form>
  );
}
