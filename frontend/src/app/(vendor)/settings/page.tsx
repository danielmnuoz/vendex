"use client";

import { Save } from "lucide-react";
import { useState, type FormEvent } from "react";

import { ErrorState, LoadingState, PageHeader } from "@/components/primitives";
import { useResource } from "@/hooks/use-resource";
import { api, messageFor } from "@/lib/api";
import type { NotificationPreferences, Profile } from "@/lib/contracts";

async function loadSettings() {
  const [profile, preferences] = await Promise.all([api<Profile>("/profile"), api<NotificationPreferences>("/notifications/preferences")]);
  return { profile, preferences };
}

export default function SettingsPage() {
  const resource = useResource(loadSettings, []);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  if (resource.loading) return <LoadingState label="Loading account settings…" />;
  if (resource.error || !resource.data) return <ErrorState error={resource.error} onRetry={resource.reload} />;
  const { profile, preferences } = resource.data;

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(""); setMessage("");
    const form = new FormData(event.currentTarget);
    try { await api<Profile>("/profile", { method: "PATCH", body: JSON.stringify({ shopName: form.get("shopName"), city: form.get("city"), state: form.get("state") }) }); setMessage("Shop profile updated."); resource.reload(); } catch (reason) { setError(messageFor(reason)); } finally { setBusy(false); }
  }

  async function savePreferences(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(""); setMessage("");
    const form = new FormData(event.currentTarget);
    try { await api<NotificationPreferences>("/notifications/preferences", { method: "PATCH", body: JSON.stringify({ inAppEnabled: form.get("inAppEnabled") === "on", emailEnabled: form.get("emailEnabled") === "on", overlapBuyListEnabled: form.get("overlapBuyListEnabled") === "on", overlapLiquidateEnabled: form.get("overlapLiquidateEnabled") === "on", savedOverlapActiveEnabled: form.get("savedOverlapActiveEnabled") === "on", digestMode: form.get("digestMode") }) }); setMessage("Notification preferences updated."); resource.reload(); } catch (reason) { setError(messageFor(reason)); } finally { setBusy(false); }
  }

  return <><PageHeader eyebrow="Workspace" title="Settings" description="Keep your public shop summary accurate and control which overlap changes reach you." />{message ? <p className="form-success" role="status">{message}</p> : null}{error ? <p className="form-error" role="alert">{error}</p> : null}<div className="settings-grid"><form className="panel settings-panel" onSubmit={saveProfile}><div><p className="eyebrow">Public vendor summary</p><h2>Shop profile</h2><p className="form-help">Email stays private. Shop name and location decorate authorized market views.</p></div><label className="field"><span>Email</span><input value={profile.email} disabled /></label><label className="field"><span>Shop name</span><input name="shopName" required maxLength={120} defaultValue={profile.shopName} /></label><div className="field-grid two"><label className="field"><span>City</span><input name="city" maxLength={120} defaultValue={profile.city} /></label><label className="field"><span>State</span><input name="state" maxLength={64} defaultValue={profile.state} /></label></div><button className="button primary icon-label" disabled={busy}><Save size={16} /> Save profile</button></form><form className="panel settings-panel" onSubmit={savePreferences}><div><p className="eyebrow">Delivery policy</p><h2>Notifications</h2><p className="form-help">Email transport is not connected yet; the preference is stored for that later channel.</p></div><div className="toggle-list"><label><input name="inAppEnabled" type="checkbox" defaultChecked={preferences.inAppEnabled} /><span><strong>In-app notifications</strong><small>Show live activity in VenDex.</small></span></label><label><input name="emailEnabled" type="checkbox" defaultChecked={preferences.emailEnabled} /><span><strong>Email notifications</strong><small>Remember this choice for the later email transport.</small></span></label><label><input name="overlapBuyListEnabled" type="checkbox" defaultChecked={preferences.overlapBuyListEnabled} /><span><strong>Buy-list overlaps</strong><small>Notify when another vendor can fill your demand.</small></span></label><label><input name="overlapLiquidateEnabled" type="checkbox" defaultChecked={preferences.overlapLiquidateEnabled} /><span><strong>Liquidation overlaps</strong><small>Notify when another vendor wants priority stock.</small></span></label><label><input name="savedOverlapActiveEnabled" type="checkbox" defaultChecked={preferences.savedOverlapActiveEnabled} /><span><strong>Saved-plan changes</strong><small>Notify when saved opportunities become active.</small></span></label></div><label className="field"><span>Digest mode</span><select name="digestMode" defaultValue={preferences.digestMode}><option value="real_time">Real time</option><option value="daily">Daily digest</option><option value="event_only">Event days only</option></select></label><button className="button primary icon-label" disabled={busy}><Save size={16} /> Save preferences</button></form></div></>;
}
