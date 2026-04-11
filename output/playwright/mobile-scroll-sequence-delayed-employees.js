async (page) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/v1/public/employees', async (route) => {
    const response = await route.fetch();
    await page.waitForTimeout(3200);
    await route.fulfill({ response });
  });
  await page.goto('http://127.0.0.1:5173/', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(600);

  async function openMenu() {
    const open = page.getByRole('button', { name: 'Open menu' });
    await open.waitFor({ state: 'visible', timeout: 5000 });
    await open.click();
    await page.locator('.mobile-nav-overlay-open').waitFor({ timeout: 5000 });
    await page.waitForTimeout(120);
  }

  async function clickMobileNav(label) {
    await openMenu();
    await page.locator('.mobile-nav-panel .mobile-nav-link', { hasText: label }).click();
  }

  async function metrics(label) {
    return page.evaluate((snapshotLabel) => {
      const byId = (id) => {
        const el = document.getElementById(id);
        if (!el) return null;
        const rect = el.getBoundingClientRect();
        return {
          top: Math.round(rect.top),
          bottom: Math.round(rect.bottom),
          height: Math.round(rect.height)
        };
      };

      return {
        label: snapshotLabel,
        path: location.pathname,
        scrollY: Math.round(window.scrollY),
        gallery: byId('gallery'),
        employees: byId('employees'),
        contact: byId('contact'),
        pending: sessionStorage.getItem('pending-section-scroll')
      };
    }, label);
  }

  const out = [];

  out.push(await metrics('start'));
  await clickMobileNav('FAQ');
  await page.waitForTimeout(1200);
  out.push(await metrics('after FAQ'));
  await clickMobileNav('Contact');
  await page.waitForTimeout(2200);
  out.push(await metrics('after Contact'));
  await clickMobileNav('Gallery');
  await page.waitForTimeout(1800);
  out.push(await metrics('after Gallery 1.8s'));
  await page.waitForTimeout(2600);
  out.push(await metrics('after Gallery 4.4s'));
  await clickMobileNav('Home');
  await page.waitForTimeout(2200);
  out.push(await metrics('after Home'));

  return out;
}
