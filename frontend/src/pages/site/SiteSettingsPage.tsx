import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Check, ExternalLink, Pipette } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { api, errorMessage, fieldErrors } from '../../api'
import { Field } from '../../Field'
import { isManager, ROLE_LABEL, TYPE_LABEL } from '../../labels'
import { useOrgSite } from '../../orgSite'
import { OrgHeader } from '../../site/OrgHeader'
import { nearStatusHue, readable } from '../../site/color'
import { COLORS, colorStyle, type PresetColor, type Site } from '../../site/site'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

const MAX_LOGO = 200 * 1024

/** UC-10. 관리자는 꾸미고, 공개 주소·공개·가입 받기는 소유자만 (API 38, 39) */
export function SiteSettingsPage() {
  const { orgId } = useParams()
  const org = useMyOrganization(orgId)
  const { site, reloadSite } = useOrgSite()
  // 고친 칸만 들고 있다가 저장하면 비운다. 화면에 보이는 값 = 틀이 읽은 값 + 고친 칸
  const [edits, setEdits] = useState<Partial<Site>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')
  const [saved, setSaved] = useState('')
  const [colorNote, setColorNote] = useState('')

  const form: Site | undefined = site && { ...site, ...edits }
  if (org === undefined || !form) return <main className="page page-wide" />
  if (org === null || !isManager(org.role)) return <NotMember />
  const owner = org.role === 'OWNER'

  /** 저장한 폼의 칸(saves)만 고친 목록에서 지운다. 소개를 쓰다가 로고를 올려도 쓰던 소개가 남는다 */
  async function run(action: () => Promise<unknown>, done: string, saves: (keyof Site)[] = []) {
    setErrors({})
    setMessage('')
    setSaved('')
    try {
      await action()
      setEdits((current) => Object.fromEntries(Object.entries(current).filter(([k]) => !saves.includes(k as keyof Site))))
      setSaved(done)
      reloadSite()
    } catch (error) {
      setErrors(fieldErrors(error))
      setMessage(errorMessage(error))
    }
  }

  function describe(event: FormEvent) {
    event.preventDefault()
    run(() => api(`/organizations/${orgId}/site`, {
      method: 'PUT',
      body: { intro: form!.intro, address: form!.address, phone: form!.phone, hoursText: form!.hoursText, color: form!.color },
    }), '저장했어요.', ['intro', 'address', 'phone', 'hoursText', 'color'])
  }

  function publish(event: FormEvent) {
    event.preventDefault()
    run(() => api(`/organizations/${orgId}/site/publishing`, {
      method: 'PUT', body: { slug: form!.slug ?? '', published: !!form!.published, acceptJoin: !!form!.acceptJoin },
    }), '공개 설정을 저장했어요.', ['slug', 'published', 'acceptJoin'])
  }

  /** 바이트 그대로 (API 40). api()를 거쳐서 토큰이 만료됐으면 로그인했다가 돌아온다 */
  function uploadLogo(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (file.size > MAX_LOGO) {
      setMessage('200KB 이하 이미지만 올릴 수 있어요.')
      return
    }
    run(() => api(`/organizations/${orgId}/site/logo`, { method: 'PUT', body: file }), '로고를 바꿨어요.')
  }

  const set = (patch: Partial<Site>) => setEdits({ ...edits, ...patch })
  const custom = !(form.color in COLORS)

  function pickCustom(value: string) {
    const { color, adjusted } = readable(value)
    set({ color })
    setColorNote([
      adjusted && '흰 글자가 잘 보이도록 같은 색을 조금 진하게 맞췄어요.',
      nearStatusHue(color) && '예약 상태 색(초록 빈 방, 빨강 오류)과 비슷해서 지도에서 헷갈릴 수 있어요.',
    ].filter(Boolean).join(' '))
  }

  return (
    <main className="page page-wide">
      <h1>사이트 꾸미기</h1>
      <p className="page-sub">기관 홈과 공개 소개 페이지에 보이는 정보예요.</p>

      {/* 미리보기: 고른 색이 바로 보인다 */}
      <div style={colorStyle(form.color)}>
        <OrgHeader name={org.name} subtitle={`${TYPE_LABEL[org.type]} · ${ROLE_LABEL[org.role]}`}
          logoUrl={site?.logoUrl ?? null} intro={form.intro} />
      </div>

      {form.published && (
        <p className="notice-inline">이 내용은 공개 페이지 <Link to={`/s/${form.slug}`}>/s/{form.slug}</Link>에도 바로 보여요.</p>
      )}
      {saved && <p className="notice-inline" role="status">{saved}</p>}
      {message && <p className="alert" role="alert">{message}</p>}

      <section className="card form section">
        <p className="card-title">로고</p>
        <p className="card-meta">PNG, JPEG, WebP · 200KB 이하. 정사각형이 가장 잘 보여요.</p>
        <div className="actions">
          <label className="button">
            {site?.logoUrl ? '바꾸기' : '올리기'}
            <input type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={uploadLogo} />
          </label>
          {site?.logoUrl && (
            <button type="button" className="button button-danger"
              onClick={() => run(() => api(`/organizations/${orgId}/site/logo`, { method: 'DELETE' }), '로고를 지웠어요.')}>
              지우기
            </button>
          )}
        </div>
      </section>

      <form className="card form section" onSubmit={describe} noValidate>
        <p className="card-title">소개와 연락처</p>
        <Field id="intro" label="소개" error={errors.intro}>
          <textarea id="intro" rows={4} maxLength={2000} value={form.intro} placeholder="예: 1:1 피아노·보컬 레슨, 연습실 12개"
            onChange={(e) => set({ intro: e.target.value })} />
        </Field>
        <Field id="address" label="주소" error={errors.address}>
          <input id="address" maxLength={200} value={form.address} onChange={(e) => set({ address: e.target.value })} />
        </Field>
        <Field id="phone" label="대표 번호" error={errors.phone}>
          <input id="phone" type="tel" maxLength={30} value={form.phone} onChange={(e) => set({ phone: e.target.value })} />
        </Field>
        <Field id="hoursText" label="운영 안내" error={errors.hoursText}>
          <input id="hoursText" maxLength={200} value={form.hoursText} placeholder="예: 평일 14:00~22:00, 토 10:00~18:00"
            onChange={(e) => set({ hoursText: e.target.value })} />
        </Field>
        <fieldset className="swatches" aria-label="기관 색">
          <legend className="field-label">기관 색</legend>
          {(Object.keys(COLORS) as PresetColor[]).map((c) => (
            <label key={c} className="swatch" style={{ background: COLORS[c].primary }} title={COLORS[c].name}>
              <input type="radio" name="color" checked={form.color === c} onChange={() => { set({ color: c }); setColorNote('') }}
                aria-label={COLORS[c].name} />
              {form.color === c && <Check size={20} color="#fff" />}
            </label>
          ))}
          {/* 직접 고르기: 흰 글자가 안 보이면 같은 색조로 진하게 맞춘다. 고르는 도중 색판이 튀지 않게 입력칸은 제어하지 않는다 */}
          <label className={custom ? 'swatch swatch-custom swatch-custom-on' : 'swatch swatch-custom'}
            style={custom ? { background: form.color } : undefined} title="직접 고르기">
            <input type="color" aria-label="직접 고르기" defaultValue={custom ? form.color : '#4F46E5'} onChange={(e) => pickCustom(e.target.value)} />
            {custom ? <Check size={20} color="#fff" /> : <Pipette size={18} />}
          </label>
        </fieldset>
        {colorNote && <p className="hint">{colorNote}</p>}
        <button className="button button-primary">저장</button>
      </form>

      {owner && (
        <form className="card form section" onSubmit={publish} noValidate>
          <p className="card-title">공개 소개 페이지</p>
          <p className="card-meta">로그인하지 않은 사람도 볼 수 있는 페이지예요. 소개, 연락처, 외부 공개 공지만 보여요.</p>
          <Field id="slug" label="주소" error={errors.slug}>
            <div className="input-prefix">
              <span>/s/</span>
              <input id="slug" value={form.slug ?? ''} placeholder="harmony" autoCapitalize="none" autoComplete="off"
                onChange={(e) => set({ slug: e.target.value.toLowerCase() })} />
            </div>
          </Field>
          <label className="check">
            <input type="checkbox" checked={!!form.published} onChange={(e) => set({ published: e.target.checked })} />
            공개하기
          </label>
          <label className="check">
            <input type="checkbox" checked={!!form.acceptJoin} onChange={(e) => set({ acceptJoin: e.target.checked })} />
            공개 페이지에서 가입 신청 받기
          </label>
          <p className="hint">가입 코드 가입이 켜져 있어야 받을 수 있어요. 신청은 멤버 화면에서 승인해요. 가입 코드는 공개되지 않아요.</p>
          <div className="actions">
            <button className="button">공개 설정 저장</button>
            {site?.published && site.slug && (
              <Link className="button" to={`/s/${site.slug}`} target="_blank">
                <ExternalLink size={16} /> 열어 보기
              </Link>
            )}
          </div>
        </form>
      )}
    </main>
  )
}
