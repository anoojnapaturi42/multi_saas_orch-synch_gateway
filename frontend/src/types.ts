export type ExecutionStatus = 'SUCCESS' | 'COMPLETED' | 'FAILED' | 'RETRYING' | 'IN_PROGRESS' | 'RUNNING' | 'PENDING'
export type Execution = { id: string; executionId?: string; workflowName: string; workflowType: string; targetApp: string; status: ExecutionStatus; startedAt: string; durationMs: number; completedSteps: number; totalSteps: number; trigger?: string }
export type StepTrace = { id: string; name: string; app: string; status: ExecutionStatus; requestPayload: unknown; responsePayload: unknown; errorMessage?: string; executionTimeMs: number }
export type ExecutionDetail = Execution & { steps: StepTrace[] }
export type ExecutionFilters = { dateRange: string; workflowType: string; status: string; targetApp: string }
