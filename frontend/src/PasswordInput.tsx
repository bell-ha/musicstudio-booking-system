import { useState, type InputHTMLAttributes } from 'react'
import { Eye, EyeOff } from 'lucide-react'

/** 비밀번호 칸 + 오른쪽 눈 버튼. 눌러서 친 글자를 확인한다 (휴대폰에서 잘못 치기 쉬움) */
export function PasswordInput(props: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>) {
  const [shown, setShown] = useState(false)
  return (
    <div className="password">
      <input {...props} type={shown ? 'text' : 'password'} autoCapitalize="none" autoCorrect="off" spellCheck={false} />
      <button type="button" className="password-toggle" aria-label="비밀번호 보기"
        aria-pressed={shown} aria-controls={props.id} onClick={() => setShown((v) => !v)}>
        {shown ? <EyeOff size={20} /> : <Eye size={20} />}
      </button>
    </div>
  )
}
