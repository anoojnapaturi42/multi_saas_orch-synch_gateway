import type { Execution, ExecutionDetail, ExecutionFilters } from './types'

const json = async <T>(url: string, init?: RequestInit): Promise<T> => {
  const response = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...init })
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
  return response.json()
}

export async function getExecutions(filters: ExecutionFilters): Promise<Execution[]> {
  const params = new URLSearchParams(Object.entries(filters).filter(([, value]) => value && value !== 'all'))
  try { return await json<Execution[]>(`/api/v1/executions?${params}`) }
  catch { return demoExecutions }
}

export async function getExecution(id: string): Promise<ExecutionDetail> {
  try { return await json<ExecutionDetail>(`/api/v1/executions/${id}`) }
  catch { return demoDetails[id] ?? demoDetails['exec-2401'] }
}

export async function redriveExecution(id: string): Promise<void> { await json(`/api/v1/executions/${id}/redrive`, { method: 'POST' }) }

const now = Date.now()
export const demoExecutions: Execution[] = [
  { id: 'exec-2401', workflowName: 'Employee Onboarding', workflowType: 'HRIS_SYNC', targetApp: 'Workday', status: 'FAILED', startedAt: new Date(now - 1000 * 60 * 4).toISOString(), durationMs: 1240, completedSteps: 3, totalSteps: 5, trigger: 'Webhook' },
  { id: 'exec-2402', workflowName: 'Invoice Reconciliation', workflowType: 'FINANCE', targetApp: 'Salesforce', status: 'SUCCESS', startedAt: new Date(now - 1000 * 60 * 11).toISOString(), durationMs: 8450, completedSteps: 8, totalSteps: 8, trigger: 'Schedule' },
  { id: 'exec-2403', workflowName: 'Ticket Enrichment', workflowType: 'SUPPORT', targetApp: 'Jira', status: 'RETRYING', startedAt: new Date(now - 1000 * 60 * 18).toISOString(), durationMs: 3100, completedSteps: 2, totalSteps: 4, trigger: 'Webhook' },
  { id: 'exec-2404', workflowName: 'New Hire Provisioning', workflowType: 'HRIS_SYNC', targetApp: 'Okta', status: 'IN_PROGRESS', startedAt: new Date(now - 1000 * 60 * 23).toISOString(), durationMs: 420, completedSteps: 1, totalSteps: 6, trigger: 'Webhook' },
  { id: 'exec-2405', workflowName: 'Customer 360 Sync', workflowType: 'CRM', targetApp: 'Salesforce', status: 'SUCCESS', startedAt: new Date(now - 1000 * 60 * 31).toISOString(), durationMs: 2340, completedSteps: 5, totalSteps: 5, trigger: 'Polling' },
  { id: 'exec-2406', workflowName: 'Employee Offboarding', workflowType: 'HRIS_SYNC', targetApp: 'Workday', status: 'SUCCESS', startedAt: new Date(now - 1000 * 60 * 43).toISOString(), durationMs: 1870, completedSteps: 5, totalSteps: 5, trigger: 'Webhook' },
]
const demoDetails: Record<string, ExecutionDetail> = Object.fromEntries(demoExecutions.map((e) => [e.id, { ...e, steps: [
  { id: 'step-1', name: 'Validate employee payload', app: 'Gateway', status: 'SUCCESS', requestPayload: { employee_id: 'WD-88421', email: 'mira.chen@northstar.io' }, responsePayload: { valid: true }, executionTimeMs: 42 },
  { id: 'step-2', name: 'Create Workday profile', app: 'Workday', status: 'SUCCESS', requestPayload: { email: 'mira.chen@northstar.io', department: 'Finance' }, responsePayload: { worker_id: 'W-55291', state: 'created' }, executionTimeMs: 608 },
  { id: 'step-3', name: 'Provision Okta identity', app: 'Okta', status: e.status === 'FAILED' ? 'FAILED' : 'SUCCESS', requestPayload: { login: 'mira.chen@northstar.io' }, responsePayload: e.status === 'FAILED' ? { error: 'upstream_timeout' } : { user_id: '00u9x' }, errorMessage: e.status === 'FAILED' ? '504 Gateway Timeout · retry budget exhausted' : undefined, executionTimeMs: 590 },
  { id: 'step-4', name: 'Notify manager', app: 'Slack', status: e.status === 'SUCCESS' ? 'SUCCESS' : 'PENDING', requestPayload: { channel: '#new-hires' }, responsePayload: { delivered: true }, executionTimeMs: 310 },
]}]))
