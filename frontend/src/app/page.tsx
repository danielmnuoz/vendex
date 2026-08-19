const opportunities = [
  {
    direction: "You can sell",
    card: "Charizard ex · 125/197",
    detail: "Moonlight Collectibles wants 2 · Booth B-17",
    value: "$72 max",
    tone: "warm",
  },
  {
    direction: "You can buy",
    card: "Umbreon VMAX · 215/203",
    detail: "Northstar Cards has 1 LP · Booth C-04",
    value: "$615 ask",
    tone: "blue",
  },
  {
    direction: "Liquidate match",
    card: "Pikachu · 173/165",
    detail: "Cardboard Corner wants 4 · Booth A-22",
    value: "$24 max",
    tone: "warm",
  },
] as const;

export default function Home() {
  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#" aria-label="VenDex dashboard">
          <span className="brand-mark" aria-hidden="true">V</span>
          <span>VenDex</span>
        </a>
        <nav className="desktop-nav" aria-label="Primary navigation">
          <a className="nav-link active" href="#overview">Overview</a>
          <a className="nav-link" href="#inventory">Inventory</a>
          <a className="nav-link" href="#buy-list">Buy list</a>
          <a className="nav-link" href="#events">Events</a>
        </nav>
        <div className="topbar-actions">
          <button className="icon-button" type="button" aria-label="Notifications">
            <span aria-hidden="true">3</span>
          </button>
          <button className="avatar" type="button" aria-label="Open account menu">TC</button>
        </div>
      </header>

      <main className="dashboard" id="overview">
        <section className="welcome-row">
          <div>
            <p className="eyebrow">Tuesday, August 18</p>
            <h1>Good afternoon, Taylor.</h1>
            <p className="lede">Your next show has 8 fresh opportunities waiting.</p>
          </div>
          <button className="event-picker" type="button">
            <span>
              <small>Active event</small>
              Collect-A-Con Dallas
            </span>
            <span aria-hidden="true">⌄</span>
          </button>
        </section>

        <section className="stat-grid" aria-label="Event summary">
          <article className="stat-card">
            <span className="stat-kicker">Opportunities</span>
            <strong>8</strong>
            <span className="stat-detail positive">+3 since yesterday</span>
          </article>
          <article className="stat-card">
            <span className="stat-kicker">Saved to plan</span>
            <strong>5</strong>
            <span className="stat-detail">Ready for event day</span>
          </article>
          <article className="stat-card">
            <span className="stat-kicker">Inventory</span>
            <strong>286</strong>
            <span className="stat-detail">42 cards at this event</span>
          </article>
          <article className="stat-card emphasis">
            <span className="stat-kicker">Event starts in</span>
            <strong>4 days</strong>
            <span className="stat-detail">Aug 22–23 · Dallas, TX</span>
          </article>
        </section>

        <div className="dashboard-grid">
          <section className="panel opportunities-panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Live market</p>
                <h2>Best opportunities</h2>
              </div>
              <a href="#all-opportunities">View all 8</a>
            </div>
            <div className="opportunity-list">
              {opportunities.map((item) => (
                <article className="opportunity" key={item.card}>
                  <span className={`direction-dot ${item.tone}`} aria-hidden="true" />
                  <div className="opportunity-copy">
                    <span className="direction-label">{item.direction}</span>
                    <strong>{item.card}</strong>
                    <small>{item.detail}</small>
                  </div>
                  <div className="opportunity-action">
                    <span>{item.value}</span>
                    <button type="button" aria-label={`View ${item.card}`}>View</button>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <aside className="right-rail">
            <section className="panel progress-panel">
              <p className="eyebrow">Show readiness</p>
              <div className="progress-heading">
                <h2>Event setup</h2>
                <strong>75%</strong>
              </div>
              <div className="progress-track" aria-label="Event setup 75 percent complete">
                <span />
              </div>
              <ul className="checklist">
                <li className="done"><span>✓</span> Registered for event</li>
                <li className="done"><span>✓</span> Buy list is active</li>
                <li className="done"><span>✓</span> Inventory is scoped</li>
                <li><span>4</span> Review new opportunities</li>
              </ul>
            </section>

            <section className="panel notification-panel">
              <div className="panel-heading compact">
                <h2>Latest updates</h2>
                <a href="#notifications">See all</a>
              </div>
              <div className="notification-item">
                <span className="notification-mark" aria-hidden="true" />
                <p><strong>New overlap found</strong><br />Moonlight Collectibles wants your Charizard ex.</p>
                <time>12m</time>
              </div>
              <div className="notification-item muted">
                <span className="notification-mark" aria-hidden="true" />
                <p><strong>Plan reminder</strong><br />Collect-A-Con Dallas starts in 4 days.</p>
                <time>2h</time>
              </div>
            </section>
          </aside>
        </div>
      </main>

      <nav className="mobile-nav" aria-label="Mobile navigation">
        <a className="active" href="#overview"><span aria-hidden="true">⌂</span>Home</a>
        <a href="#inventory"><span aria-hidden="true">▦</span>Inventory</a>
        <a href="#events"><span aria-hidden="true">◇</span>Events</a>
        <a href="#account"><span aria-hidden="true">○</span>Account</a>
      </nav>
    </div>
  );
}
