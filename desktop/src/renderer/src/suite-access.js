/** UI visibility only: server authorization is evaluated on every API request. */
const PAGE_MODULES = Object.freeze({ dashboard: 'dashboard', fiscal: 'finance', 'expense-documents': 'finance', budget: 'finance', balance: 'finance', members: 'members', activities: 'activities', events: 'events', attendance: 'attendance', volunteers: 'volunteers', projects: 'projects', inventory: 'inventory', communications: 'communications', certificates: 'certificates', assemblies: 'assemblies', settings: 'settings' });
export function accessAllows(access, module, action = 'read') {
  return access?.status === 'active' && access.applications?.associago?.enabled === true
    && access.permissions?.includes(`associago.${module}.${action}`) === true;
}
export function pagesForAccess(pages, access) {
  return pages.filter(page => page.id === 'coupons' ? access?.owner === true && accessAllows(access, 'finance')
    : page.id === 'manual' ? access?.status === 'active' && access.applications?.associago?.enabled === true
    : accessAllows(access, PAGE_MODULES[page.id]));
}
export function editableAccess(user) {
  const configured = user.configuredApplications ?? user.applications;
  return { version: user.version, status: user.status, applications: Object.fromEntries(
    ['associago', 'librepm', 'bandi'].map(app => [app, { role: configured?.[app]?.role ?? 'none', permissions: configured?.[app]?.role === 'custom' ? [...(configured?.[app]?.permissions ?? [])] : [] }]),
  ) };
}
export function changeModulePermission(application, permission, checked) {
  const permissions = new Set(application.permissions ?? []);
  const prefix = permission.slice(0, permission.lastIndexOf('.') + 1);
  if (checked) { permissions.add(permission); if (permission.endsWith('.write')) permissions.add(prefix + 'read'); }
  else { permissions.delete(permission); if (permission.endsWith('.read')) permissions.delete(prefix + 'write'); }
  return { role: 'custom', permissions: [...permissions].sort() };
}
