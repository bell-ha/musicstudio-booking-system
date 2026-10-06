import { useEffect, useState } from 'react'
import { api } from './api'
import type { MyOrganization } from './labels'

/**
 * 경로의 기관에서 내 역할. 불러오는 중이면 undefined, 활성 멤버가 아니면 null.
 * refreshKey가 바뀌면 다시 읽는다 (내 역할이 바뀌었을 수 있을 때).
 */
export function useMyOrganization(orgId: string | undefined, refreshKey = 0): MyOrganization | null | undefined {
  const [org, setOrg] = useState<MyOrganization | null>()
  useEffect(() => {
    api<MyOrganization[]>('/me/organizations')
      .then((list) => setOrg(list.find((o) => String(o.organizationId) === orgId && o.status === 'ACTIVE') ?? null))
      .catch(() => setOrg(null))
  }, [orgId, refreshKey])
  return org
}
