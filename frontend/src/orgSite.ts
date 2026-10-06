import { useOutletContext } from 'react-router'
import type { Site } from './site/site'

/** 기관 화면들이 함께 쓰는 사이트 정보(OrgLayout이 읽는다). 꾸미기 화면이 저장한 뒤 reloadSite로 앱 바·색을 바로 바꾼다 */
export type OrgOutlet = { site: Site | undefined; reloadSite: () => void; reloadOrg: () => void }

export const useOrgSite = () => useOutletContext<OrgOutlet>()
