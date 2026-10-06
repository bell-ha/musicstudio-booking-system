import type { ReactNode } from 'react'

/** 라벨 + 입력칸 + 칸 바로 아래 오류 문구 (DESIGN.md 4절 입력칸) */
export function Field({ id, label, error, children }: { id: string; label: string; error?: string; children: ReactNode }) {
  return (
    <div className="field" data-invalid={error ? '' : undefined}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error && <span className="field-error" id={`${id}-error`}>{error}</span>}
    </div>
  )
}
