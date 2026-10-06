import { useEffect, useState } from 'react'
import { api } from './api'
import type { MyOrganization } from './labels'

/** 경로의 기관에서 내 역할. 불러오는 중이면 undefined, 활성 멤버가 아니면 null */
export function useMyOrganization(orgId: string | undefined): MyOrganization | null | undefined {
  const [org, setOrg] = useState<MyOrganization | null>()
  useEffect(() => {
    api<MyOrganization[]>('/me/organizations')
      .then((list) => setOrg(list.find((o) => String(o.organizationId) === orgId && o.status === 'ACTIVE') ?? null))
      .catch(() => setOrg(null))
  }, [orgId])
  return org
}
