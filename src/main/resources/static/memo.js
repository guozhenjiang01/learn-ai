// 备忘录前端逻辑 — 使用 index.html 中的 PORT 和 authHeaders
function loadMemos() {
  const list = document.getElementById('memoList');
  try {
    fetch(api('/memo/list'), { headers: authHeaders() })
      .then(r => {
        if (r.status === 401) { window.location.href = '/login.html'; return; }
        return r.json();
      })
      .then(memos => {
        if (!memos || !memos.length) {
          list.innerHTML = '<div style="color:var(--text3);text-align:center;padding:30px;">暂无备忘录</div>';
          return;
        }
        const curUser = localStorage.getItem('username');
        const role = localStorage.getItem('role');
        const isAdmin = localStorage.getItem('isAdmin') === '1' || localStorage.getItem('role') === 'admin';
        list.innerHTML = memos.map(m => {
          const time = new Date(m.createdAt).toLocaleString('zh-CN');
          const byUser = (isAdmin && m.username && m.username !== curUser)
            ? '<span class="memo-user">by ' + esc(m.username) + '</span>' : '';
          return '<div class="memo-item">' +
            '<div style="display:flex;align-items:baseline;">' +
              '<span class="memo-title">📌 ' + esc(m.title) + '</span>' + byUser +
              '<span class="memo-time">' + time + '</span>' +
            '</div>' +
            '<div class="memo-body">' + esc(m.content) + '</div>' +
            '<button class="memo-del" onclick="delMemo(\'' + m.id + '\')">删除</button>' +
          '</div>';
        }).join('');
      });
  } catch(e) {
    list.innerHTML = '<div style="color:var(--red);text-align:center;padding:30px;">加载失败</div>';
  }
}

function addMemo() {
  const titleEl = document.getElementById('memoTitle');
  const contentEl = document.getElementById('memoContent');
  const title = titleEl.value.trim();
  const content = contentEl.value.trim();
  if (!title && !content) return;
  fetch(api('/memo/add'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + localStorage.getItem('token') },
    body: JSON.stringify({ title, content })
  }).then(() => {
    titleEl.value = '';
    contentEl.value = '';
    loadMemos();
  }).catch(() => { alert('添加失败'); });
}

function delMemo(id) {
  fetch(api('/memo/' + id), {
    method: 'DELETE',
    headers: { 'Authorization': 'Bearer ' + localStorage.getItem('token') }
  }).then(() => { loadMemos(); }).catch(() => { alert('删除失败'); });
}

function esc(s) { return (s||'').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
