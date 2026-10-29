// Community: a Reddit-style feed of road-safety discussions. Sort (Hot / New / Top), topic filter, search, votes,
// comments with nested replies, edit and delete for your own content, and public author profiles.
import { api, ApiError } from '../api.js';
import { state } from '../state.js';
import { h, icon, mount, field, toast, debounce, pageHeader, errorState, emptyState, skeleton, confirmDialog, showFieldErrors } from '../ui.js';
import { timeAgo, fmtDateTime } from '../format.js';
import { navigate } from '../router.js';

const SORTS = [['hot', 'Hot', 'chart'], ['new', 'New', 'clock'], ['top', 'Top', 'arrowUp']];
const PERIODS = [['', 'All time'], ['day', 'Past day'], ['week', 'Past week'], ['month', 'Past month'], ['year', 'Past year']];

/** Vote control shared by posts and comments. Optimistic update is avoided: the server's totals are shown. */
function voteBox(kind, item, onChange) {
  const score = h('span', { class: 'vscore tech', 'aria-live': 'polite' }, String(item.score));
  const up = h('button', { class: 'vbtn up' + (item.yourVote === 1 ? ' on' : ''), type: 'button', 'aria-label': 'Upvote', 'aria-pressed': String(item.yourVote === 1) }, icon('arrowUp', 18));
  const down = h('button', { class: 'vbtn down' + (item.yourVote === -1 ? ' on' : ''), type: 'button', 'aria-label': 'Downvote', 'aria-pressed': String(item.yourVote === -1) }, icon('arrowDown', 18));
  const send = (vote) => async () => {
    if (!state.me) { toast('Log in to vote.', 'info'); navigate('/login'); return; }
    const want = item.yourVote === (vote === 'up' ? 1 : -1) ? 'none' : vote;   // pressing the same arrow again removes the vote
    try {
      const path = kind === 'post' ? '/api/community/posts/' + item.id + '/vote' : '/api/community/comments/' + item.id + '/vote';
      const r = await api.post(path, { vote: want });
      item.score = r.score; item.yourVote = r.yourVote;
      if (kind === 'post') { item.upCount = r.upCount; item.downCount = r.downCount; }
      score.textContent = String(r.score);
      up.classList.toggle('on', r.yourVote === 1); down.classList.toggle('on', r.yourVote === -1);
      up.setAttribute('aria-pressed', String(r.yourVote === 1)); down.setAttribute('aria-pressed', String(r.yourVote === -1));
      if (onChange) onChange(r);
    } catch (e) { toast(e.message, 'error'); }
  };
  up.addEventListener('click', send('up')); down.addEventListener('click', send('down'));
  return h('div', { class: 'votes' + (kind === 'comment' ? ' small' : '') }, up, score, down);
}

function authorLink(a) {
  if (!a) return h('span', { class: 'muted' }, '[deleted]');
  return h('a', { class: 'author', href: '/community/users/' + a.id }, a.displayName + (a.self ? ' (you)' : ''));
}

function postCard(p, ctx) {
  const card = h('article', { class: 'post card' + (p.mine ? ' mine' : '') },
    voteBox('post', p, () => {}),
    h('div', { class: 'post-main' },
      h('div', { class: 'post-meta' }, h('span', { class: 'topic' }, p.categoryLabel), h('span', { class: 'muted' }, 'Posted by '), authorLink(p.author), h('span', { class: 'muted' }, ' · ' + timeAgo(p.createdAt) + (p.edited ? ' · edited' : ''))),
      h('h3', { class: 'post-title' }, h('a', { href: '/community/posts/' + p.id }, p.title)),
      p.preview ? h('p', { class: 'post-preview muted' }, p.preview) : null,
      h('div', { class: 'post-actions' },
        h('a', { class: 'action', href: '/community/posts/' + p.id }, icon('list', 14), p.commentCount + (p.commentCount === 1 ? ' comment' : ' comments')),
        p.mine ? h('button', { class: 'action', type: 'button', onclick: () => ctx.edit(p) }, icon('edit', 14), 'Edit') : null,
        p.mine ? h('button', { class: 'action danger', type: 'button', onclick: () => ctx.remove(p) }, icon('trash', 14), 'Delete') : null)));
  return card;
}

