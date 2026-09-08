// 예약 정책 관리
function go(p) { location.assign(p); }
function api(path) {
  const p = path.startsWith('/') ? path : `/${path}`;
  return new URL(p, location.origin).toString();
}
function readJwt(token) {
  try { return JSON.parse(atob(token.split('.')[1])); } catch { return null; }
}

document.addEventListener('DOMContentLoaded', () => {
  const token = localStorage.getItem('token');
  if (!token) { go('/index.html'); return; }
  const payload = readJwt(token);
  if (!payload) { localStorage.removeItem('token'); go('/index.html'); return; }
  if (payload.role !== 'admin') { alert('관리자 전용 페이지입니다.'); go('/index.html'); return; }

  const headers = { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' };
  const form  = document.getElementById('policyForm');
  const msg   = document.getElementById('policyMsg');
  const save  = document.getElementById('policySave');
  const reset = document.getElementById('policyReset');

  // 서버에서 받은 마지막 값. '되돌리기'와 저장 시 변경분 계산에 쓴다.
  let loaded = null;

  function showMsg(text, kind) {
    msg.textContent = text;
    msg.className = `form-msg ${kind}`;
    msg.hidden = false;
  }
  function clearMsg() { msg.hidden = true; }

  // "09:00:00" → "09:00" (input[type=time]은 초를 안 받는다)
  const toInputTime  = (v) => (v || '').slice(0, 5);
  const toServerTime = (v) => (v.length === 5 ? `${v}:00` : v);

  function fill(p) {
    form.querySelectorAll('input[name="same_day_mode"]').forEach(r => {
      r.checked = (r.value === p.same_day_mode);
    });
    form.max_minutes.value         = p.max_minutes;
    form.cancel_deadline_min.value = p.cancel_deadline_min;
    form.slot_minutes.value        = p.slot_minutes;
    form.open_time.value           = toInputTime(p.open_time);
    form.close_time.value          = toInputTime(p.close_time);
    form.week_open_weekday.value   = String(p.week_open_weekday);
    form.week_open_time.value      = toInputTime(p.week_open_time);
  }

  async function load() {
    const res = await fetch(api('/api/policy'), { headers });
    if (res.status === 401) { localStorage.removeItem('token'); go('/index.html'); return; }
    if (!res.ok) { showMsg('정책을 불러오지 못했습니다.', 'error'); return; }
    loaded = await res.json();
    fill(loaded);
    clearMsg();
  }

  function current() {
    const mode = form.querySelector('input[name="same_day_mode"]:checked');
    return {
      same_day_mode:       mode ? mode.value : loaded.same_day_mode,
      max_minutes:         Number(form.max_minutes.value),
      cancel_deadline_min: Number(form.cancel_deadline_min.value),
      slot_minutes:        Number(form.slot_minutes.value),
      open_time:           toServerTime(form.open_time.value),
      close_time:          toServerTime(form.close_time.value),
      week_open_weekday:   Number(form.week_open_weekday.value),
      week_open_time:      toServerTime(form.week_open_time.value),
    };
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearMsg();

    const body = current();
    if (body.close_time <= body.open_time) {
      showMsg('종료 시각이 시작 시각보다 빠릅니다.', 'error');
      return;
    }
    if (body.max_minutes % body.slot_minutes !== 0) {
      showMsg(`최대 길이(${body.max_minutes}분)가 슬롯 단위(${body.slot_minutes}분)의 배수가 아닙니다.`, 'error');
      return;
    }

    save.disabled = true;
    try {
      const res = await fetch(api('/api/policy'), {
        method: 'PATCH', headers, body: JSON.stringify(body),
      });
      if (!res.ok) {
        const d = await res.json().catch(() => ({}));
        showMsg(d.detail || '저장에 실패했습니다.', 'error');
        return;
      }
      loaded = await res.json();
      fill(loaded);
      showMsg('저장했습니다. 예약 화면에 바로 적용됩니다.', 'success');
    } finally {
      save.disabled = false;
    }
  });

  reset.addEventListener('click', () => {
    if (loaded) { fill(loaded); clearMsg(); }
  });

  document.getElementById('logout').onclick = () => {
    localStorage.removeItem('token');
    go('/index.html');
  };

  load();
});
