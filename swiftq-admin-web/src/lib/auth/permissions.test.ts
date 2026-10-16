import { test } from "node:test";
import assert from "node:assert/strict";
import { assignableRoles, canAccess, isAdminAppRole } from "./permissions";

test("only IT admins and managers may use the admin app", () => {
  assert.ok(isAdminAppRole("it_admin"));
  assert.ok(isAdminAppRole("manager"));
  assert.ok(!isAdminAppRole("nurse"));
  assert.ok(!isAdminAppRole("receptionist"));
  assert.ok(!isAdminAppRole("patient"));
});

test("IT admins and managers can open every screen", () => {
  for (const section of ["staff", "clinics", "queue-config", "sync", "audit", "analytics"] as const) {
    assert.ok(canAccess("it_admin", section) && canAccess("manager", section));
  }
  assert.ok(!canAccess("nurse", "staff"));
});

test("managers can only give the receptionist and nurse roles", () => {
  assert.deepEqual(assignableRoles("manager"), ["receptionist", "nurse"]);
  assert.ok(assignableRoles("it_admin").includes("it_admin"));
});