export async function communityPage({ container, query, isCurrent }) {
  let sort = SORTS.some(([k]) => k === query.sort) ? query.sort : 'hot';
  let category = query.topic || '';
  let period = PERIODS.some(([k]) => k === query.period) ? query.period : '';
  let q = query.q || '';
  let page = 0;
  let cats = [];
  const feed = h('div', { class: 'feed', 'aria-live': 'polite' });
  const pager = h('div', { class: 'pager' });
  const topicList = h('nav', { class: 'topic-list', 'aria-label': 'Topics' });
  const search = h('input', { type: 'search', placeholder: 'Search posts…', value: q, 'aria-label': 'Search community posts' });
  const periodSel = h('select', { 'aria-label': 'Time period', hidden: sort !== 'top' }, PERIODS.map(([v, l]) => h('option', { value: v, selected: v === period }, l)));

  function sortBar() {
    return h('div', { class: 'sortbar', role: 'tablist', 'aria-label': 'Sort posts' },
      SORTS.map(([k, label, ic]) => h('button', { type: 'button', role: 'tab', class: 'sort' + (sort === k ? ' active' : ''), 'aria-selected': String(sort === k),
        onclick: () => { sort = k; page = 0; periodSel.hidden = k !== 'top'; drawSort(); load(); } }, icon(ic, 15), label)),
      periodSel);
  }
  function drawSort() { mount(sortBarBox, sortBar()); }
  const sortBarBox = h('div');

  function drawTopics() {
    const total = cats.reduce((n, c) => n + c.postCount, 0);
    mount(topicList, h('h3', null, 'Topics'),
      h('button', { type: 'button', class: 'topic-btn' + (!category ? ' active' : ''), onclick: () => { category = ''; page = 0; drawTopics(); load(); } }, 'All topics', h('span', { class: 'count tech' }, total)),
      cats.map((c) => h('button', { type: 'button', class: 'topic-btn' + (category === c.key ? ' active' : ''), onclick: () => { category = c.key; page = 0; drawTopics(); load(); } }, c.label, h('span', { class: 'count tech' }, c.postCount))));
  }

  async function load() {
    mount(feed, skeleton(4));
    try {
      const r = await api.get('/api/community/posts', { sort, category, q: q.trim(), period: sort === 'top' ? period : '', page, size: 15 });
      if (!isCurrent()) return;
      if (!r.items.length) {
        mount(feed, emptyState(q || category ? 'No posts match these filters' : 'No posts yet', q || category ? 'Try another topic, a different search, or clear the filters.' : 'Start the conversation: share a pothole, an accident or a traffic problem.',
          state.me ? h('button', { class: 'btn primary', type: 'button', onclick: () => openComposer() }, icon('plus', 16), 'Create a post') : h('a', { class: 'btn primary', href: '/login' }, 'Log in to post')));
      } else {
        mount(feed, r.items.map((p) => postCard(p, ctx)));
      }
      mount(pager, h('span', { class: 'muted small tech' }, r.totalItems + ' post' + (r.totalItems === 1 ? '' : 's')),
        h('button', { class: 'btn small', type: 'button', disabled: page <= 0, onclick: () => { page--; load(); } }, '‹ Newer'),
        h('button', { class: 'btn small', type: 'button', disabled: page + 1 >= r.totalPages, onclick: () => { page++; load(); } }, 'Older ›'));
    } catch (e) { if (isCurrent()) mount(feed, errorState(e, load)); }
  }

  const ctx = {
    edit: (p) => openComposer(p),
    remove: async (p) => {
      if (!(await confirmDialog({ title: 'Delete this post?', message: 'Your post will be removed from the community. Its comments stay visible as "[deleted]" so the discussion makes sense.', confirmText: 'Delete post', danger: true }))) return;
      try { await api.del('/api/community/posts/' + p.id); toast('Post deleted.', 'success'); load(); } catch (e) { toast(e.message, 'error'); }
    },
  };

  function openComposer(existing) {
    const title = h('input', { name: 'title', type: 'text', maxlength: '140', value: existing ? existing.title : '', placeholder: 'What is the road problem? (5-140 characters)' });
    const body = h('textarea', { name: 'body', maxlength: '5000', placeholder: 'Add details: where, when, what you saw. (optional)' }, existing ? existing.body || '' : '');
    const topic = h('select', { name: 'category' }, cats.filter((c) => c.key).map((c) => h('option', { value: c.key, selected: existing ? existing.category === c.key : category === c.key }, c.label)));
    const hazardRef = h('input', { name: 'hazardReportId', type: 'text', maxlength: '40', value: existing && existing.hazard ? existing.hazard.reportId : '', placeholder: 'Optional: a hazard reference, e.g. RG-2026-00431' });
    const err = h('div', { class: 'banner error', hidden: true, role: 'alert' });
    const save = h('button', { class: 'btn primary', type: 'submit' }, existing ? 'Save changes' : 'Post');
    const form = h('form', { novalidate: true, onsubmit: async (e) => {
      e.preventDefault(); err.hidden = true;
      const payload = { title: title.value.trim(), body: body.value.trim(), category: topic.value, hazardReportId: hazardRef.value.trim() || null };
      save.disabled = true;
      try {
        const r = existing ? await api.put('/api/community/posts/' + existing.id, payload) : await api.post('/api/community/posts', payload);
        dlg.close(); toast(existing ? 'Post updated.' : 'Posted to the community.', 'success');
        if (!existing) { sort = 'new'; page = 0; drawSort(); }
        if (existing && isCurrent()) { load(); }
        navigate('/community/posts/' + r.id);
      } catch (ex) {
        save.disabled = false;
        if (!showFieldErrors(form, ex.fieldErrors)) { err.textContent = ex.message; err.hidden = false; }
      }
    } }, err, field('Title', title), field('Topic', topic), field('Details (optional)', body), field('Link to a hazard report (optional)', hazardRef, { hint: 'Paste the reference from your report. The thread then shows that report\'s live status.' }), h('div', { class: 'dialog-actions' },
      h('button', { class: 'btn ghost', type: 'button', onclick: () => dlg.close() }, 'Cancel'), save));
    const dlg = modalish(existing ? 'Edit post' : 'Create a post', form);
  }

  const searchDebounced = debounce(() => { q = search.value; page = 0; load(); }, 350);
  search.addEventListener('input', searchDebounced);
  periodSel.addEventListener('change', () => { period = periodSel.value; page = 0; load(); });

  const newBtn = state.me
    ? h('button', { class: 'btn primary', type: 'button', onclick: () => openComposer() }, icon('plus', 16), 'Create post')
    : h('a', { class: 'btn primary', href: '/login' }, 'Log in to post');
  mount(container, pageHeader('Community', 'Discuss road safety, accidents, potholes and traffic with your neighbours.', newBtn),
    h('div', { class: 'community-layout' },
      h('aside', { class: 'card side-topics' }, topicList),
      h('main', { class: 'community-main' },
        h('div', { class: 'toolbar card-plain' }, h('div', { class: 'grow' }, search), sortBarBox),
        feed, pager)));
  drawSort();
  try { cats = await api.get('/api/community/categories'); } catch (e) { cats = []; }
  drawTopics();
  await load();
  return () => {};
}

