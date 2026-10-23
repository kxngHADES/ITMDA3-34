"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { SelectField, TextField } from "@/components/ui/Field";
import { EmptyState, ErrorPanel, LoadingBlock } from "@/components/ui/States";
import { useToast } from "@/components/ui/Toast";
import { BarChart, HorizontalBars } from "@/components/charts/Charts";
import { downloadAnalyticsCsv, fetchAll, getAnalytics, listClinics, listStaff } from "@/lib/api/endpoints";
import type { AnalyticsSnapshot } from "@/lib/api/types";
import { saveBlob } from "@/lib/dom";
import { errorMessage } from "@/lib/errors";
import { daysAgoYmd, formatMinutes, formatShortDate, shortId, toYmd } from "@/lib/format";
import { useLoad } from "@/lib/hooks";
import { validateDateRange } from "@/lib/validation";
import { summarise } from "./summary";

const CHANNEL_LABEL: Record<string, string> = { app: "App", sms: "SMS", staff_assisted: "Added by staff" };

export function AnalyticsScreen() {
  const toast = useToast();
  const [clinicId, setClinicId] = useState("");
  const [from, setFrom] = useState(() => daysAgoYmd(6));
  const [to, setTo] = useState(() => toYmd(new Date()));
  const [rangeError, setRangeError] = useState<string | null>(null);

  const [items, setItems] = useState<AnalyticsSnapshot[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const request = useRef(0);

  const loadClinics = useCallback(() => fetchAll(listClinics), []);
  const clinics = useLoad(loadClinics);

  // Pick the first clinic once they have loaded, so the page opens with data.
  useEffect(() => {
    if (!clinicId && clinics.data && clinics.data.length > 0) setClinicId(clinics.data[0].id);
  }, [clinics.data, clinicId]);

  const run = useCallback(async () => {
    const problem = validateDateRange(from, to);
    setRangeError(problem);
    if (problem || !clinicId) return;
    const id = ++request.current;
    setLoading(true);
    setError(null);
    try {
      const result = await getAnalytics(clinicId, from, to);
      if (id === request.current) setItems(result.items);
    } catch (e) {
      if (id === request.current) setError(errorMessage(e));
    } finally {
      if (id === request.current) setLoading(false);
    }
  }, [clinicId, from, to]);

  // Load once when the clinic is first chosen. After that, "Show results" reloads on demand.
  const firstLoad = useRef(true);
  useEffect(() => {
    if (clinicId && firstLoad.current) {
      firstLoad.current = false;
      void run();
    }
  }, [clinicId, run]);

  async function exportCsv() {
    const problem = validateDateRange(from, to);
    setRangeError(problem);
    if (problem || !clinicId) return;
    setExporting(true);
    try {
      const file = await downloadAnalyticsCsv(clinicId, from, to);
      saveBlob(file.blob, file.filename ?? `swiftq-analytics-${shortId(clinicId)}-${from}-to-${to}.csv`);
      toast.success("Your CSV file was downloaded.");
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setExporting(false);
    }
  }

  // Staff names turn the ids in the analytics into people. If this fails we simply show ids.
  const loadStaff = useCallback(
    async () => (clinicId ? (await listStaff({ clinic_id: clinicId, include_inactive: true })).items : []),
    [clinicId],
  );
  const staff = useLoad(loadStaff);
  const staffNames = useMemo(() => new Map((staff.data ?? []).map((p) => [p.id, p.display_name])), [staff.data]);

  const summary = useMemo(() => (items ? summarise(items) : null), [items]);
  const sorted = useMemo(() => [...(items ?? [])].sort((a, b) => a.date.localeCompare(b.date)), [items]);
  const labels = sorted.map((s) => formatShortDate(s.date));
  const peakRows = (summary?.peakHourDays ?? [])
    .map((days, hour) => ({ hour, days }))
    .filter((r) => r.days > 0)
    .map((r) => ({ label: `${String(r.hour).padStart(2, "0")}:00`, value: r.days }));

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Analytics</h1>
          <p>See how busy a clinic was, how long people waited and how staff worked. Choose up to 92 days.</p>
        </div>
        <Button size="lg" loading={exporting} disabled={!clinicId} onClick={() => void exportCsv()}>Export CSV</Button>
      </div>

      <form className="filters" onSubmit={(e) => { e.preventDefault(); void run(); }} noValidate>
        <SelectField
          label="Clinic"
          value={clinicId}
          options={(clinics.data ?? []).map((c) => ({ value: c.id, label: c.name }))}
          placeholder={clinics.loading ? "Loading clinics" : "Choose a clinic"}
          onChange={(e) => setClinicId(e.target.value)}
        />
        <TextField label="From" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
        <TextField label="To" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} error={rangeError ?? undefined} />
        <Button type="submit" variant="primary" size="lg" loading={loading} disabled={!clinicId}>Show results</Button>
      </form>

      {clinics.error && <ErrorPanel message={clinics.error} onRetry={() => void clinics.reload()} />}

      {loading && !items ? (
        <LoadingBlock label="Loading analytics" />
      ) : error ? (
        <ErrorPanel message={error} onRetry={() => void run()} />
      ) : !summary || sorted.length === 0 ? (
        <div className="card"><EmptyState title={items ? "No data for these dates" : "Choose a clinic and dates"}>{items ? "Try a longer range, or a clinic that was open in this period." : "Then select Show results."}</EmptyState></div>
      ) : (
        <div className="stack-lg" aria-busy={loading}>
          <div className="card">
            <div className="stat-grid">
              <div className="stat"><span className="value">{summary.joined}</span><span className="label">Patients joined</span></div>
              <div className="stat"><span className="value">{summary.served}</span><span className="label">Patients served</span></div>
              <div className="stat"><span className="value">{formatMinutes(summary.avgWaitMinutes)}</span><span className="label">Average wait</span></div>
              <div className="stat"><span className="value">{summary.noShows}</span><span className="label">No-shows</span></div>
              <div className="stat"><span className="value">{summary.cancellations}</span><span className="label">Cancellations</span></div>
              <div className="stat"><span className="value">{formatMinutes(summary.worstP90Minutes)}</span><span className="label">Longest 90th percentile wait</span></div>
            </div>
            <p className="field-help" style={{ marginTop: "var(--sq-space-4)" }}>Average wait counts each day by how many patients were served that day.</p>
          </div>

          <div className="card">
            <BarChart
              title="Patients joined and served each day"
              labels={labels}
              series={[
                { name: "Joined", values: sorted.map((s) => s.tickets_joined ?? 0), tone: "primary" },
                { name: "Served", values: sorted.map((s) => s.tickets_served ?? 0), tone: "secondary" },
              ]}
            />
          </div>

          <div className="card">
            <BarChart
              title="Average wait each day (minutes)"
              labels={labels}
              series={[{ name: "Average wait", values: sorted.map((s) => Math.round((s.avg_wait_minutes ?? 0) * 10) / 10), tone: "primary" }]}
            />
          </div>

          <div className="two-col">
            <div className="card">
              <HorizontalBars title="Busiest hour (days each hour was the peak)" rows={peakRows} empty="No peak hours were recorded." />
            </div>
            <div className="card">
              <HorizontalBars
                title="How patients joined"
                rows={summary.channels.map((c) => ({ label: CHANNEL_LABEL[c.channel] ?? c.channel, value: c.joins }))}
                empty="No joins were recorded."
              />
            </div>
          </div>

          <div className="card">
            <HorizontalBars
              title="Patients called by each staff member"
              rows={summary.callsByStaff.map((s) => ({ label: staffNames.get(s.staffId) ?? shortId(s.staffId), value: s.calls, hint: s.staffId }))}
              empty="No calls were recorded."
            />
            <p className="field-help" style={{ marginTop: "var(--sq-space-3)" }}>Staff who can't be found are shown by id.</p>
          </div>
        </div>
      )}
    </>
  );
}
