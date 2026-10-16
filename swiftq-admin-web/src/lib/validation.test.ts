import { test } from "node:test";
import assert from "node:assert/strict";
import {
  DEFAULT_QUEUE_CONFIG,
  configToForm,
  isValidEmail,
  validateClinicForm,
  validateDateRange,
  validateQueueConfig,
  validateServicePointForm,
  validateStaffForm,
} from "./validation";
import { toRows, toEntries, summariseHours } from "./hours";

test("email check", () => {
  assert.ok(isValidEmail("nurse@clinic.co.za"));
  assert.ok(!isValidEmail("nurse@clinic"));
  assert.ok(!isValidEmail("nurse clinic.co.za"));
});

test("staff form: create needs every field", () => {
  const errs = validateStaffForm({ email: "", displayName: "", role: "", clinicId: "" }, "create");
  assert.deepEqual(Object.keys(errs).sort(), ["clinicId", "displayName", "email", "role"]);
});

test("staff form: edit does not ask for an email", () => {
  const errs = validateStaffForm({ email: "", displayName: "Thandi", role: "nurse", clinicId: "c1" }, "edit");
  assert.deepEqual(errs, {});
});

test("staff form: name over 60 characters is rejected", () => {
  const errs = validateStaffForm({ email: "a@b.co", displayName: "x".repeat(61), role: "nurse", clinicId: "c1" }, "create");
  assert.ok(errs.displayName);
});

test("clinic form: needs one open day and closing after opening", () => {
  const closed = toRows(undefined).map((r) => ({ ...r, open: false }));
  assert.ok(validateClinicForm({ name: "A", address: "B", hours: closed }).hours);

  const rows = toRows(undefined);
  rows[0] = { ...rows[0], opens: "16:00", closes: "07:00" };
  assert.ok(validateClinicForm({ name: "A", address: "B", hours: rows })["hours.mon"]);
  assert.deepEqual(validateClinicForm({ name: "A", address: "B", hours: toRows(undefined) }), {});
});

test("service point form: servers must be 1 to 20", () => {
  const base = { name: "General", icon: "stethoscope" };
  assert.deepEqual(validateServicePointForm({ ...base, servers: "1" }), {});
  assert.ok(validateServicePointForm({ ...base, servers: "0" }).servers);
  assert.ok(validateServicePointForm({ ...base, servers: "21" }).servers);
  assert.ok(validateServicePointForm({ ...base, servers: "2.5" }).servers);
});

test("queue config: defaults pass and round trip", () => {
  const result = validateQueueConfig(configToForm(DEFAULT_QUEUE_CONFIG));
  assert.deepEqual(result.errors, {});
  assert.deepEqual(result.value, DEFAULT_QUEUE_CONFIG);
});

test("queue config: enforces the contract's minimums", () => {
  const form = configToForm(DEFAULT_QUEUE_CONFIG);
  const { errors, value } = validateQueueConfig({
    ...form,
    starvationMinutes: "0",
    baseSeconds: "59",
    emaAlpha: "1.5",
    elderly: "-1",
    minSamples: "0",
  });
  assert.equal(value, undefined);
  assert.deepEqual(Object.keys(errors).sort(), ["baseSeconds", "elderly", "emaAlpha", "minSamples", "starvationMinutes"]);
});

test("date range: end before start, and more than 92 days", () => {
  assert.equal(validateDateRange("2026-10-01", "2026-10-07"), null);
  assert.ok(validateDateRange("2026-10-07", "2026-10-01"));
  assert.ok(validateDateRange("2026-01-01", "2026-06-01"));
  assert.equal(validateDateRange("2026-01-01", "2026-04-02"), null); // 92 days counting both ends
  assert.ok(validateDateRange("2026-01-01", "2026-04-03"));
  assert.ok(validateDateRange("", "2026-04-03"));
});

test("opening hours: summary groups days with the same hours", () => {
  assert.equal(summariseHours(toEntries(toRows(undefined))), "Mon to Fri 07:00 to 16:00");
  assert.equal(
    summariseHours([
      { day: "mon", opens: "07:00", closes: "16:00" },
      { day: "tue", opens: "07:00", closes: "16:00" },
      { day: "sat", opens: "08:00", closes: "12:00" },
    ]),
    "Mon to Tue 07:00 to 16:00; Sat 08:00 to 12:00",
  );
  assert.equal(summariseHours([]), "No opening hours set");
});