/** Single post with its full comment tree, comment form and reply boxes. */
export async function communityPostPage({ container, params, isCurrent }) {
  mount(container, skeleton(6));
  let post;
  try { post = await api.get('/api/community/posts/' + encodeURIComponent(params.id)); }
  catch (e) { if (isCurrent()) mount(container, h('a', { href: '/community' }, '← Community'), e.status === 404 ? emptyState('Post not found', 'It may have been deleted by its author.', h('a', { class: 'btn', href: '/community' }, 'Back to the community')) : errorState(e, () => location.reload())); return; }
  if (!isCurrent()) return;

  const thread = h('section', { class: 'thread' });
  function drawThread() { mount(thread, post.comments.length ? post.comments.map((c) => commentNode(c, 0)) : emptyState('No comments yet', 'Be the first to add something useful.')); }

  function commentNode(c, depth) {
    const replyBox = h('div', { class: 'reply-box', hidden: true });
    const body = c.deleted ? h('p', { class: 'muted deleted' }, '[deleted]') : h('p', { class: 'comment-body' }, c.body);
    const actions = h('div', { class: 'post-actions' },
      !c.deleted && state.me ? h('button', { class: 'action', type: 'button', onclick: () => { replyBox.hidden = !replyBox.hidden; replyBox.querySelector('textarea') && replyBox.querySelector('textarea').focus(); } }, icon('plus', 14), 'Reply') : null,
      c.mine && !c.deleted ? h('button', { class: 'action', type: 'button', onclick: () => editComment(c, body) }, icon('edit', 14), 'Edit') : null,
      c.mine && !c.deleted ? h('button', { class: 'action danger', type: 'button', onclick: () => removeComment(c) }, icon('trash', 14), 'Delete') : null,
      c.edited && !c.deleted ? h('span', { class: 'muted small' }, 'edited') : null);
    const reply = state.me && !c.deleted ? h('form', { class: 'reply-form', onsubmit: async (e) => {
      e.preventDefault();
      const t = e.target.querySelector('textarea');
      await addComment(t.value, c.id, (created) => { c.replies.push(created); drawThread(); });
    } }, h('textarea', { rows: '2', maxlength: '2000', placeholder: 'Write a reply…', 'aria-label': 'Reply' }), h('div', { class: 'btn-row' }, h('button', { class: 'btn small primary', type: 'submit' }, 'Reply'), h('button', { class: 'btn small ghost', type: 'button', onclick: () => { replyBox.hidden = true; } }, 'Cancel'))) : null;
    replyBox.append(reply);
    const node = h('div', { class: 'comment depth-' + Math.min(depth, 6), 'data-id': c.id },
      h('div', { class: 'comment-head' }, c.deleted ? h('span', { class: 'muted' }, '[deleted]') : authorLink(c.author), h('span', { class: 'muted small' }, ' · ' + timeAgo(c.createdAt))),
      h('div', { class: 'comment-row' }, voteBox('comment', c, () => {}), h('div', { class: 'comment-content' }, body, actions, replyBox)),
      c.replies && c.replies.length ? h('div', { class: 'replies' }, c.replies.map((r) => commentNode(r, depth + 1))) : null);
    node.replyBoxRef = replyBox;
    return node;
  }

  async function addComment(text, parentId, done) {
    const t = (text || '').trim();
    if (!t) { toast('Write something first.', 'info'); return; }
    try {
      const created = await api.post('/api/community/posts/' + post.id + '/comments', { body: t, parentId: parentId || null });
      post.commentCount += 1;
      if (parentId) { done(created); } else { post.comments.push(created); drawThread(); }
      drawHead();
      toast('Comment posted.', 'success');
    } catch (e) { toast((e.fieldErrors && (e.fieldErrors.body || e.fieldErrors.parentId)) || e.message, 'error'); }
  }

  async function editComment(c, bodyEl) {
    const ta = h('textarea', { rows: '4', maxlength: '2000' }, c.body);
    const dlg = modalish('Edit comment', h('form', { onsubmit: async (e) => {
      e.preventDefault();
      try { const r = await api.put('/api/community/comments/' + c.id, { body: ta.value.trim() }); c.body = r.body; c.edited = true; dlg.close(); drawThread(); toast('Comment updated.', 'success'); }
      catch (ex) { toast((ex.fieldErrors && ex.fieldErrors.body) || ex.message, 'error'); }
    } }, field('Your comment', ta), h('div', { class: 'dialog-actions' }, h('button', { class: 'btn ghost', type: 'button', onclick: () => dlg.close() }, 'Cancel'), h('button', { class: 'btn primary', type: 'submit' }, 'Save'))));
    const dlgRef = dlg; void dlgRef;
  }

  async function removeComment(c) {
    if (!(await confirmDialog({ title: 'Delete this comment?', message: 'Replies under it stay visible, and this comment shows as "[deleted]".', confirmText: 'Delete comment', danger: true }))) return;
    try { await api.del('/api/community/comments/' + c.id); c.deleted = true; c.body = ''; c.author = null; post.commentCount = Math.max(0, post.commentCount - 0); drawThread(); toast('Comment deleted.', 'success'); }
    catch (e) { toast(e.message, 'error'); }
  }

  const head = h('div');
  function drawHead() {
    const p = post;
    mount(head,
      h('article', { class: 'post-full card' + (p.mine ? ' mine' : '') },
        h('div', { class: 'post-full-row' },
          voteBox('post', p, (r) => { p.upCount = r.upCount; p.downCount = r.downCount; }),
          h('div', { class: 'post-main' },
            h('div', { class: 'post-meta' }, h('span', { class: 'topic' }, p.categoryLabel), h('span', { class: 'muted' }, 'Posted by '), authorLink(p.author), h('span', { class: 'muted' }, ' · ' + fmtDateTime(p.createdAt) + (p.edited ? ' · edited' : ''))),
            h('h1', { class: 'post-title-lg' }, p.title),
            p.hazard ? h('a', { class: 'linked-hazard', href: p.hazard.url }, icon('alert', 16), h('span', null, h('strong', null, 'About hazard ' + p.hazard.reportId + ': '), p.hazard.hazardType + ' on ' + (p.hazard.roadName || 'an unnamed road') + ' · status ', h('span', { class: 'tech' }, p.hazard.status))) : null,
            p.body ? h('p', { class: 'post-body' }, p.body) : null,
            p.deleted ? h('p', { class: 'muted' }, 'This post was deleted.') : null,
            h('div', { class: 'post-actions' },
              h('span', { class: 'muted small tech' }, p.commentCount + ' comments · ' + p.upCount + ' up · ' + p.downCount + ' down'),
              p.mine ? h('button', { class: 'action', type: 'button', onclick: () => editPost() }, icon('edit', 14), 'Edit') : null,
              p.mine ? h('button', { class: 'action danger', type: 'button', onclick: () => deletePost() }, icon('trash', 14), 'Delete') : null)))),
      h('div', { class: 'card compose' }, state.me
        ? h('form', { onsubmit: async (e) => { e.preventDefault(); const t = e.target.querySelector('textarea'); await addComment(t.value, null); t.value = ''; } },
          h('label', { class: 'label-text', for: 'new-comment' }, 'Add a comment'), h('textarea', { id: 'new-comment', rows: '3', maxlength: '2000', placeholder: 'Share what you know. Be respectful; this is a road-safety community.' }),
          h('div', { class: 'btn-row' }, h('button', { class: 'btn primary', type: 'submit' }, 'Comment')))
        : h('p', null, h('a', { href: '/login' }, 'Log in'), ' to join the discussion.')),
      h('h2', { class: 'thread-title' }, 'Comments'));
  }

  async function editPost() {
    const title = h('input', { name: 'title', type: 'text', maxlength: '140', value: post.title });
    const body = h('textarea', { name: 'body', maxlength: '5000' }, post.body || '');
    const topic = h('select', { name: 'category' }, (await api.get('/api/community/categories')).filter((c) => c.key).map((c) => h('option', { value: c.key, selected: c.key === post.category }, c.label)));
    const err = h('div', { class: 'banner error', hidden: true, role: 'alert' });
    const form = h('form', { novalidate: true, onsubmit: async (e) => {
      e.preventDefault();
      try { const r = await api.put('/api/community/posts/' + post.id, { title: title.value.trim(), body: body.value.trim(), category: topic.value }); Object.assign(post, r); dlg.close(); drawHead(); toast('Post updated.', 'success'); }
      catch (ex) { if (!showFieldErrors(form, ex.fieldErrors)) { err.textContent = ex.message; err.hidden = false; } }
    } }, err, field('Title', title), field('Topic', topic), field('Details', body), h('div', { class: 'dialog-actions' }, h('button', { class: 'btn ghost', type: 'button', onclick: () => dlg.close() }, 'Cancel'), h('button', { class: 'btn primary', type: 'submit' }, 'Save changes')));
    const dlg = modalish('Edit post', form);
  }

  async function deletePost() {
    if (!(await confirmDialog({ title: 'Delete this post?', message: 'The post will be removed. Its comments stay visible as "[deleted]".', confirmText: 'Delete post', danger: true }))) return;
    try { await api.del('/api/community/posts/' + post.id); toast('Post deleted.', 'success'); navigate('/community'); }
    catch (e) { toast(e.message, 'error'); }
  }

  mount(container, h('p', null, h('a', { href: '/community' }, '← Community')), head, thread);
  drawHead(); drawThread();
  return () => {};
}

