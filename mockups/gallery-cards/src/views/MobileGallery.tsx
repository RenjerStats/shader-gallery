import {useState} from 'react';
import {WORKS} from '../data';
import {StatusBar, GyroButton} from '../PhoneShell';
import {GalleryWall} from '../three/GalleryWall';
import '../styles/mobile-gallery.css';

const pad = (n: number) => String(n).padStart(2, '0');

export function MobileGallery() {
  const [focus, setFocus] = useState<number | null>(null);
  const [nearest, setNearest] = useState(0);
  const [gyro, setGyro] = useState(false);
  const active = WORKS[focus ?? nearest];

  return (
    <div className="gw-view gw-view--mobile">
      <GalleryWall works={WORKS} mode="mobile" focus={focus} onFocusChange={setFocus} onNearest={setNearest} gyro={gyro} />
      <StatusBar />

      <div className="gw-m-counter">
        <b>{pad((focus ?? nearest) + 1)}</b> / {pad(WORKS.length)}
      </div>

      <div className="gw-m-caption" data-focus={focus != null ? 'true' : 'false'}>
        <div className="gw-m-head">
          <h3>{active.title}</h3>
          <span>{active.year}</span>
        </div>
        <p className="gw-m-author">by {active.author}</p>
        {focus != null && <p className="gw-m-desc">{active.description}</p>}
        <div className="gw-m-tags">{active.tags.join(' • ')}</div>
        <div className="gw-m-row">
          <GyroButton onEnable={() => setGyro(true)} label="Взгляд гироскопом" />
          <span className="gw-m-hint">веди пальцем — прогулка, тап — рассмотреть</span>
        </div>
      </div>
    </div>
  );
}
