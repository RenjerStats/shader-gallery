import {useState} from 'react';
import {WORKS} from '../data';
import {Icon} from '../Icon';
import {GalleryWall} from '../three/GalleryWall';
import '../styles/desktop-gallery.css';

const pad = (n: number) => String(n).padStart(2, '0');

export function DesktopGallery() {
  const [focus, setFocus] = useState<number | null>(null);
  const [nearest, setNearest] = useState(0);
  const active = WORKS[focus ?? nearest];

  return (
    <div className="gw-view">
      <GalleryWall works={WORKS} mode="desktop" focus={focus} onFocusChange={setFocus} onNearest={setNearest} />

      <header className="gw-hud">
        <div className="gw-brand">
          <span className="gw-kicker">A curated gallery of</span>
          <h1>Shader Art</h1>
        </div>
        <div className="gw-counter">
          <b>{pad((focus ?? nearest) + 1)}</b>
          <i>/ {pad(WORKS.length)}</i>
          <span>{active.title}</span>
        </div>
      </header>

      <div className="gw-hint">
        <Icon name="compass" size={14} />
        <span>Прокрутите колесом или потяните мышью — прогулка по залу · клик по картине — рассмотреть вблизи</span>
      </div>

      {focus != null && (
        <aside className="gw-caption">
          <span className="gw-caption-kicker">Now viewing</span>
          <h2>{active.title}</h2>
          <p className="gw-caption-author">by {active.author} · {active.year}</p>
          <p className="gw-caption-desc">{active.description}</p>
          <div className="gw-caption-tags">{active.tags.join(' • ')}</div>
          <div className="gw-caption-stats">
            <span><Icon name="eye" size={14} /> {active.views}</span>
            <span><Icon name="heart" size={14} /> {active.likes}</span>
          </div>
          <button onClick={() => setFocus(null)}>
            <Icon name="home" size={14} /> Вернуться в зал
          </button>
        </aside>
      )}
    </div>
  );
}