/** Public profile: name, when they joined, counts, score and their posts. */
export async function communityProfilePage({ container, params, isCurrent }) {
  mount(container, skeleton(5));
  try {
    const p = await api.get('/api/community/users/' + encodeURIComponent(params.id));
    if (!isCurrent()) return;
    mount(container, h('p', null, h('a', { href: '/community' }, '← Community')),
      pageHeader(p.author.displayName + (p.author.self ? ' (you)' : ''), 'Member since ' + fmtDateTime(p.joinedAt)),
      h('div', { class: 'stat-grid' },
        h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Posts'), h('div', { class: 'value' }, p.postCount)),
        h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Comments'), h('div', { class: 'value' }, p.commentCount)),
        h('div', { class: 'card stat ok' }, h('div', { class: 'label' }, 'Post score'), h('div', { class: 'value' }, p.postScore))),
      h('h2', null, 'Posts'),
      p.posts.length ? p.posts.map((x) => postCard(x, { edit: () => navigate('/community/posts/' + x.id), remove: () => {} })) : emptyState('No posts yet'));
  } catch (e) { if (isCurrent()) mount(container, e.status === 404 ? emptyState('User not found', '') : errorState(e, () => location.reload())); }
}

function modalish(title, content) {
  const overlay = h('div', { class: 'overlay', role: 'dialog', 'aria-modal': 'true', 'aria-label': title });
  const card = h('div', { class: 'dialog wide' }, h('h3', null, title), content);
  overlay.appendChild(card);
  document.body.appendChild(overlay);
  const close = () => { overlay.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });
  return { overlay, card, close };
}
