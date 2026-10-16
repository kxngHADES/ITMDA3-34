"use client";

import { useCallback, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { EmptyState, ErrorPanel, LoadingBlock } from "@/components/ui/States";
import { useToast } from "@/components/ui/Toast";
import { fetchAll, listClinics, listServicePoints } from "@/lib/api/endpoints";
import type { ServicePoint } from "@/lib/api/types";
import { summariseHours } from "@/lib/hours";
import { useLoad } from "@/lib/hooks";
import { ClinicFormModal } from "./ClinicFormModal";
import { ServicePointFormModal } from "./ServicePointFormModal";

export function ClinicsScreen() {
  const toast = useToast();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [clinicModal, setClinicModal] = useState<"new" | null>(null);
  const [spModal, setSpModal] = useState<"new" | null>(null);

  const loadClinics = useCallback(() => fetchAll(listClinics), []);
  const clinics = useLoad(loadClinics);
  const selected = clinics.data?.find((c) => c.id === selectedId) ?? null;

  const loadPoints = useCallback(async () => {
    if (!selectedId) return [] as ServicePoint[];
    return (await listServicePoints(selectedId)).items;
  }, [selectedId]);
  const points = useLoad(loadPoints);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Clinics</h1>
          <p>Set up each clinic, its opening hours and the service points patients can queue for. Editing or deactivating a clinic or service point is not available yet.</p>
        </div>
        <Button variant="primary" size="lg" onClick={() => setClinicModal("new")}>Add clinic</Button>
      </div>

      {clinics.loading ? (
        <LoadingBlock label="Loading clinics" />
      ) : clinics.error ? (
        <ErrorPanel message={clinics.error} onRetry={() => void clinics.reload()} />
      ) : (clinics.data ?? []).length === 0 ? (
        <div className="card">
          <EmptyState title="No clinics yet" action={<Button variant="primary" onClick={() => setClinicModal("new")}>Add clinic</Button>}>
            Add the first clinic, then add its service points.
          </EmptyState>
        </div>
      ) : (
        <div className="stack-lg">
          <div className="table-wrap">
            <table>
              <caption className="visually-hidden">Clinics</caption>
              <thead>
                <tr>
                  <th scope="col">Clinic</th>
                  <th scope="col">Opening hours</th>
                  <th scope="col">Status</th>
                  <th scope="col"><span className="visually-hidden">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {(clinics.data ?? []).map((clinic) => {
                  const active = clinic.is_active !== false;
                  return (
                    <tr key={clinic.id} className={clinic.id === selectedId ? "is-selected" : undefined}>
                      <td>
                        <div className="primary">{clinic.name}</div>
                        <div className="secondary">{clinic.address}</div>
                      </td>
                      <td>{summariseHours(clinic.opening_hours)}</td>
                      <td>
                        {!active ? <Chip tone="neutral">Deactivated</Chip> : clinic.is_open ? <Chip tone="success">Open now</Chip> : <Chip tone="info">Closed now</Chip>}
                      </td>
                      <td>
                        <div className="actions">
                          <Button variant={clinic.id === selectedId ? "primary" : "secondary"} onClick={() => setSelectedId(clinic.id)} aria-label={`Show service points for ${clinic.name}`}>
                            Service points
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {selected && (
            <section aria-labelledby="sp-heading" className="stack">
              <div className="row-between">
                <h2 id="sp-heading" className="sq-text-title-3">Service points at {selected.name}</h2>
                <Button variant="primary" onClick={() => setSpModal("new")}>Add service point</Button>
              </div>
              {points.loading ? (
                <LoadingBlock label="Loading service points" />
              ) : points.error ? (
                <ErrorPanel message={points.error} onRetry={() => void points.reload()} />
              ) : (points.data ?? []).length === 0 ? (
                <div className="card">
                  <EmptyState title="No service points yet">Patients can&apos;t join a queue here until you add at least one.</EmptyState>
                </div>
              ) : (
                <div className="table-wrap">
                  <table>
                    <caption className="visually-hidden">Service points at {selected.name}</caption>
                    <thead>
                      <tr>
                        <th scope="col">Name</th>
                        <th scope="col">Icon</th>
                        <th scope="col">Serving at once</th>
                        <th scope="col">Today&apos;s queue</th>
                        <th scope="col">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(points.data ?? []).map((sp) => (
                        <tr key={sp.id}>
                          <td className="primary">{sp.name}</td>
                          <td>{sp.icon ?? "None"}</td>
                          <td className="mono">{sp.concurrent_servers ?? 1}</td>
                          <td>{sp.queue_id ? <Chip tone="info">Open</Chip> : <Chip tone="neutral">Not opened today</Chip>}</td>
                          <td>{sp.is_active ? <Chip tone="success">Active</Chip> : <Chip tone="neutral">Deactivated</Chip>}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          )}
        </div>
      )}

      <ClinicFormModal
        open={clinicModal !== null}
        clinic={null}
        onClose={() => setClinicModal(null)}
        onSaved={(message, saved) => {
          toast.success(message);
          setClinicModal(null);
          if (clinicModal === "new") setSelectedId(saved.id);
          void clinics.reload();
        }}
      />
      {selectedId && (
        <ServicePointFormModal
          open={spModal !== null}
          clinicId={selectedId}
          servicePoint={null}
          onClose={() => setSpModal(null)}
          onSaved={(message) => {
            toast.success(message);
            setSpModal(null);
            void points.reload();
          }}
        />
      )}
    </>
  );
}
