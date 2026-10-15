/* VidGravity multi-screen UI polish: mobile drawer, escape handling, and page focus. */
(() => {
  'use strict';
  const sidebar = document.querySelector('.sidebar');
  const menu = document.getElementById('menu');
  if (!sidebar || !menu) return;
  let backdrop = document.querySelector('.vg-mobile-nav-backdrop');
  if (!backdrop) {
    backdrop = document.createElement('button');
    backdrop.type = 'button';
    backdrop.className = 'vg-mobile-nav-backdrop';
    backdrop.setAttribute('aria-label', 'Close navigation');
    backdrop.tabIndex = -1;
    document.body.appendChild(backdrop);
  }
  const isMobile = () => window.matchMedia('(max-width: 650px)').matches;
  const close = () => {
    sidebar.classList.remove('open');
    backdrop.classList.remove('open');
    menu.setAttribute('aria-expanded', 'false');
    document.body.classList.remove('vg-nav-open');
  };
  const open = () => {
    if (!isMobile()) return;
    sidebar.classList.add('open');
    backdrop.classList.add('open');
    menu.setAttribute('aria-expanded', 'true');
    document.body.classList.add('vg-nav-open');
    sidebar.querySelector('.nav')?.focus({preventScroll:true});
  };
  menu.setAttribute('aria-controls', 'vgPrimaryNavigation');
  menu.setAttribute('aria-expanded', 'false');
  const nav = sidebar.querySelector('nav');
  if (nav) nav.id = 'vgPrimaryNavigation';
  menu.addEventListener('click', () => sidebar.classList.contains('open') ? close() : open());
  backdrop.addEventListener('click', close);
  sidebar.querySelectorAll('[data-page]').forEach(button => button.addEventListener('click', () => {
    if (isMobile()) close();
    const pageId = button.getAttribute('data-page');
    if (pageId) {
      const page = document.getElementById(pageId);
      if (page) page.setAttribute('tabindex', '-1');
    }
  }));
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && sidebar.classList.contains('open')) {
      close();
      menu.focus({preventScroll:true});
    }
  });
  window.addEventListener('resize', () => { if (!isMobile()) close(); });
  document.addEventListener('click', event => {
    if (!isMobile() || !sidebar.classList.contains('open')) return;
    if (!sidebar.contains(event.target) && !menu.contains(event.target)) close();
  });
})();
