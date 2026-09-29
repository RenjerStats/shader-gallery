import {useState} from 'react';
import {WORKS, type Work} from '../data';
import {ShaderCanvas} from '../ShaderCanvas';
import {Icon} from '../Icon';
import {Tilt} from '../Tilt';
import {StatusBar, BottomNav, GyroButton} from '../PhoneShell';
import '../styles/mobile-minimal.css';

const chips = ['All', 'Generative', 'Raymarching', 'Particles', 'Fluid'];

const nav = [
  {icon: 'home', label: 'Gallery', active: true},
  {icon: 'layers', label: 'Collections'},
  {icon: 'users', label: 'Artists'},
  {icon: 'heart', label: 'Saved'},
  {icon: 'user', label: 'Profile'}
];

function AppCard({work, gyro}: {work: Work; gyro: boolean}) {
  return (
    <Tilt className="am-card" style={{'--accent': work.accent} as React.CSSProperties} max={16} gyro={gyro}>
      <div className="am-card-glow" aria-hidden="true" />
      <div className="am-art">
        {/* слой со сдвигом даёт параллакс «картинка внутри карточки» */}
        <div className="am-art-parallax">
          <ShaderCanvas shader={work.shader} className="am-canvas" timeScale={work.speed ?? 1} />
        </div>
        <div className="am-art-fade" aria-hidden="true" />
        <button className="am-more" aria-label="Ещё"><Icon name="more" size={15} /></button>
      </div>
      <div className="am-body">
        <div className="am-head">
          <h3>{work.title}</h3>
          <span className="am-year">{work.year}</span>
        </div>
        <p className="am-desc">{work.description}</p>
        <div className="am-tags">
          {work.tags.map((t) => (
            <span key={t} className="am-tag">{t}</span>
          ))}
        </div>
        <div className="am-foot">
          <div className="am-stats">
            <span><Icon name="eye" size={16} />{work.views}</span>
            <span><Icon name="heart" size={16} />{work.likes}</span>
          </div>
          <button className="am-open"><Icon name="play" size={12} />Open</button>
        </div>
      </div>
      <div className="glare" aria-hidden="true" />
    </Tilt>
  );
}

export function MobileMinimal() {
  const [gyro, setGyro] = useState(false);
  return (
    <div className="am-page">
      <StatusBar />

      <header className="am-header">
        <div>
          <h1>
            A Gallery<br />
            <em>of</em> Living Graphics
          </h1>
          <p>Interactive shader art, real-time experiments, and generative worlds.</p>
        </div>
        <button className="am-avatar" aria-label="Профиль"><Icon name="user" size={19} /></button>
      </header>

      <div className="am-searchrow">
        <label className="am-search">
          <Icon name="search" size={16} />
          <input placeholder="Search shaders, tags, or artists…" />
        </label>
        <button className="am-filter" aria-label="Фильтры"><Icon name="filter" size={16} /></button>
      </div>

      <div className="am-chips">
        {chips.map((c, i) => (
          <button key={c} data-active={i === 0 ? 'true' : undefined}>{c}</button>
        ))}
      </div>

      <div className="am-hint">
        <GyroButton onEnable={() => setGyro(true)} />
        <span>или просто пальцем</span>
      </div>

      <div className="am-feed">
        {WORKS.map((w) => (
          <AppCard key={w.id} work={w} gyro={gyro} />
        ))}
      </div>

      <BottomNav items={nav} />
    </div>
  );
}