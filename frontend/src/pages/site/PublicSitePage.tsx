import { useEffect, useState } from 'react'
import { ChevronDown, Clock, MapPin, Megaphone, Phone, Pin } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { api } from '../../api'
import { TYPE_LABEL } from '../../labels'
import { Linkified } from '../../site/Linkified'
import { OrgHeader } from '../../site/OrgHeader'
import { colorStyle, dateOf, type PublicSite } from '../../site/site'

/**
 * UC-13. 로그인 없이 보는 기관 소개 페이지 /s/:slug. 기관 틀(OrgLayout) 밖이다.
 * 없는 주소와 비공개를 구분하지 않는다 (서버도 같은 404).
 */
export function PublicSitePage() {
  const { slug } = useParams()
  const [site, setSite] = useState<PublicSite | null>()
  const [open, setOpen] = useState<number | null>(null)

  useEffect(() => {
    api<PublicSite>(`/public/sites/${encodeURIComponent(slug ?? '')}`).then(setSite).catch(() => setSite(null))
  }, [slug])

  useEffect(() => {
    if (site) document.title = site.name
  }, [site])

  if (site === undefined) return <main className="page" />
  if (site === null) {
    return (
      <main className="page page-auth">
        <h1>찾을 수 없어요</h1>
        <p className="page-sub">주소가 바뀌었거나 아직 공개하지 않은 페이지예요.</p>
        <Link className="button" to="/">처음으로</Link>
      </main>
    )
  }

  const contact = [
    site.address && { icon: MapPin, text: site.address },
    site.phone && { icon: Phone, text: site.phone, href: `tel:${site.phone.replace(/[^0-9+]/g, '')}` },
    site.hoursText && { icon: Clock, text: site.hoursText },
  ].filter((c) => !!c)

  return (
    <div className="public-site" style={colorStyle(site.color)}>
      <main className="page page-wide">
        <OrgHeader name={site.name} subtitle={TYPE_LABEL[site.type]} logoUrl={site.logoUrl} />

        {site.intro && <section className="card"><p className="intro"><Linkified text={site.intro} /></p></section>}

        {contact.length > 0 && (
          <ul className="group section">
            {contact.map((c) => (
              <li key={c.text}>
                {'href' in c && c.href
                  ? <a className="row row-compact" href={c.href}><span className="row-icon"><c.icon size={18} /></span><span className="row-text"><span className="row-title-plain">{c.text}</span></span></a>
                  : <div className="row row-compact"><span className="row-icon"><c.icon size={18} /></span><span className="row-text"><span className="row-title-plain">{c.text}</span></span></div>}
              </li>
            ))}
          </ul>
        )}

        {site.notices.length > 0 && (
          <section>
            <h2 className="group-title">소식</h2>
            <ul className="group">
              {site.notices.map((n) => (
                <li key={n.id}>
                  <button type="button" className="row row-button" aria-expanded={open === n.id}
                    onClick={() => setOpen(open === n.id ? null : n.id)}>
                    <span className="row-icon">{n.pinned ? <Pin size={18} /> : <Megaphone size={18} />}</span>
                    <span className="row-text">
                      <span className="row-title">{n.title}</span>
                      <span className="row-meta">{dateOf(n.createdAt)}</span>
                    </span>
                    <ChevronDown className={open === n.id ? 'row-chevron row-chevron-open' : 'row-chevron'} size={20} />
                  </button>
                  {open === n.id && n.body && <div className="notice-body"><p><Linkified text={n.body} /></p></div>}
                </li>
              ))}
            </ul>
          </section>
        )}

        <p className="helper">마디로 만든 페이지예요.</p>
      </main>

      {site.join.open && (
        <div className="sticky-cta">
          <Link className="button button-primary button-block" to={`/join?slug=${encodeURIComponent(slug ?? '')}`}>
            가입 신청
          </Link>
        </div>
      )}
    </div>
  )
}
