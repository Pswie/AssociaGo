import test from 'node:test';
import assert from 'node:assert/strict';
import { accessAllows, pagesForAccess, editableAccess, changeModulePermission } from '../src/renderer/src/suite-access.js';
const access = permissions => ({status:'active', applications:{associago:{enabled:true}}, permissions});
test('only explicit granted pages are visible and unknown pages stay closed', () => {
  const pages=[{id:'members'},{id:'fiscal'},{id:'projects'},{id:'unknown'},{id:'manual'}];
  assert.deepEqual(pagesForAccess(pages,access(['associago.members.read'])).map(p=>p.id),['members','manual']);
});
test('a revoked old role never keeps a module visible', () => {
  const old=access(['associago.members.read']); old.status='suspended';
  assert.equal(accessAllows(old,'members'),false);
  assert.deepEqual(pagesForAccess([{id:'members'}],old),[]);
});

test('legacy voucher operations remain visible only to the protected owner', () => {
  const viewer=access(['associago.finance.read']);
  assert.deepEqual(pagesForAccess([{id:'coupons'}],viewer),[]);
  assert.deepEqual(pagesForAccess([{id:'coupons'}],{...viewer,owner:true}).map(p=>p.id),['coupons']);
  assert.deepEqual(pagesForAccess([{id:'coupons'}],{...viewer,owner:true,status:'suspended'}),[]);
});
test('writing requires an explicit write permission', () => {
  assert.equal(accessAllows(access(['associago.members.read']),'members','write'),false);
  const next=changeModulePermission({role:'none',permissions:[]},'associago.members.write',true);
  assert.deepEqual(next.permissions,['associago.members.read','associago.members.write']);
  assert.deepEqual(changeModulePermission(next,'associago.members.read',false).permissions,[]);
});
test('configured choices remain available while the effective access is suspended', () => {
  const result=editableAccess({version:3,status:'suspended',applications:{},configuredApplications:{bandi:{role:'custom',permissions:['bandi.sources.read']}}});
  assert.equal(result.version,3);assert.equal(result.status,'suspended');
  assert.deepEqual(result.applications.bandi.permissions,['bandi.sources.read']);
});
test('preset grants are recomputed by the authority rather than forwarded as custom choices', () => {
  const result=editableAccess({version:1,status:'active',configuredApplications:{bandi:{role:'viewer',permissions:['bandi.sources.read']}}});
  assert.deepEqual(result.applications.bandi,{role:'viewer',permissions:[]});
});
