import { useEffect, useState } from 'react'
import { ChevronRight, Clock, MapPin, Megaphone, Phone, Pin } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { ROLE_LABEL, TYPE_LABEL } from '../labels'
import { orgMenu } from '../orgMenu'
import { api } from '../api'
import { useOrgSite } from '../orgSite'
import { OrgHeader } from '../site/OrgHeader'
import { dateOf, type Notice } from '../site/site'
import { useMyOrganization } from '../useMyOrganization'

/**
 * 기관 홈: 기관 머리(로고·이름·소개) → 연락처 → 공지(고정 먼저 3개) → 메뉴 묶음.
 * 메뉴 목록은 왼쪽 메뉴·탭 바와 같은 orgMenu다.
 */
export function OrganizationHomePage() {
  const { orgId } = useParams()
  const org = useMyOrganization(orgId)
  const { site } = useOrgSite()
  const [notices, setNotices] = useState<Notice[]>([])

  useEffect(() => {
    api<Notice[]>(`/organizations/${orgId}/notices`).then(setNotices).catch(() => setNotices([]))
  }, [orgId])

  if (org === undefined) return <main className="page page-wide" />
  if (org === null) return <NotMember />

  // 공지는 위의 공지 묶음이 대신한다 (같은 메뉴가 두 번 보이지 않게)
  const groups = orgMenu(org).filter((g) => g.title !== '소식')
  const contact = site ? [
    site.address && { icon: MapPin, text: site.address },
    site.phone && { icon: Phone, text: site.phone, href: `tel:${site.phone.replace(/[^0-9+]/g, '')}` },
    site.hoursText && { icon: Clock, text: site.hoursText },
  ].filter((c) => !!c) : []

  return (
    <main className="page page-wide">
      <OrgHeader name={org.name} subtitle={`${TYPE_LABEL[org.type]} · ${ROLE_LABEL[org.role]}`}
        logoUrl={site?.logoUrl ?? null} intro={site?.intro} />

      {contact.length > 0 && (
        <ul className="group">
          {contact.map((c) => (
            <li key={c.text}>
              {'href' in c && c.href
                ? <a className="row row-compact" href={c.href}><span className="row-icon"><c.icon size={18} /></span><span className="row-text"><span className="row-title-plain">{c.text}</span></span></a>
                : <div className="row row-compact"><span className="row-icon"><c.icon size={18} /></span><span className="row-text"><span className="row-title-plain">{c.text}</span></span></div>}
            </li>
          ))}
        </ul>
      )}

      <section>
        <h2 className="group-title">공지</h2>
        <ul className="group">
          {notices.length === 0 && (
            <li><Link className="row row-compact" to="notices"><span className="row-icon"><Megaphone size={18} /></span>
              <span className="row-text"><span className="row-meta">아직 공지가 없어요</span></span>
              <ChevronRight className="row-chevron" size={20} /></Link></li>
          )}
          {notices.slice(0, 3).map((n) => (
              <li key={n.id}>
                <Link className="row" to={`notices#${n.id}`}>
                  <span className="row-icon">{n.pinned ? <Pin size={18} /> : <Megaphone size={18} />}</span>
                  <span className="row-text">
                    <span className="row-title">{n.title}</span>
                    <span className="row-meta">{dateOf(n.createdAt)}</span>
                  </span>
                  <ChevronRight className="row-chevron" size={20} />
                </Link>
              </li>
            ))}
          {notices.length > 3 && (
            <li><Link className="row row-compact" to="notices"><span className="row-icon" aria-hidden="true" />
              <span className="row-text"><span className="row-title-plain">공지 {notices.length}개 모두 보기</span></span>
              <ChevronRight className="row-chevron" size={20} /></Link></li>
          )}
        </ul>
      </section>

      {groups.length > 0 ? (
        <div className="groups">
          {groups.map((g) => (
            <section key={g.title}>
              <h2 className="group-title">{g.title}</h2>
              <ul className="group">
                {g.items.map((item) => (
                  <li key={item.to}>
                    <Link className="row" to={item.to}>
                      <span className="row-icon"><item.icon size={20} /></span>
                      <span className="row-text">
                        <span className="row-title">{item.title}</span>
                        <span className="row-meta">{item.meta}</span>
                      </span>
                      <ChevronRight className="row-chevron" size={20} />
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      ) : (
        <p className="card empty section">이 기관에서 쓸 수 있는 메뉴가 아직 없어요.</p>
      )}
    </main>
  )
}

export function NotMember() {
  return (
    <main className="page">
      <p className="alert" role="alert">이 기관의 멤버가 아니거나 아직 승인되지 않았어요.</p>
      <p className="helper"><Link to="/">내 기관으로</Link></p>
    </main>
  )
}
