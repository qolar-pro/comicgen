async function loadCharacters() {
  const res = await fetch('/api/characters');
  const chars = await res.json();
  const list = document.getElementById('characterList');
  list.innerHTML = '';

  for (const c of chars) {
    const card = document.createElement('label');
    card.className = 'char-card';
    card.innerHTML = `
      <input type="checkbox" value="${c.id}" />
      <div><strong>${c.name}</strong></div>
      <div class="muted">${c.description || ''}</div>
      <div class="muted"><code>${c.image_path}</code></div>
    `;
    list.appendChild(card);
  }
}

document.getElementById('characterForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = new FormData(e.target);
  const res = await fetch('/api/characters', { method: 'POST', body: form });
  if (!res.ok) {
    alert('Failed to save character');
    return;
  }
  e.target.reset();
  await loadCharacters();
});

document.getElementById('generateBtn').addEventListener('click', async () => {
  const selected = [...document.querySelectorAll('#characterList input:checked')].map((i) => i.value);
  const payload = {
    character_ids: selected,
    location: document.getElementById('location').value,
    event: document.getElementById('event').value,
    villain: document.getElementById('villain').value,
  };

  const res = await fetch('/api/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const out = document.getElementById('output');
  const body = await res.json();
  out.textContent = JSON.stringify(body, null, 2);
});

loadCharacters();
