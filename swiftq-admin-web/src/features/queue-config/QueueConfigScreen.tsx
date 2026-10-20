"use client";

import { useCallback, useEffect, useState } from "react";
import { QueueConfigForm } from "./QueueConfigForm";
import { SelectField, type Option } from "@/components/ui/Field";
import { EmptyState, ErrorPanel, LoadingBlock } from "@/components/ui/States";
import { fetchAll, getQueueConfig, listClinics, listServicePoints } from "@/lib/api/endpoints";
import { useLoad } from "@/lib/hooks";

export function QueueConfigScreen() {
  const [clinicId, setClinicId] = useState("");
  const [servicePointId, setServicePointId] = useState("");

  const loadClinics = useCallback(() => fetchAll(listClinics), []);
  const clinics = useLoad(loadClinics);

  const loadPoints = useCallback(async () => (clinicId ? (await listServicePoints(clinicId)).items : []), [clinicId]);
  const points = useLoad(loadPoints);

  // Changing clinic clears the service point, because the old one belongs to a different clinic.
  useEffect(() => setServicePointId(""), [clinicId]);

  const servicePoint = points.data?.find((p) => p.id === servicePointId) ?? null;
  const servicePointKey = servicePoint?.id ?? null;

  const loadConfig = useCallback(async () => (servicePointKey ? await getQueueConfig(servicePointKey) : null), [servicePointKey]);
  const config = useLoad(loadConfig);

  const clinicOptions: Option[] = (clinics.data ?? []).filter((c) => c.is_active !== false).map((c) => ({ value: c.id, label: c.name }));
  const pointOptions: Option[] = (points.data ?? []).map((p) => ({ value: p.id, label: p.is_active ? p.name : `${p.name} (deactivated)` }));

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Queue settings</h1>
          <p>Choose a service point to change how its queue orders patients and estimates waiting times. Changes apply to each new day&apos;s queue. Today&apos;s queue keeps the settings it started with.</p>
        </div>
      </div>

      {clinics.error && <ErrorPanel message={clinics.error} onRetry={() => void clinics.reload()} />}
      {!clinics.error && (
        <div className="filters">
          <SelectField label="Clinic" value={clinicId} options={clinicOptions} placeholder={clinics.loading ? "Loading clinics" : "Choose a clinic"} onChange={(e) => setClinicId(e.target.value)} />
          <SelectField
            label="Service point"
            value={servicePointId}
            options={pointOptions}
            placeholder={!clinicId ? "Choose a clinic first" : points.loading ? "Loading" : "Choose a service point"}
            disabled={!clinicId || points.loading}
            onChange={(e) => setServicePointId(e.target.value)}
          />
        </div>
      )}
      {points.error && <ErrorPanel message={points.error} onRetry={() => void points.reload()} />}

      {!servicePoint ? (
        <div className="card">
          <EmptyState title="Choose a service point">Pick a clinic and a service point above to see its queue settings.</EmptyState>
        </div>
      ) : config.loading ? (
        <LoadingBlock label="Loading queue settings" />
      ) : config.error ? (
        <ErrorPanel message={config.error} onRetry={() => void config.reload()} />
      ) : config.data ? (
        // key = service point id, so switching service point starts a fresh form
        <QueueConfigForm key={servicePointKey} servicePointId={servicePoint.id} initial={config.data} onSaved={() => undefined} />
      ) : null}
    </>
  );
}
