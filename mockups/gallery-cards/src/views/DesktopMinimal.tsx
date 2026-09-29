import {WORKS, type Work} from '../data';
import {ShaderCanvas} from '../ShaderCanvas';
import {Icon} from '../Icon';
import {Tilt} from '../Tilt';
import '../styles/desktop-minimal.css';

const chips = ['All', 'Generative', 'Raymarching', 'Particles', 'Fluid', 'Abstract', 'Landscape'];

function MinimalCard({work, index}: {work: Work; index: number}) {
  return (
    <Tilt className="dm-card" style={{'--accent': work.accent, '--i': index} as React.CSSProperties} max={4}>
      <div className="dm-card-glow" aria-hidden="true" />
      <div className="dm-art">
        <ShaderCanvas shader={work.shader} className="dm-canvas" timeScale={work.speed ?? 1} />
        <div className="dm-art-fade" aria-hidden="true" />
        <button className="dm-more" aria-label="Ещё">
          <Icon name="more" size={16} />
        </button>
        <div className="glare" aria-hidden="true" />
      </div>
      <div className="dm-body">
        <div className="dm-head">
          <h3>{work.title}</h3>
          <span className="dm-year">{work.year}</span>
        </div>
        <p className="dm-desc">{work.description}</p>
        <div className="dm-tags">
          {work.tags.map((t) => (
            <span key={t} className="dm-tag">{t}</span>
          ))}
        </div>
        <div className="dm-foot">
          <div className="dm-stats">
            <span><Icon name="eye" size={17} />{work.views}</span>
            <span><Icon name="heart" size={17} />{work.likes}</span>
          </div>
          <button className="dm-open"><Icon name="play" size={13} />Open</button>
        </div>
        <div className="dm-author">by {work.author}</div>
      </div>
    </Tilt>
  );
}

export function DesktopMinimal() {
  return (
    <div className="dm-page">
      <header className="dm-header">
        <div className="dm-brand"><span className="dm-brand-mark" />Shader Gallery</div>
        <nav>
          <a data-active="true">Gallery</a>
          <a>Collections</a>
          <a>Artists</a>
          <a>About</a>
        </nav>
        <div className="dm-header-actions">
          <label className="dm-search">
            <Icon name="search" size={16} />
            <input placeholder="Search shaders, tags, or artists…" />
          </label>
          <button className="dm-icon-btn" aria-label="Настройки"><Icon name="sparkle" size={17} /></button>
        </div>
      </header>

      <section className="dm-hero">
        <div>
          <h1>
            A Gallery <em>of</em> Living Graphics
          </h1>
          <p>Interactive shader art, real-time experiments, and generative worlds.<br />Explore a growing collection of visual experiences built with code.</p>
        </div>
        <div className="dm-filters">
          {chips.map((c, i) => (
            <button key={c} className="dm-chip" data-active={i === 0 ? 'true' : undefined}>{c}</button>
          ))}
          <button className="dm-chip dm-sort">Sort: Featured <Icon name="filter" size={14} /></button>
        </div>
      </section>

      <section className="dm-grid">
        {WORKS.map((w, i) => (
          <MinimalCard key={w.id} work={w} index={i} />
        ))}
      </section>
    </div>
  );
}